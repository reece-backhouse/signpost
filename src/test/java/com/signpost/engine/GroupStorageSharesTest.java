package com.signpost.engine;

import com.signpost.engine.model.Route;
import com.signpost.engine.model.RouteStep;
import com.signpost.engine.model.Shortfall;
import com.signpost.engine.model.ShortfallItem;
import com.signpost.engine.model.SkillPlan;
import com.signpost.kb.ItemQuantity;
import com.signpost.kb.MethodEntry;
import java.util.List;
import java.util.Map;
import net.runelite.api.Skill;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The group storage share per route step / shortfall line is decided by the engine, in planner order. */
class GroupStorageSharesTest
{
	private static final int UNF = 200;
	private static final int RANARR = 201;

	private static MethodEntry method(String name, int... materialIds)
	{
		List<ItemQuantity> materials = new java.util.ArrayList<>();
		for (int id : materialIds)
		{
			materials.add(new ItemQuantity("item " + id, id, 1));
		}
		return new MethodEntry(Skill.HERBLORE, name, name, 1, 10, materials, List.of(), List.of(), true, false, null, false, true);
	}

	@Test
	void stepsConsumeStorageFirstInRouteOrderAndShortfallLinesShareTheRemainderWithoutConsuming()
	{
		RouteStep craft = new RouteStep(method("make unf", RANARR), 2, 1, 1, 0L, Map.of(RANARR, 2), List.of());
		RouteStep first = new RouteStep(method("brew", UNF), 4, 1, 2, 40L, Map.of(UNF, 4), List.of(craft));
		RouteStep second = new RouteStep(method("restore", UNF), 4, 2, 3, 40L, Map.of(UNF, 4), List.of());
		Route route = new Route(List.of(first, second), 100L, 80L, Map.of(UNF, 3, RANARR, 1));
		ItemQuantity unf = new ItemQuantity("unf", UNF, 1);
		ItemQuantity ranarr = new ItemQuantity("ranarr", RANARR, 1);
		ShortfallItem child = new ShortfallItem(ranarr, 1, 10, List.of(), List.of());
		Shortfall alternative = new Shortfall(method("alt", UNF), List.of(new ShortfallItem(unf, 3, 10, List.of(), List.of())), 100L, 10);
		Shortfall shortfall = new Shortfall(method("restore", UNF), List.of(new ShortfallItem(unf, 3, 10, List.of(), List.of(child))), 100L, 10, alternative);
		SkillPlan plan = new SkillPlan(Skill.HERBLORE, 1, 3, 0L, 100L, false, route, shortfall, false, "quest");

		SkillPlan shared = GroupStorageShares.apply(plan, Map.of(UNF, 5, RANARR, 3));

		List<RouteStep> steps = shared.getRoute().getSteps();
		assertEquals(Map.of(UNF, 4), steps.get(0).getFromGroupStorage(), "first step takes 4 of the 5");
		assertEquals(Map.of(RANARR, 2), steps.get(0).getCrafts().get(0).getFromGroupStorage(), "its craft takes 2 of the 3 ranarr");
		assertEquals(Map.of(UNF, 1), steps.get(1).getFromGroupStorage(), "second step gets the last 1");
		assertEquals(0, shared.getShortfall().getItems().get(0).getInGroupStorage(), "storage is used up: have 3 is bank");
		assertEquals(1, shared.getShortfall().getItems().get(0).getCraftFrom().get(0).getInGroupStorage(), "craft-from child: min(have 1, 1 left)");
		assertEquals(0, shared.getShortfall().getAlternative().getItems().get(0).getInGroupStorage(), "alternative restates the same have");
		assertEquals(plan.getShortfall().getMethod(), shared.getShortfall().getMethod());
		assertEquals(4, steps.get(0).getCount(), "everything else is untouched");
	}

	@Test
	void shortfallItemsRepeatingAnItemAllShowTheSameShareBecauseNothingIsConsumed()
	{
		Route route = new Route(List.of(), 100L, 0L, Map.of(UNF, 4));
		ItemQuantity unf = new ItemQuantity("unf", UNF, 1);
		ShortfallItem child = new ShortfallItem(unf, 4, 10, List.of(), List.of());
		Shortfall alternative = new Shortfall(method("alt", UNF), List.of(new ShortfallItem(unf, 4, 10, List.of(), List.of())), 100L, 10);
		Shortfall shortfall = new Shortfall(method("m", UNF), List.of(new ShortfallItem(unf, 4, 10, List.of(), List.of(child))), 100L, 10, alternative);
		SkillPlan plan = new SkillPlan(Skill.HERBLORE, 1, 3, 0L, 100L, false, route, shortfall, false, "quest");

		SkillPlan shared = GroupStorageShares.apply(plan, Map.of(UNF, 3));

		assertEquals(3, shared.getShortfall().getItems().get(0).getInGroupStorage());
		assertEquals(3, shared.getShortfall().getItems().get(0).getCraftFrom().get(0).getInGroupStorage());
		assertEquals(3, shared.getShortfall().getAlternative().getItems().get(0).getInGroupStorage());
	}

	@Test
	void coveredPlanWithNoShortfallAndEmptyStorageIsUnchanged()
	{
		RouteStep step = new RouteStep(method("brew", UNF), 4, 1, 2, 40L, Map.of(UNF, 4), List.of());
		Route route = new Route(List.of(step), 0L, 80L, Map.of());
		SkillPlan plan = new SkillPlan(Skill.HERBLORE, 1, 2, 0L, 40L, false, route, null, true, "quest");

		SkillPlan shared = GroupStorageShares.apply(plan, Map.of());

		assertEquals(Map.of(), shared.getRoute().getSteps().get(0).getFromGroupStorage());
		assertEquals(null, shared.getShortfall());
	}
}
