package dev.reece.nta.engine;

import com.google.gson.Gson;
import dev.reece.nta.engine.model.GoalStatus;
import dev.reece.nta.engine.model.NextStep;
import dev.reece.nta.engine.model.Route;
import dev.reece.nta.engine.model.RouteStep;
import dev.reece.nta.kb.KnowledgeBase;
import dev.reece.nta.snapshot.Snapshot;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Experience;
import net.runelite.api.Quest;
import net.runelite.api.Skill;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task 38 (ticket F5): the real bundled {@link KnowledgeBase} on a near-maxed account must not
 * blow the engine's 1s budget, and the Herblore 61-&gt;70 route from a realistic bank (the ticket's
 * headline C6 example) must actually cover some of the gap using the bank's Ranarr weed.
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
		// The ticket's C6 headline example bank. "Toadflax seed" from the ticket prose has no
		// materials.json entry in the bundled kb (grown herbs aren't modelled as a farming seed
		// material there), so Snape grass - the bundled Prayer potion(3) recipe's second
		// ingredient - stands in as the third bank item.
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
