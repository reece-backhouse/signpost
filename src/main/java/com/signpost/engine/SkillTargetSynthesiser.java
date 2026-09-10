package com.signpost.engine;

import com.signpost.engine.model.DiaryTaskGap;
import com.signpost.engine.model.Gap;
import com.signpost.engine.model.Goal;
import com.signpost.engine.model.GoalCategory;
import com.signpost.engine.model.GoalRef;
import com.signpost.engine.model.GoalStatus;
import com.signpost.engine.model.QuestXp;
import com.signpost.engine.model.Route;
import com.signpost.engine.model.SkillLevelGap;
import com.signpost.kb.KnowledgeBase;
import com.signpost.kb.QuestEntry;
import com.signpost.kb.QuestLamp;
import com.signpost.kb.WikiUrls;
import com.signpost.snapshot.SkillState;
import com.signpost.snapshot.Snapshot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Value;
import net.runelite.api.Experience;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;

/**
 * Skill targets are goals. For each skill, the lowest unmet level any upcoming
 * goal requires (top-level {@link SkillLevelGap}s, those inside a {@link DiaryTaskGap}, and
 * recommended-profile skill gaps alike) becomes one {@code skill:<SKILL>:<level>} goal of
 * {@link GoalCategory#SKILL_TARGET}, carrying every parent with a gap in that skill (lowest level
 * first), the priority, stage and score of the best-scoring parent needing exactly that level, and the {@link RoutePlanner} route from the current bank (computed once here; the same
 * object is reused when the target is focused). A hidden parent, or one more than one stage above
 * the account (the same "later" rule {@link Ranker} applies), never contributes.
 *
 * <p>Reward xp from quests the player can do right now (unfinished, not hidden,
 * every requirement met) counts towards a target first - fixed xp in the skill, then lamps the
 * skill is allowed for at the level reached by credited rewards, largest eligible first, each lamp spent on at most one
 * target (targets are visited in their best parent's score order). A target the quests fully
 * cover is not synthesised; the covering quests are reported as unblocking its parents instead.
 * Pure: no {@link net.runelite.api.Client}, no I/O.
 */
final class SkillTargetSynthesiser
{
	private SkillTargetSynthesiser()
	{
	}

	/** The targets plus, per covering quest goal id, the parent goal ids its reward xp fully unblocks. */
	@Value
	static class Synthesis
	{
		List<GoalStatus> targets;
		Map<String, Set<String>> questUnblocks;
	}

	static List<GoalStatus> synthesise(List<GoalStatus> statuses, Set<String> hidden, int accountStage, Snapshot snapshot, KnowledgeBase kb)
	{
		return run(statuses, hidden, accountStage, snapshot, kb).getTargets();
	}

