package com.signpost.engine;

import com.signpost.engine.model.Advice;
import com.signpost.engine.model.CombatLevelGap;
import com.signpost.engine.model.DiaryTaskGap;
import com.signpost.engine.model.DiaryTierGap;
import com.signpost.engine.model.Gap;
import com.signpost.engine.model.GearGap;
import com.signpost.engine.model.Goal;
import com.signpost.engine.model.GoalCategory;
import com.signpost.engine.model.GoalStatus;
import com.signpost.engine.model.ItemGap;
import com.signpost.engine.model.KudosGap;
import com.signpost.engine.model.PrerequisiteGap;
import com.signpost.engine.model.QuestPointsGap;
import com.signpost.engine.model.QuestPrereqGap;
import com.signpost.engine.model.QuestXp;
import com.signpost.engine.model.RankedGoal;
import com.signpost.engine.model.SkillLevelGap;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure formatting of one {@link Engine#run} result into human-readable log lines, so the
 * ranking decision behind the Suggest panel's top three is visible in the client log without a
 * debugger attached. One line per top-three pick (with its "Why?" explanation lines
 * joined by " | "), plus one line for every id in {@link #WATCH}
 * regardless of whether it was picked - {@link com.signpost.NextTargetPlugin} logs each line at
 * INFO. Pure: no {@link net.runelite.api.Client}, no I/O (global constraint: engine code is pure).
 */
public final class AdviceDiagnostics
{
	/** Goal ids always logged to make boss-ranking decisions visible. */
	static final List<String> WATCH = List.of("boss:moons-of-peril", "boss:god-wars-dungeon");

	private AdviceDiagnostics()
	{
	}

	public static List<String> lines(Advice advice)
	{
		List<String> lines = new ArrayList<>();
		List<RankedGoal> picked = advice.getPicked();
		for (int i = 0; i < picked.size(); i++)
		{
			lines.add(pickLine(i + 1, picked.get(i), advice));
		}
		for (String id : WATCH)
		{
			lines.add(watchLine(id, advice));
		}
		return lines;
	}

	private static String pickLine(int n, RankedGoal r, Advice advice)
	{
		Goal goal = r.getStatus().getGoal();
		// A quest pick also prints how many goals it unblocks; a skill target prints the quest reward xp it counts.
		String dependents = goal.getCategory() == GoalCategory.QUEST ? " dependents=" + r.getUnblocks().size() : "";
		if (goal.getCategory() == GoalCategory.SKILL_TARGET)
		{
			dependents = " questXp=" + r.getStatus().getQuestXp().stream().mapToLong(QuestXp::getXp).sum();
		}
		return String.format(
			"pick %d: %s \"%s\" tier=%s score=%.2f stage=%d/%d gaps=%d%s why=\"%s\" explain=\"%s\"",
			n, goal.getId(), goal.getName(), tierName(r), r.getScore(),
			goal.getStage(), advice.getAccountStage(), r.getStatus().getGaps().size(), dependents,
			advice.getWhys().getOrDefault(goal.getId(), ""),
			String.join(" | ", advice.getExplanations().getOrDefault(goal.getId(), List.of())));
	}

	private static String watchLine(String id, Advice advice)
	{
		RankedGoal ranked = findRanked(advice.getRanked(), id);
		if (ranked != null)
		{
			GoalStatus status = ranked.getStatus();
			boolean ready = status.isReady() && !status.isBankUnknown();
			return String.format("watch %s: tier=%s score=%.2f ready=%b gaps=[%s]",
				id, tierName(ranked), ranked.getScore(), ready, gapList(status.getGaps()));
		}
		boolean stillTracked = advice.getStatuses().stream().anyMatch(s -> s.getGoal().getId().equals(id));
		return "watch " + id + ": " + (stillTracked ? "hidden" : "done");
	}

	private static RankedGoal findRanked(List<RankedGoal> ranked, String id)
	{
		for (RankedGoal r : ranked)
		{
			if (r.getStatus().getGoal().getId().equals(id))
			{
				return r;
			}
		}
		return null;
	}

	private static String tierName(RankedGoal r)
	{
		return r.getTier().name().toLowerCase();
	}

	private static String gapList(List<Gap> gaps)
	{
		List<String> texts = new ArrayList<>();
		for (Gap gap : gaps)
		{
			texts.add(gapText(gap));
		}
		return String.join(", ", texts);
	}

	/** Short "<kind>:<have>/<need>"-shaped text per gap kind - mirrors {@link GapFingerprint}'s dispatch, but keeps the have/need values a fingerprint deliberately drops. */
	private static String gapText(Gap gap)
	{
		if (gap instanceof GearGap)
		{
			GearGap g = (GearGap) gap;
			return "gear:" + g.getRole() + (g.isBankUnknown() ? " (unverified)" : " (missing)");
		}
		if (gap instanceof SkillLevelGap)
		{
			SkillLevelGap g = (SkillLevelGap) gap;
			return g.getSkill().getName() + " " + g.getHave() + "/" + g.getNeed();
		}
		if (gap instanceof CombatLevelGap)
		{
			CombatLevelGap g = (CombatLevelGap) gap;
			return "combat " + g.getHave() + "/" + g.getNeed();
		}
		if (gap instanceof QuestPrereqGap)
		{
			return "quest:" + ((QuestPrereqGap) gap).getQuest().getName();
		}
		if (gap instanceof QuestPointsGap)
		{
			QuestPointsGap g = (QuestPointsGap) gap;
			return "qp " + g.getHave() + "/" + g.getNeed();
		}
		if (gap instanceof KudosGap)
		{
			KudosGap g = (KudosGap) gap;
			return "kudos " + g.getHave() + "/" + g.getNeed();
		}
		if (gap instanceof DiaryTierGap)
		{
			return "diary:" + ((DiaryTierGap) gap).getTier().name();
		}
		if (gap instanceof DiaryTaskGap)
		{
			return "diary task:" + ((DiaryTaskGap) gap).getText();
		}
		if (gap instanceof ItemGap)
		{
			ItemGap g = (ItemGap) gap;
			return g.getName() + " " + (g.getHave() == null ? "?" : g.getHave()) + "/" + g.getNeed();
		}
		if (gap instanceof PrerequisiteGap)
		{
			return "prereq:" + ((PrerequisiteGap) gap).getGoalId();
		}
		return gap.getClass().getSimpleName();
	}
}
