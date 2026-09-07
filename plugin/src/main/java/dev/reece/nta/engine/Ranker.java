package dev.reece.nta.engine;

import dev.reece.nta.engine.model.GoalStatus;
import dev.reece.nta.engine.model.RankedGoal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Orders {@link GoalStatus}es into {@link RankedGoal}s per spec ruling 14: pinned goals first (in
 * pin order), then goals that are ready right now, then everything else by
 * {@code priority × closeness} descending. Pure: no {@link net.runelite.api.Client}, no I/O
 * (global constraint: engine code is pure).
 */
public final class Ranker
{
	private static final double XP_SCALE = 250_000.0;
	/** Spec ruling 27: a goal more than one stage above the account's is scored down, not excluded. */
	private static final double LATER_PENALTY = 0.05;

	private static final Comparator<RankedGoal> BY_SCORE_THEN_PRIORITY_THEN_NAME = Comparator
		.comparingDouble(RankedGoal::getScore).reversed()
		.thenComparing((RankedGoal r) -> r.getStatus().getGoal().getPriority(), Comparator.reverseOrder())
		.thenComparing(r -> r.getStatus().getGoal().getName());

	/**
	 * As {@link #rank(List, Set, List, int)} with {@code accountStage} high enough that no goal is
	 * ever "later" (stage 4 is the max) - kept for callers that don't have a stage yet.
	 */
	public List<RankedGoal> rank(List<GoalStatus> statuses, Set<String> hidden, List<String> pins)
	{
		return rank(statuses, hidden, pins, 4);
	}

	/**
	 * {@code accountStage} (spec ruling 27) is the account's estimated progression stage
	 * ({@link StageEstimator}). A non-pinned goal with {@code stage > accountStage + 1} has its
	 * score multiplied by {@link #LATER_PENALTY} and {@link RankedGoal#isLater()} set - a pinned
	 * goal is never marked later, since a pin is an explicit user override.
	 */
	public List<RankedGoal> rank(List<GoalStatus> statuses, Set<String> hidden, List<String> pins, int accountStage)
	{
		Map<String, GoalStatus> byId = new LinkedHashMap<>();
		for (GoalStatus status : statuses)
		{
			if (!hidden.contains(status.getGoal().getId()))
			{
				byId.put(status.getGoal().getId(), status);
			}
		}

		List<RankedGoal> pinned = new ArrayList<>();
		Set<String> used = new LinkedHashSet<>();
		for (String id : pins)
		{
			GoalStatus status = byId.get(id);
			if (status == null || !used.add(id))
			{
				continue;
			}
			pinned.add(new RankedGoal(status, score(status), true, false));
		}

		List<RankedGoal> ready = new ArrayList<>();
		List<RankedGoal> rest = new ArrayList<>();
		for (GoalStatus status : byId.values())
		{
			if (used.contains(status.getGoal().getId()))
			{
				continue;
			}
			boolean later = status.getGoal().getStage() > accountStage + 1;
			double score = later ? score(status) * LATER_PENALTY : score(status);
			RankedGoal ranked = new RankedGoal(status, score, false, later);
			(isReadyNow(status) ? ready : rest).add(ranked);
		}
		ready.sort(BY_SCORE_THEN_PRIORITY_THEN_NAME);
		rest.sort(BY_SCORE_THEN_PRIORITY_THEN_NAME);

		List<RankedGoal> result = new ArrayList<>(pinned.size() + ready.size() + rest.size());
		result.addAll(pinned);
		result.addAll(ready);
		result.addAll(rest);
		return result;
	}

	/** Ready per D2, except a status with an unseen bank is never "Ready now" even with no gaps (ruling 14). */
	private static boolean isReadyNow(GoalStatus status)
	{
		return status.isReady() && !status.isBankUnknown();
	}

	/** {@code score = priority × closeness}, {@code closeness = 1 / (1 + unmet + xpDelta / 250000)} (ruling 14). */
	static double score(GoalStatus status)
	{
		int unmet = GoalMetrics.unmetCount(status.getGaps());
		long xpDelta = GoalMetrics.xpDeltaSum(status.getGaps());
		double closeness = 1.0 / (1 + unmet + xpDelta / XP_SCALE);
		return status.getGoal().getPriority() * closeness;
	}
}
