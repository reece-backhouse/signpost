package dev.reece.nta.engine;

import dev.reece.nta.engine.model.DiaryTaskGap;
import dev.reece.nta.engine.model.Gap;
import dev.reece.nta.engine.model.Goal;
import dev.reece.nta.engine.model.GoalCategory;
import dev.reece.nta.engine.model.GoalRef;
import dev.reece.nta.engine.model.GoalStatus;
import dev.reece.nta.engine.model.Route;
import dev.reece.nta.engine.model.SkillLevelGap;
import dev.reece.nta.kb.KnowledgeBase;
import dev.reece.nta.kb.WikiUrls;
import dev.reece.nta.snapshot.SkillState;
import dev.reece.nta.snapshot.Snapshot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Experience;
import net.runelite.api.Skill;

/**
 * Spec ruling 28: skill targets are goals. For each skill, the lowest unmet level any upcoming
 * goal requires (top-level {@link SkillLevelGap}s, those inside a {@link DiaryTaskGap}, and
 * recommended-profile skill gaps alike) becomes one {@code skill:<SKILL>:<level>} goal of
 * {@link GoalCategory#SKILL_TARGET}, carrying every parent with a gap in that skill (lowest level
 * first), the priority, stage and score of the best-scoring parent needing exactly that level, and the {@link RoutePlanner} route from the current bank (computed once here; the same
 * object is reused when the target is focused). A hidden parent, or one more than one stage above
 * the account (the same "later" rule {@link Ranker} applies), never contributes. Pure: no
 * {@link net.runelite.api.Client}, no I/O.
 */
final class SkillTargetSynthesiser
{
	private SkillTargetSynthesiser()
	{
	}

	static List<GoalStatus> synthesise(List<GoalStatus> statuses, Set<String> hidden, int accountStage, Snapshot snapshot, KnowledgeBase kb)
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
		List<GoalStatus> targets = new ArrayList<>();
		for (Map.Entry<Skill, Map<String, Need>> entry : needs.entrySet())
		{
			Skill skill = entry.getKey();
			List<Need> parents = new ArrayList<>(entry.getValue().values());
			parents.sort(Comparator.comparingInt((Need n) -> n.level).thenComparing(n -> -n.goal.getPriority()).thenComparing(n -> n.goal.getName()));
			int level = parents.get(0).level;
			// Priority, stage and score cap come from the best-scoring parent this level actually
			// unlocks (fix round 1) - a goal needing a higher level is listed as a parent but lends
			// nothing (otherwise "52 Prayer" for two hard diaries would rank with Vorkath's priority).
			Need best = parents.stream().filter(n -> n.level == level)
				.max(Comparator.comparingDouble((Need n) -> Ranker.score(n.status)).thenComparing(n -> n.goal.getPriority())
					.thenComparing(n -> n.goal.getName(), Comparator.reverseOrder()))
				.orElseThrow();
			int priority = best.goal.getPriority();
			int stage = best.goal.getStage();

			SkillState state = snapshot.getSkills().get(skill);
			int have = state == null ? 1 : state.getLevel();
			long currentXp = state == null ? 0 : state.getXp();
			long targetXp = Experience.getXpForLevel(level);
			SkillLevelGap gap = new SkillLevelGap(skill, have, level, targetXp - currentXp, false, null, false);
			Route route = RoutePlanner.route(skill, currentXp, targetXp, bankAll, kb);

			Goal goal = new Goal("skill:" + skill.name() + ":" + level, GoalCategory.SKILL_TARGET, level + " " + skill.getName(),
				WikiUrls.forTitle(skill.getName() + " training"), priority, stage);
			List<GoalRef> refs = new ArrayList<>();
			for (Need need : parents)
			{
				refs.add(new GoalRef(need.goal.getId(), need.goal.getName(), need.level));
			}
			targets.add(new GoalStatus(goal, List.of(gap), false, false, List.of(), List.of(), List.copyOf(refs), route, Ranker.score(best.status)));
		}
		return targets;
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
