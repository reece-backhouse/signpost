package com.signpost.engine;

import com.signpost.engine.model.Route;
import com.signpost.engine.model.RouteStep;
import com.signpost.engine.model.Shortfall;
import com.signpost.engine.model.ShortfallItem;
import com.signpost.engine.model.SkillPlan;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Decides how much of each route step's materials and each shortfall
 * line's {@code have} the group ironman shared storage supplied, so the detail view can print
 * "in group storage: N". Pure: {@code (SkillPlan, storage) -> SkillPlan}.
 *
 * <p>Storage is spent first, walking the steps and each step's crafts in planner order against a
 * working copy, since the route's pool is one bank-plus-storage sum. Shortfall lines - the primary
 * items, their craft-from children and the alternative - all restate the same post-route
 * {@code have}, so each shows {@code min(have, left)} of what the steps left and consumes nothing.
 */
public final class GroupStorageShares
{
	private GroupStorageShares()
	{
	}

	/** Returns {@code plan} itself when {@code storage} is empty (every non-group account), so a route reused from {@code NextStepPicker} stays the same object. */
	public static SkillPlan apply(SkillPlan plan, Map<Integer, Integer> storage)
	{
		if (storage.isEmpty())
		{
			return plan;
		}
		Map<Integer, Integer> left = new HashMap<>(storage);
		Route route = plan.getRoute();
		Route sharedRoute = route == null
			? null
			: new Route(shareSteps(route.getSteps(), left), route.getUncoveredXp(), route.getFinalXp(), route.getSimulatedBank());
		Shortfall sharedShortfall = shareShortfall(plan.getShortfall(), left);
		return new SkillPlan(plan.getSkill(), plan.getFromLevel(), plan.getToLevel(), plan.getFromXp(), plan.getToXp(), plan.isRecommended(),
			sharedRoute, sharedShortfall, plan.isCovered(), plan.getSource());
	}

	private static List<RouteStep> shareSteps(List<RouteStep> steps, Map<Integer, Integer> left)
	{
		List<RouteStep> result = new ArrayList<>();
		for (RouteStep step : steps)
		{
			// Crafts run before the step's own action, so they draw on the storage first.
			List<RouteStep> crafts = shareSteps(step.getCrafts(), left);
			Map<Integer, Integer> share = new LinkedHashMap<>();
			for (Map.Entry<Integer, Integer> used : step.getMaterialsUsed().entrySet())
			{
				int taken = Math.min(used.getValue(), left.getOrDefault(used.getKey(), 0));
				if (taken > 0)
				{
					share.put(used.getKey(), taken);
					left.merge(used.getKey(), -taken, Integer::sum);
				}
			}
			result.add(new RouteStep(step.getMethod(), step.getCount(), step.getFromLevel(), step.getToLevel(), step.getXpGained(),
				step.getMaterialsUsed(), List.copyOf(crafts), Map.copyOf(share), step.getQuest()));
		}
		return result;
	}

	private static Shortfall shareShortfall(Shortfall shortfall, Map<Integer, Integer> left)
	{
		if (shortfall == null)
		{
			return null;
		}
		return new Shortfall(shortfall.getMethod(), shareItems(shortfall.getItems(), left), shortfall.getXpShort(), shortfall.getActionsNeeded(),
			shareShortfall(shortfall.getAlternative(), left), shortfall.getUnobtainable(), shortfall.getNotes());
	}

	private static List<ShortfallItem> shareItems(List<ShortfallItem> items, Map<Integer, Integer> left)
	{
		List<ShortfallItem> result = new ArrayList<>();
		for (ShortfallItem item : items)
		{
			Integer id = item.getItem().getId();
			int share = id == null ? 0 : Math.min(item.getHave(), left.getOrDefault(id, 0));
			result.add(new ShortfallItem(item.getItem(), item.getHave(), item.getNeed(), item.getSources(), shareItems(item.getCraftFrom(), left),
				item.getWikiUrl(), item.getPlans(), share));
		}
		return List.copyOf(result);
	}
}
