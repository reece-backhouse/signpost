package dev.reece.nta.engine;

import dev.reece.nta.engine.model.Advice;
import dev.reece.nta.engine.model.CombatLevelGap;
import dev.reece.nta.engine.model.DiaryTaskGap;
import dev.reece.nta.engine.model.DiaryTierGap;
import dev.reece.nta.engine.model.Gap;
import dev.reece.nta.engine.model.GearGap;
import dev.reece.nta.engine.model.Goal;
import dev.reece.nta.engine.model.GoalStatus;
import dev.reece.nta.engine.model.ItemGap;
import dev.reece.nta.engine.model.KudosGap;
import dev.reece.nta.engine.model.QuestPointsGap;
import dev.reece.nta.engine.model.QuestPrereqGap;
import dev.reece.nta.engine.model.RankedGoal;
import dev.reece.nta.engine.model.SkillLevelGap;
import java.util.ArrayList;
import java.util.List;

/**
 * Task 50: pure formatting of one {@link Engine#run} result into human-readable log lines, so the
 * ranking decision behind the Suggest panel's top three is visible in the client log without a
 * debugger attached. One line per top-three pick, plus one line for every id in {@link #WATCH}
 * regardless of whether it was picked - {@link dev.reece.nta.NextTargetPlugin} logs each line at
 * INFO. Pure: no {@link net.runelite.api.Client}, no I/O (global constraint: engine code is pure).
 */
public final class AdviceDiagnostics
{
	/** Goal ids always logged (task 50): the two bosses at the centre of the live user report. */
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
		return String.format(
			"pick %d: %s \"%s\" tier=%s score=%.2f stage=%d/%d gaps=%d why=\"%s\"",
			n, goal.getId(), goal.getName(), tierName(r), r.getScore(),
			goal.getStage(), advice.getAccountStage(), r.getStatus().getGaps().size(),
			advice.getWhys().getOrDefault(goal.getId(), ""));
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
			return "gear:" + g.getOwned() + "/" + g.getRequired();
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
		return gap.getClass().getSimpleName();
	}
}
