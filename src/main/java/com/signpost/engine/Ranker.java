package com.signpost.engine;

import com.signpost.engine.model.DiaryTaskGap;
import com.signpost.engine.model.Gap;
import com.signpost.engine.model.GoalCategory;
import com.signpost.engine.model.GoalRef;
import com.signpost.engine.model.GoalStatus;
import com.signpost.engine.model.PrerequisiteGap;
import com.signpost.engine.model.QuestPrereqGap;
import com.signpost.engine.model.RankedGoal;
import com.signpost.kb.RewardValue;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Orders {@link GoalStatus}es into {@link RankedGoal}s: pinned goals first (in
 * pin order), then goals that are ready right now, then everything else by
 * {@code priority × closeness} descending. Pure: no {@link net.runelite.api.Client}, no I/O
 * (global constraint: engine code is pure).
 */
public final class Ranker
{
	private static final double XP_SCALE = 250_000.0;
	/** A goal more than one stage above the account's is scored down, not excluded. */
	private static final double LATER_PENALTY = 0.05;
	/** Each distinct goal a quest unblocks adds this to its priority, up to {@link #FAN_OUT_CAP}. */
	private static final double FAN_OUT_PER_DEPENDENT = 0.5;
	private static final double FAN_OUT_CAP = 2.0;

	private static Comparator<RankedGoal> byScoreThenProgression(int accountStage)
	{
		return Comparator.comparingDouble(RankedGoal::getScore).reversed()
			// Uncovered targets never outrank the parent they serve at the same score.
			.thenComparing((RankedGoal r) -> r.getStatus().isUncoveredTarget() ? 1 : 0)
			// Prefer the account's current progression stage, not the oldest accessible content.
			.thenComparingInt(r -> Math.abs(accountStage - r.getStatus().getGoal().getStage()))
			.thenComparing((RankedGoal r) -> r.getStatus().getGoal().getPriority(), Comparator.reverseOrder())
			.thenComparing(r -> r.getStatus().getGoal().getName())
			.thenComparing(r -> r.getStatus().getGoal().getId());
	}

	private static int progressionGroup(RankedGoal ranked, int accountStage)
	{
		GoalStatus status = ranked.getStatus();
		return status.isBankCovered() || status.getGoal().getStage() <= accountStage ? 0 : 1;
	}

	/**
	 * As {@link #rank(List, Set, List, int)} with {@code accountStage} high enough that no goal is
	 * ever "later" (stage 4 is the max) - kept for callers that don't have a stage yet.
	 */
	public List<RankedGoal> rank(List<GoalStatus> statuses, Set<String> hidden, List<String> pins)
	{
		return rank(statuses, hidden, pins, 4);
	}

	/**
	 * {@code accountStage} is the account's estimated progression stage
	 * ({@link StageEstimator}). A non-pinned goal with {@code stage > accountStage + 1} has its
	 * score multiplied by {@link #LATER_PENALTY} and {@link RankedGoal#isLater()} set - a pinned
	 * goal is never marked later, since a pin is an explicit user override.
	 */
	public List<RankedGoal> rank(List<GoalStatus> statuses, Set<String> hidden, List<String> pins, int accountStage)
	{
		return rank(statuses, hidden, pins, accountStage, Map.of());
	}