	static Synthesis run(List<GoalStatus> statuses, Set<String> hidden, int accountStage, Snapshot snapshot, KnowledgeBase kb)
	{
		// skill -> (parent goal id -> the lowest level that parent needs the skill at)
		Map<Skill, Map<String, Need>> needs = new EnumMap<>(Skill.class);
		for (GoalStatus status : statuses)
		{
			Goal goal = status.getGoal();
			if (goal.getCategory() == GoalCategory.SKILL_TARGET || hidden.contains(goal.getId()) || goal.getStage() > accountStage + 1)
			{
				continue;
			}
			collect(status.getGaps(), status, needs);
		}

		Map<Integer, Integer> bankAll = NextStepPicker.bankAll(snapshot);
		List<Candidate> candidates = new ArrayList<>();
		for (Map.Entry<Skill, Map<String, Need>> entry : needs.entrySet())
		{
			Skill skill = entry.getKey();
			List<Need> parents = new ArrayList<>(entry.getValue().values());
			parents.sort(Comparator.comparingInt((Need n) -> n.level).thenComparing(n -> -n.goal.getPriority()).thenComparing(n -> n.goal.getName()));
			int level = parents.get(0).level;
			// Priority, stage and score cap come from the best-scoring parent this level actually
			// unlocks - a goal needing a higher level is listed as a parent but lends
			// nothing (otherwise "52 Prayer" for two hard diaries would rank with Vorkath's priority).
			Need best = parents.stream().filter(n -> n.level == level)
				.max(Comparator.comparingDouble((Need n) -> Ranker.score(n.status)).thenComparing(n -> n.goal.getPriority())
					.thenComparing(n -> n.goal.getName(), Comparator.reverseOrder()))
				.orElseThrow();
			candidates.add(new Candidate(skill, level, parents, best));
		}

		// Lamps are spent in the order the targets would rank (their best parent's score), so a lamp
		// two targets could use goes to the one that ranks first.
		List<Candidate> byRank = new ArrayList<>(candidates);
		byRank.sort(Comparator.comparingDouble((Candidate c) -> -Ranker.score(c.best.status)).thenComparing(c -> c.skill.getName()));
		List<ReadyQuest> readyQuests = readyQuests(statuses, hidden, snapshot, kb);
		Map<Candidate, List<QuestXp>> questXpByCandidate = new LinkedHashMap<>();
		for (Candidate c : byRank)
		{
			questXpByCandidate.put(c, questXpFor(c, readyQuests, snapshot));
		}

		List<GoalStatus> targets = new ArrayList<>();
		Map<String, Set<String>> questUnblocks = new LinkedHashMap<>();
		for (Candidate c : candidates)
		{
			Skill skill = c.skill;
			int level = c.level;
			int priority = c.best.goal.getPriority();
			int stage = c.best.goal.getStage();

			SkillState state = snapshot.getSkills().get(skill);
			int have = state == null ? 1 : state.getLevel();
			long currentXp = state == null ? 0 : state.getXp();
			long targetXp = Experience.getXpForLevel(level);
			List<QuestXp> questXp = questXpByCandidate.get(c);
			long questTotal = questXp.stream().mapToLong(QuestXp::getXp).sum();
			if (questTotal >= targetXp - currentXp)
			{
				// Credit only parents whose own required level the forecast actually reaches.
				for (QuestXp q : questXp)
				{
					Set<String> unblocked = questUnblocks.computeIfAbsent(q.getQuestId(), k -> new LinkedHashSet<>());
					for (Need need : c.parents)
					{
						if (currentXp + questTotal >= Experience.getXpForLevel(need.level))
						{
							unblocked.add(need.goal.getId());
						}
					}
				}
				continue;
			}
			SkillLevelGap gap = new SkillLevelGap(skill, have, level, targetXp - currentXp - questTotal, false, null, false);
			Route route = RoutePlanner.route(skill, currentXp, targetXp, bankAll, kb, questXp);

			Goal goal = new Goal("skill:" + skill.name() + ":" + level, GoalCategory.SKILL_TARGET, level + " " + skill.getName(),
				WikiUrls.forTitle(skill.getName() + " training"), priority, stage);
			List<GoalRef> refs = new ArrayList<>();
			for (Need need : c.parents)
			{
				refs.add(new GoalRef(need.goal.getId(), need.goal.getName(), need.level));
			}
			targets.add(new GoalStatus(goal, List.of(gap), false, false, List.of(), List.of(), List.copyOf(refs), route,
				Ranker.score(c.best.status), questXp));
		}
		return new Synthesis(List.copyOf(targets), Map.copyOf(questUnblocks));
	}

	/** Every unfinished, non-hidden quest goal with no gaps at all, with its knowledge-base entry and current state. */
	private static List<ReadyQuest> readyQuests(List<GoalStatus> statuses, Set<String> hidden, Snapshot snapshot, KnowledgeBase kb)
	{
		List<ReadyQuest> ready = new ArrayList<>();
		for (GoalStatus status : statuses)
		{
			Goal goal = status.getGoal();
			if (goal.getCategory() != GoalCategory.QUEST || hidden.contains(goal.getId())
				|| !status.isReady() || status.isBankUnknown())
			{
				continue;
			}
			QuestEntry entry = kb.questById(Integer.parseInt(goal.getId().substring("quest:".length())));
			Quest quest = GapEngine.questByName(goal.getName());
			QuestState state = quest == null ? QuestState.NOT_STARTED : snapshot.getQuests().getOrDefault(quest, QuestState.NOT_STARTED);
			if (entry == null || quest == null || state == QuestState.FINISHED)
			{
				continue;
			}
			ready.add(new ReadyQuest(goal, entry, state));
		}
		return ready;
	}

