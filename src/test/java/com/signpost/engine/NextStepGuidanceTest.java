package com.signpost.engine;

import com.signpost.engine.model.BringItemStatus;
import com.signpost.engine.model.NextStep;
import com.signpost.engine.model.PlanOffer;
import com.signpost.engine.model.Route;
import com.signpost.engine.model.RouteStep;
import com.signpost.engine.model.Shortfall;
import com.signpost.engine.model.ShortfallItem;
import com.signpost.engine.model.SkillLevelGap;
import com.signpost.kb.BringItem;
import com.signpost.kb.GatheringPlan;
import com.signpost.kb.GatheringRequires;
import com.signpost.kb.ItemQuantity;
import com.signpost.kb.KnowledgeBase;
import com.signpost.kb.MethodEntry;
import com.signpost.snapshot.Snapshot;
import java.util.List;
import java.util.Map;
import net.runelite.api.Skill;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NextStepGuidanceTest
{
	@Test
	void mergedTrainingStepRecommendsOnlyTheAffordableBatchAcrossContainers()
	{
		MethodEntry method = method("Arrow shafts", List.of(new ItemQuantity("Logs", 1511, 2),
			new ItemQuantity("Feather", 314, 1)), List.of(new ItemQuantity("Arrow shaft", 52, 15)));
		Snapshot snapshot = new SnapshotBuilder().bankItem(1511, "Logs", 2).inventoryItem(1511, "Logs", 1)
			.equipmentItem(1511, "Logs", 1).groupStorageItem(1511, "Logs", 3).bankItem(314, "Feather", 10).build();
		NextStep result = NextStepGuidance.derive(training(method, 10, List.of()), null, snapshot, kb(method));

		assertEquals("Make 45 Arrow shaft", result.getDoText());
		assertEquals(6, result.getBringItems().get(0).getQuantity());
		assertEquals(3, result.getBringItems().get(1).getQuantity());
		assertTrue(result.getBringItems().stream().allMatch(BringItemStatus::isOwned));
		assertEquals("any bank", result.getWhere(), "uncurated methods remain usable");
	}

	@Test
	void trainingRoundsFractionalMaterialsUpForTheSelectedCount()
	{
		MethodEntry method = method("Practice", List.of(new ItemQuantity("Supply", 1, 0.4)), List.of());
		// Four separately planned one-action batches consumed four rounded supplies.
		// The immediate combined batch needs ceil(4 * 0.4), not that historical total.
		RouteStep merged = new RouteStep(method, 4, 1, 2, 40, Map.of(1, 4), List.of());
		NextStep next = NextStep.skill(new SkillLevelGap(Skill.HERBLORE, 1, 2, 40, false, null, false),
			new Route(List.of(merged), 0, 40, Map.of()));
		NextStep result = NextStepGuidance.derive(next, null,
			new SnapshotBuilder().bankItem(1, "Supply", 2).build(), kb(method));
		assertEquals("Make 4 Practice", result.getDoText());
		assertEquals(2, result.getBringItems().get(0).getQuantity());
	}

	@Test
	void trainingStartsWithTheDeepestIntermediateCraft()
	{
		MethodEntry finished = method("Potion", List.of(new ItemQuantity("Unfinished potion", 2, 1)), List.of());
		MethodEntry intermediate = method("Unfinished potion", List.of(new ItemQuantity("Herb", 1, 1)),
			List.of(new ItemQuantity("Unfinished potion", 2, 1)));
		RouteStep craft = new RouteStep(intermediate, 8, 1, 1, 0, Map.of(1, 8), List.of());
		NextStep result = NextStepGuidance.derive(training(finished, 8, List.of(craft)), null,
			new SnapshotBuilder().bankItem(1, "Herb", 5).build(), kb(finished, intermediate));
		assertEquals("Make 5 Unfinished potion", result.getDoText());
		assertEquals(1, result.getBringItems().get(0).getItem().getId());
		assertEquals(5, result.getBringItems().get(0).getQuantity());
	}

	@Test
	void unaffordableTrainingFallsBackToTheMissingMaterialLoop()
	{
		MethodEntry method = method("Potion", List.of(new ItemQuantity("Herb", 1, 1)), List.of());
		Shortfall missing = new Shortfall(method, List.of(item(1, 0, 5, List.of(offer(1, "Walk to the patch. Pick herbs.")), List.of())));
		NextStep result = NextStepGuidance.derive(training(method, 5, List.of()), missing,
			new SnapshotBuilder().build(), kb(method));
		assertEquals("Walk to the patch.", result.getWhere());
		assertEquals("Walk to the patch. Pick herbs.", result.getDoText());
	}

	@Test
	void gatheringSkipsOwnedItemsAndUsesOnlyTheOfferedActionableSteps()
	{
		PlanOffer owned = offer(1, "Do not gather owned herbs.");
		PlanOffer blocked = new PlanOffer(offer(2, "Locked shortcut").getPlan(), false, List.of("Agility 70"),
			List.of("Locked shortcut"), List.of(), List.of(), List.of());
		PlanOffer available = offer(2, "Walk to Taverley Dungeon. Collect the scales.");
		Shortfall shortfall = new Shortfall(null, List.of(item(1, 5, 5, List.of(owned), List.of()),
			item(2, 0, 5, List.of(blocked, available), List.of())));
		NextStep result = NextStepGuidance.derive(NextStep.none(), shortfall, new SnapshotBuilder().build(), kb());
		assertEquals("Walk to Taverley Dungeon.", result.getWhere());
		assertEquals("Walk to Taverley Dungeon. Collect the scales.", result.getDoText());
	}

	@Test
	void gatheringDoesNotSelectAnOwnedCraftIngredientFromTheParentsCollectedOffers()
	{
		PlanOffer owned = offer(1, "Gather herbs.");
		PlanOffer missing = offer(2, "Buy vials of water");
		ShortfallItem parent = item(3, 0, 5, List.of(owned, missing),
			List.of(item(1, 5, 5, List.of(owned), List.of()), item(2, 0, 5, List.of(missing), List.of())));
		NextStep result = NextStepGuidance.derive(NextStep.none(), new Shortfall(null, List.of(parent)),
			new SnapshotBuilder().build(), kb());
		assertEquals("Buy vials of water", result.getDoText());
		assertEquals("Buy vials of water", result.getWhere(), "one sentence needs no full stop");
	}

	@Test
	void gatheringUsesAnAvailableAlternativeAndItsOwnBringItems()
	{
		List<BringItemStatus> tools = NextStepGuidance.items(List.of(new BringItem("Rope", 954)),
			new SnapshotBuilder().inventoryItem(954, "Rope", 1).build());
		PlanOffer offer = new PlanOffer(offer(1, "Unavailable").getPlan(), false, List.of("Quest"), List.of(),
			List.of(List.of(), List.of("Enter the cave. Collect herbs.")), List.of(), List.of(List.of(), tools));
		Shortfall alternative = new Shortfall(null, List.of(item(1, 0, 5, List.of(offer), List.of())));
		NextStep result = NextStepGuidance.derive(NextStep.none(), new Shortfall(null, List.of(), 0, 0, alternative),
			new SnapshotBuilder().build(), kb());
		assertEquals("Enter the cave.", result.getWhere());
		assertEquals("Enter the cave. Collect herbs.", result.getDoText());
		assertEquals("[x] Rope x1", result.getBring());
	}

	@Test
	void gatheringPreservesNameMatchedPlansWhenTheMaterialIdHasNoPlan()
	{
		ShortfallItem item = new ShortfallItem(new ItemQuantity("Material 2", 99, 1), 0, 5, List.of(), List.of(),
			null, List.of(offer(2, "Walk to the patch.")));
		NextStep result = NextStepGuidance.derive(NextStep.none(), new Shortfall(null, List.of(item)),
			new SnapshotBuilder().build(), kb());
		assertEquals("Walk to the patch.", result.getDoText());
	}

	private static NextStep training(MethodEntry method, int count, List<RouteStep> crafts)
	{
		RouteStep step = new RouteStep(method, count, 1, 20, count, Map.of(), crafts);
		return NextStep.skill(new SkillLevelGap(method.getSkill(), 1, 20, 1000, false, null, false),
			new Route(List.of(step), 0, count, Map.of()));
	}

	private static MethodEntry method(String name, List<ItemQuantity> materials, List<ItemQuantity> outputs)
	{
		return new MethodEntry(Skill.HERBLORE, name, name, 1, 10, materials, outputs, List.of(), false, false, null, false, true);
	}

	private static KnowledgeBase kb(MethodEntry... methods)
	{
		return KnowledgeBase.of("test", "test", List.of(), List.of(), List.of(), Map.of(), List.of(methods), List.of());
	}

	private static PlanOffer offer(int id, String firstStep)
	{
		GatheringRequires requires = new GatheringRequires(List.of(), null, List.of(), List.of(), null);
		GatheringPlan plan = new GatheringPlan("Material " + id, id, "Gather", requires, null, List.of(), List.of(), null);
		return new PlanOffer(plan, true, List.of(), List.of(firstStep), List.of(), List.of(), List.of());
	}

	private static ShortfallItem item(int id, int have, int need, List<PlanOffer> offers, List<ShortfallItem> ingredients)
	{
		return new ShortfallItem(new ItemQuantity("Material " + id, id, 1), have, need, List.of(), ingredients, null, offers);
	}
}