	/**
	 * A quest's {@code dependents} is the number of distinct non-hidden goals whose
	 * gap tree contains it; its priority is raised by {@code min(2, dependents x 0.5)} before the
	 * closeness multiplier, and the resulting {@link RankedGoal#getUnblocks()} names those goals in
	 * rank order. {@code extraDependents} (quest goal id to goal ids) adds dependents the gap trees
	 * don't show - the goals a ready quest's reward xp covers a skill target for.
	 */
	public List<RankedGoal> rank(List<GoalStatus> statuses, Set<String> hidden, List<String> pins, int accountStage,
		Map<String, Set<String>> extraDependents)
	{
		Map<String, GoalStatus> byId = new LinkedHashMap<>();
		for (GoalStatus status : statuses)
		{
			if (!hidden.contains(status.getGoal().getId()))
			{
				byId.put(status.getGoal().getId(), status);
			}
		}
		Map<String, Set<String>> dependents = dependents(byId, extraDependents);

		List<RankedGoal> pinned = new ArrayList<>();
		Set<String> used = new LinkedHashSet<>();
		for (String id : pins)
		{
			GoalStatus status = byId.get(id);
			if (status == null || !used.add(id))
			{
				continue;
			}
			pinned.add(new RankedGoal(status, score(status, dependentCount(dependents, id)), true, false));
		}

		List<RankedGoal> ready = new ArrayList<>();
		List<RankedGoal> rest = new ArrayList<>();
		List<RankedGoal> later = new ArrayList<>();
		for (GoalStatus status : byId.values())
		{
			if (used.contains(status.getGoal().getId()))
			{
				continue;
			}
			boolean isLater = status.getGoal().getStage() > accountStage + 1;
			double base = score(status, dependentCount(dependents, status.getGoal().getId()));
			double score = isLater ? base * LATER_PENALTY : base;
			RankedGoal ranked = new RankedGoal(status, score, false, isLater);
			// A later goal never enters the ready tier, regardless of its own gaps: readiness within
			// the account's own stage range must always outrank a stage-inappropriate goal.
			(isLater ? later : (isReadyNow(status) ? ready : rest)).add(ranked);
		}
		// Ready primary progression comes first; mere readiness must not promote a situational grind.
		// Within the remaining tiers, score the actual gain rather than the age of its source.
		Comparator<RankedGoal> byScore = byScoreThenProgression(accountStage);
		Comparator<RankedGoal> byStageThenScore = Comparator
			.<RankedGoal>comparingInt(r -> progressionGroup(r, accountStage))
			.thenComparing(byScore);
		ready.sort(byStageThenScore);
		rest.sort(byStageThenScore);
		later.sort(byScore);

		List<RankedGoal> result = new ArrayList<>(pinned.size() + ready.size() + rest.size() + later.size());
		result.addAll(pinned);
		result.addAll(ready);
		result.addAll(rest);
		result.addAll(later);
		return withUnblocks(prerequisiteFirst(result), dependents, byId);
	}

	/**
	 * Stable topological ordering, choosing the earliest otherwise-ranked goal whenever
	 * its visible prerequisites have been emitted. Scores are unchanged; a pairwise comparator
	 * cannot express dependency order transitively. Pins remain explicit overrides in pin order,
	 * and hidden/completed prerequisites are never reinserted into the visible list.
	 */
	private static List<RankedGoal> prerequisiteFirst(List<RankedGoal> ranked)
	{
		Map<String, Integer> index = new LinkedHashMap<>();
		for (int i = 0; i < ranked.size(); i++)
		{
			index.put(ranked.get(i).getStatus().getGoal().getId(), i);
		}
		int[] unmet = new int[ranked.size()];
		Map<Integer, List<Integer>> dependants = new LinkedHashMap<>();
		for (int i = 0; i < ranked.size(); i++)
		{
			RankedGoal goal = ranked.get(i);
			if (goal.isPinned())
			{
				continue;
			}
			for (Gap gap : goal.getStatus().getGaps())
			{
				if (gap instanceof PrerequisiteGap)
				{
					Integer prerequisite = index.get(((PrerequisiteGap) gap).getGoalId());
					if (prerequisite != null)
					{
						unmet[i]++;
						dependants.computeIfAbsent(prerequisite, ignored -> new ArrayList<>()).add(i);
					}
				}
			}
		}
		PriorityQueue<Integer> available = new PriorityQueue<>();
		for (int i = 0; i < ranked.size(); i++)
		{
			if (unmet[i] == 0)
			{
				available.add(i);
			}
		}
		List<RankedGoal> ordered = new ArrayList<>(ranked.size());
		while (!available.isEmpty())
		{
			int next = available.remove();
			ordered.add(ranked.get(next));
			for (int dependant : dependants.getOrDefault(next, List.of()))
			{
				if (--unmet[dependant] == 0)
				{
					available.add(dependant);
				}
			}
		}
		if (ordered.size() != ranked.size())
		{
			throw new IllegalArgumentException("Cycle in milestone prerequisite gaps");
		}
		return ordered;
	}

	/** Quest goal id to the distinct non-hidden goal ids whose gap tree (top level or inside a diary task) needs that quest. */
	private static Map<String, Set<String>> dependents(Map<String, GoalStatus> byId, Map<String, Set<String>> extraDependents)
	{
		Map<String, Set<String>> dependents = new HashMap<>();
		for (GoalStatus status : byId.values())
		{
			if (status.getGoal().getCategory() == GoalCategory.SKILL_TARGET)
			{
				for (GoalRef parent : status.getParents())
				{
					if (byId.containsKey(parent.getId()))
					{
						collectQuestIds(status.getGaps(), parent.getId(), dependents);
					}
				}
			}
			else
			{
				collectQuestIds(status.getGaps(), status.getGoal().getId(), dependents);
			}
		}
		extraDependents.forEach((questId, ids) -> ids.stream().filter(byId::containsKey).filter(id -> !id.equals(questId))
			.forEach(id -> dependents.computeIfAbsent(questId, k -> new LinkedHashSet<>()).add(id)));
		return dependents;
	}