	/**
	 * The xp {@code c}'s skill can take from ready quests: every fixed reward in the skill, then -
	 * only while still short - unspent lamps allowing the skill at the level reached by credited
	 * rewards, largest eligible first. One {@link QuestXp} per quest (fixed and lamp xp summed), largest first.
	 */
	private static List<QuestXp> questXpFor(Candidate c, List<ReadyQuest> readyQuests, Snapshot snapshot)
	{
		SkillState state = snapshot.getSkills().get(c.skill);
		long currentXp = state == null ? 0 : state.getXp();
		long needed = Experience.getXpForLevel(c.level) - currentXp;
		Map<ReadyQuest, Long> xpByQuest = new LinkedHashMap<>();
		long covered = 0;
		for (ReadyQuest q : readyQuests)
		{
			Long xp = q.entry.getRewardXp().get(c.skill);
			if (xp != null)
			{
				xpByQuest.merge(q, xp, Long::sum);
				covered += xp;
			}
		}
		List<Map.Entry<ReadyQuest, Integer>> lamps = new ArrayList<>();
		for (ReadyQuest q : readyQuests)
		{
			for (int i = 0; i < q.entry.getLamps().size(); i++)
			{
				QuestLamp lamp = q.entry.getLamps().get(i);
				if (!q.spentLamps[i] && lamp.getSkills().contains(c.skill))
				{
					lamps.add(Map.entry(q, i));
				}
			}
		}
		lamps.sort(Comparator.comparingLong((Map.Entry<ReadyQuest, Integer> e) -> -e.getKey().entry.getLamps().get(e.getValue()).getXp())
			.thenComparing(e -> e.getKey().goal.getName()).thenComparingInt(Map.Entry::getValue));
		while (covered < needed)
		{
			int have = Experience.getLevelForXp((int) Math.min(currentXp + covered, Integer.MAX_VALUE));
			boolean consumed = false;
			for (Map.Entry<ReadyQuest, Integer> e : lamps)
			{
				QuestLamp lamp = e.getKey().entry.getLamps().get(e.getValue());
				if (e.getKey().spentLamps[e.getValue()] || have < lamp.getMinLevel())
				{
					continue;
				}
				e.getKey().spentLamps[e.getValue()] = true;
				xpByQuest.merge(e.getKey(), lamp.getXp(), Long::sum);
				covered += lamp.getXp();
				consumed = true;
				// Restart largest-first selection: this lamp may unlock a higher-level lamp.
				break;
			}
			if (!consumed)
			{
				break;
			}
		}
		List<QuestXp> result = new ArrayList<>();
		for (Map.Entry<ReadyQuest, Long> e : xpByQuest.entrySet())
		{
			ReadyQuest q = e.getKey();
			result.add(new QuestXp(q.goal.getId(), q.goal.getName(), q.goal.getWikiUrl(), q.state, e.getValue()));
		}
		result.sort(Comparator.comparingLong((QuestXp q) -> -q.getXp()).thenComparing(QuestXp::getName));
		return result;
	}

	private static void collect(List<Gap> gaps, GoalStatus parent, Map<Skill, Map<String, Need>> needs)
	{
		for (Gap gap : gaps)
		{
			if (gap instanceof SkillLevelGap)
			{
				SkillLevelGap g = (SkillLevelGap) gap;
				needs.computeIfAbsent(g.getSkill(), k -> new LinkedHashMap<>())
					.merge(parent.getGoal().getId(), new Need(parent, g.getNeed()), (a, b) -> a.level <= b.level ? a : b);
			}
			else if (gap instanceof DiaryTaskGap)
			{
				collect(((DiaryTaskGap) gap).getGaps(), parent, needs);
			}
		}
	}

	private static final class Candidate
	{
		final Skill skill;
		final int level;
		final List<Need> parents;
		final Need best;

		Candidate(Skill skill, int level, List<Need> parents, Need best)
		{
			this.skill = skill;
			this.level = level;
			this.parents = parents;
			this.best = best;
		}
	}

	private static final class ReadyQuest
	{
		final Goal goal;
		final QuestEntry entry;
		final QuestState state;
		final boolean[] spentLamps;

		ReadyQuest(Goal goal, QuestEntry entry, QuestState state)
		{
			this.goal = goal;
			this.entry = entry;
			this.state = state;
			this.spentLamps = new boolean[entry.getLamps().size()];
		}
	}

	private static final class Need
	{
		final GoalStatus status;
		final Goal goal;
		final int level;

		Need(GoalStatus status, int level)
		{
			this.status = status;
			this.goal = status.getGoal();
			this.level = level;
		}
	}
}
