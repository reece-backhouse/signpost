package com.signpost.engine;

import com.signpost.engine.model.Goal;
import com.signpost.engine.model.GoalCategory;
import com.signpost.engine.model.GoalStatus;
import com.signpost.engine.model.GoalObjective;
import com.signpost.kb.GearLadder;
import com.signpost.kb.KnowledgeBase;
import com.signpost.kb.MilestoneEntry;
import com.signpost.kb.WikiUrls;
import com.signpost.snapshot.Snapshot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class GearUpgradeSynthesiser
{
	private GearUpgradeSynthesiser() {}

	static String id(GearLadder ladder, GearLadder.Rung rung)
	{
		return "gear:" + ladder.getStyle() + ":" + ladder.getSlot() + ":" + rung.getId();
	}

	static List<GoalStatus> run(List<GearLadder> ladders, Snapshot snapshot, KnowledgeBase kb, Set<String> manual,
		GapEngine gaps, GearComparison comparison)
	{
		Map<String, GoalStatus> providers = new HashMap<>();
		List<GoalStatus> result = new ArrayList<>();
		for (GearLadder ladder : ladders)
		{
			String replaces = null;
			for (GearLadder.Rung rung : ladder.getRungs())
			{
				String coveringName = comparison.coveringName(rung.getVariants(), ladder.getStyle());
				if (coveringName != null)
				{
					replaces = coveringName;
					continue;
				}
				MilestoneEntry source = kb.milestoneById(rung.getProvider());
				if (ProgressionObjectives.providerGreenLogged(source, snapshot)) continue;
				if (snapshot.getAccountType().isIron() && rung.isGeOnly()) continue;
				GoalStatus provider = providers.computeIfAbsent(rung.getProvider(),
					key -> gaps.providerStatus(key, snapshot, kb, manual, comparison));
				Goal goal = new Goal(id(ladder, rung), GoalCategory.GEAR_UPGRADE, rung.getName(), WikiUrls.forTitle(rung.getName()),
					provider.getGoal().getPriority(), provider.getGoal().getStage(), new Goal.Upgrade(ladder.getStyle(), ladder.getSlot(), rung, replaces));
				List<String> notes = new ArrayList<>();
				notes.add("Provider: " + provider.getGoal().getName());
				notes.addAll(provider.getNotes());
				GoalObjective objective = ProgressionObjectives.upgrade(source, rung, kb, comparison);
				result.add(new GoalStatus(goal, provider.getGaps(), provider.isReady(), provider.isBankUnknown() || objective.isBankUnknown(),
					List.copyOf(notes), provider.getMet(), List.of(), null, 0, List.of(), objective));
				break;
			}
		}
		return result;
	}
}