	private static void collectQuestIds(List<Gap> gaps, String dependentId, Map<String, Set<String>> dependents)
	{
		for (Gap gap : gaps)
		{
			if (gap instanceof QuestPrereqGap)
			{
				String questId = "quest:" + ((QuestPrereqGap) gap).getQuest().getId();
				if (!questId.equals(dependentId))
				{
					dependents.computeIfAbsent(questId, k -> new LinkedHashSet<>()).add(dependentId);
				}
			}
			else if (gap instanceof DiaryTaskGap)
			{
				collectQuestIds(((DiaryTaskGap) gap).getGaps(), dependentId, dependents);
			}
		}
	}

	private static int dependentCount(Map<String, Set<String>> dependents, String goalId)
	{
		return dependents.getOrDefault(goalId, Set.of()).size();
	}

	/** Re-creates each ranked quest with dependents so it names them in rank order. */
	private static List<RankedGoal> withUnblocks(List<RankedGoal> ranked, Map<String, Set<String>> dependents, Map<String, GoalStatus> byId)
	{
		Map<String, Integer> position = new HashMap<>();
		for (int i = 0; i < ranked.size(); i++)
		{
			position.put(ranked.get(i).getStatus().getGoal().getId(), i);
		}
		List<RankedGoal> result = new ArrayList<>(ranked.size());
		for (RankedGoal r : ranked)
		{
			Set<String> ids = dependents.get(r.getStatus().getGoal().getId());
			if (ids == null || ids.isEmpty())
			{
				result.add(r);
				continue;
			}
			List<String> names = ids.stream()
				.sorted(Comparator.comparingInt(id -> position.getOrDefault(id, Integer.MAX_VALUE)))
				.map(id -> byId.get(id).getGoal().getName())
				.collect(Collectors.toList());
			result.add(new RankedGoal(r.getStatus(), r.getScore(), r.isPinned(), r.isLater(), names));
		}
		return result;
	}

	/** A status with an unseen bank is never "Ready now", even with no gaps. */
	private static boolean isReadyNow(GoalStatus status)
	{
		RewardValue value = status.getObjective() == null ? RewardValue.USEFUL : status.getObjective().getValue();
		return status.isReady() && !status.isBankUnknown() && value.ordinal() <= RewardValue.USEFUL.ordinal();
	}

	/**
	 * {@code score = priority × closeness × progression value}, {@code closeness = 1 / (1 + unmet + xpDelta / 250000)}.
	 * A skill target whose route the bank covers has closeness 1.0 -
	 * the materials are in hand, so it is as close as a ready goal.
	 */
	static double score(GoalStatus status)
	{
		return score(status, 0);
	}

	/** As {@link #score(GoalStatus)}, with the fan-out bonus of {@code min(2, dependents x 0.5)} added to the priority. */
	static double score(GoalStatus status, int dependents)
	{
		if (status.isBankCovered())
		{
			return status.getGoal().getPriority();
		}
		if (status.isUncoveredTarget())
		{
			// Never above the goal it serves.
			return status.getParentScore();
		}
		int unmet = GoalMetrics.unmetCount(status.getGaps());
		long xpDelta = GoalMetrics.xpDeltaSum(status.getGaps());
		double closeness = 1.0 / (1 + unmet + xpDelta / XP_SCALE);
		double bonus = Math.min(FAN_OUT_CAP, dependents * FAN_OUT_PER_DEPENDENT);
		return (status.getGoal().getPriority() + bonus) * closeness * progressionValue(status);
	}

	private static double progressionValue(GoalStatus status)
	{
		if (status.getObjective() == null) return 1.0;
		switch (status.getObjective().getValue())
		{
			case MAJOR: return 1.5;
			case USEFUL: return 1.0;
			case SITUATIONAL: return 0.2;
			case COLLECTION: return 0.0;
			default: throw new IllegalStateException("Unknown progression value");
		}
	}
}
