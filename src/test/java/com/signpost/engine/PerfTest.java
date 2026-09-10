package com.signpost.engine;

import com.google.gson.Gson;
import com.signpost.engine.model.Advice;
import com.signpost.engine.model.FocusDetail;
import com.signpost.engine.model.GoalStatus;
import com.signpost.engine.model.NextStep;
import com.signpost.engine.model.Route;
import com.signpost.engine.model.RouteStep;
import com.signpost.engine.model.SkillPlan;
import com.signpost.kb.KnowledgeBase;
import com.signpost.snapshot.Snapshot;
import com.signpost.store.AccountData;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import net.runelite.api.Experience;
import net.runelite.api.Quest;
import net.runelite.api.Skill;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bundled {@link KnowledgeBase} on a near-maxed account must not
 * exceed the engine's 1s budget, and the Herblore 61-&gt;70 route from a realistic bank
 * must cover some of the gap using the bank's Ranarr weed.
 */
class PerfTest
{
	private static final int RANARR_WEED_ID = 257;
	private static final int VIAL_OF_WATER_ID = 227;
	private static final int SNAPE_GRASS_ID = 231;

	@Test
	void engineAndNextStepStayUnderOneSecondOnAMaxedAccountAndTheHerbloreRouteUsesRanarr()
	{
		KnowledgeBase kb = KnowledgeBase.load(new Gson());
		GapEngine gapEngine = new GapEngine(new BoostTable());
		NextStepPicker picker = new NextStepPicker();
		Snapshot snapshot = maxedSnapshotWithHerbloreSixtyOne();

		// Warm up (JIT, class loading) - only the second run is measured.
		List<GoalStatus> warmStatuses = gapEngine.evaluate(snapshot, kb);
		picker.next(songOfTheElves(warmStatuses), snapshot, kb);

		long start = System.nanoTime();
		List<GoalStatus> statuses = gapEngine.evaluate(snapshot, kb);
		NextStep step = picker.next(songOfTheElves(statuses), snapshot, kb);
		long elapsedMs = (System.nanoTime() - start) / 1_000_000;

		assertTrue(elapsedMs < 1000, "GapEngine.evaluate + NextStepPicker.next took " + elapsedMs + "ms, budget is 1000ms");
		System.out.println("PerfTest: GapEngine.evaluate + NextStepPicker.next took " + elapsedMs + "ms (next step type: " + step.getType() + ")");

		Route route = RoutePlanner.route(Skill.HERBLORE, Experience.getXpForLevel(61), Experience.getXpForLevel(70), bankAll(snapshot), kb);

		System.out.println("PerfTest: Herblore 61->70 route from bank (412 Ranarr weed, 1200 Vial of water, 14 Snape grass):");
		for (RouteStep routeStep : route.getSteps())
		{
			System.out.println("  " + routeStep.getMethod().getName() + " x" + routeStep.getCount()
				+ " (level " + routeStep.getFromLevel() + " -> " + routeStep.getToLevel() + ", " + routeStep.getXpGained() + " xp)");
		}
		System.out.println("  uncoveredXp=" + route.getUncoveredXp() + " finalXp=" + route.getFinalXp());

		assertFalse(route.getSteps().isEmpty(), "expected at least one route step from the bank");
		boolean usesRanarr = route.getSteps().stream().anyMatch(s -> s.getMaterialsUsed().containsKey(RANARR_WEED_ID))
			|| route.getSteps().stream().flatMap(s -> s.getCrafts().stream()).anyMatch(s -> s.getMaterialsUsed().containsKey(RANARR_WEED_ID));
		assertTrue(usesRanarr, "expected the route to use some of the bank's Ranarr weed (id " + RANARR_WEED_ID + ")");
	}

	/**
	 * a fresh account focused on Song of the Elves has 8 skill gaps (Agility, Construction,
	 * Farming, Herblore, Hunter, Mining, Smithing, Woodcutting all req level 70) - {@link Engine#run}
	 * must compute a {@link SkillPlan} (route + shortfall) for every one of them, once, and still
	 * stay under the same 1s budget as the rest of the engine.
	 */
	@Test
	void focusedSongOfTheElvesWithEightSkillGapsStaysUnderOneSecondBudget()
	{
		KnowledgeBase kb = KnowledgeBase.load(new Gson());
		Engine engine = new Engine(new BoostTable());
		Snapshot snapshot = new SnapshotBuilder().build();
		AccountData data = focusOn("quest:" + Quest.SONG_OF_THE_ELVES.getId());

		// Warm up (JIT, class loading) - only the second run is measured.
		engine.run(snapshot, kb, data, Instant.now());

		long start = System.nanoTime();
		Advice advice = engine.run(snapshot, kb, data, Instant.now());
		long elapsedMs = (System.nanoTime() - start) / 1_000_000;

		assertTrue(elapsedMs < 1000, "Engine.run (focused Song of the Elves) took " + elapsedMs + "ms, budget is 1000ms");
		System.out.println("PerfTest: Engine.run with a focused Song of the Elves (8 skill gaps) took " + elapsedMs + "ms");

		FocusDetail focus = advice.getFocus();
		assertNotNull(focus, "expected a focus detail for the focused Song of the Elves goal");
		assertEquals(8, focus.getSkillPlans().size(), "expected one SkillPlan per Song of the Elves skill requirement");
	}

	private static AccountData focusOn(String goalId)
	{
		return new AccountData(new HashMap<>(), null, new HashMap<>(), new HashSet<>(), new ArrayList<>(), goalId, new HashSet<>());
	}

	private static GoalStatus songOfTheElves(List<GoalStatus> statuses)
	{
		String id = "quest:" + Quest.SONG_OF_THE_ELVES.getId();
		return statuses.stream().filter(s -> s.getGoal().getId().equals(id)).findFirst()
			.orElseThrow(() -> new AssertionError("no GoalStatus for Song of the Elves"));
	}

	private static Snapshot maxedSnapshotWithHerbloreSixtyOne()
	{
		SnapshotBuilder builder = new SnapshotBuilder();
		for (Skill skill : Skill.values())
		{
			builder.skill(skill, 99);
		}
		builder.skill(Skill.HERBLORE, 61);
		// The bank includes Ranarr weed, Vials of water, and Snape grass. Farming seeds have no
		// materials.json entry in the bundled kb (grown herbs aren't modelled as a farming seed
		// material there). Snape grass is the bundled Prayer potion(3) recipe's second
		// ingredient.
		builder.bankItem(RANARR_WEED_ID, "Ranarr weed", 412);
		builder.bankItem(VIAL_OF_WATER_ID, "Vial of water", 1200);
		builder.bankItem(SNAPE_GRASS_ID, "Snape grass", 14);
		return builder.build();
	}

	private static Map<Integer, Integer> bankAll(Snapshot snapshot)
	{
		Map<Integer, Integer> result = new HashMap<>(snapshot.getBank());
		snapshot.getInventory().forEach((id, qty) -> result.merge(id, qty, Integer::sum));
		snapshot.getEquipment().forEach((id, qty) -> result.merge(id, qty, Integer::sum));
		return result;
	}
}
