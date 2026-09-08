package dev.reece.nta.engine;

import com.google.gson.Gson;
import dev.reece.nta.engine.model.Route;
import dev.reece.nta.engine.model.RouteStep;
import dev.reece.nta.kb.KnowledgeBase;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Experience;
import net.runelite.api.Skill;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Task 35: {@link RoutePlanner}, per spec ruling 15. */
class RoutePlannerTest
{
	private static final int DRAGON_BONES_ID = 536;

	/** Final-review C1: on the bundled KB, burying 300 Dragon bones must spend exactly 300 bones, not refill the bank each action. */
	@Test
	void prayerRouteFromThreeHundredDragonBonesOnTheRealKbConsumesExactlyThreeHundred()
	{
		KnowledgeBase kb = KnowledgeBase.load(new Gson());
		Map<Integer, Integer> bank = new HashMap<>();
		bank.put(DRAGON_BONES_ID, 300);
		long fromXp = Experience.getXpForLevel(70);
		long toXp = Experience.getXpForLevel(77);

		Route route = RoutePlanner.route(Skill.PRAYER, fromXp, toXp, bank, kb);

		int bonesUsed = route.getSteps().stream().mapToInt(s -> s.getMaterialsUsed().getOrDefault(DRAGON_BONES_ID, 0)).sum();
		assertEquals(300, bonesUsed, "steps: " + route.getSteps());
		assertEquals(0, route.getSimulatedBank().getOrDefault(DRAGON_BONES_ID, 0));
		assertEquals(fromXp + 300 * 72, route.getFinalXp(), "300 bones at 72 xp each");
		assertEquals(toXp - route.getFinalXp(), route.getUncoveredXp());
		assertTrue(route.getUncoveredXp() > 0, "300 bones cannot cover 70->77");
	}

	@Test
	void switchesToTheHigherXpMethodExactlyAtItsUnlockLevelAndConsumesTheSharedMaterialOnceAcrossBoth()
	{
		KnowledgeBase kb = new KbBuilder()
			.method(Skill.HERBLORE, "Prayer potion(3)", 1, 5)
			.material(10, 1)
			.method(Skill.HERBLORE, "Saradomin brew(3)", 2, 100)
			.material(10, 1)
			.build();
		Map<Integer, Integer> bank = new HashMap<>();
		bank.put(10, 10_000);

		long toXp = Experience.getXpForLevel(3);
		Route route = RoutePlanner.route(Skill.HERBLORE, 0, toXp, bank, kb);

		assertEquals(2, route.getSteps().size());

		RouteStep first = route.getSteps().get(0);
		assertEquals("Prayer potion(3)", first.getMethod().getName());
		assertEquals(1, first.getFromLevel());
		assertEquals(2, first.getToLevel());
		assertEquals(17, first.getCount());
		assertEquals(17, first.getMaterialsUsed().get(10));

		RouteStep second = route.getSteps().get(1);
		assertEquals("Saradomin brew(3)", second.getMethod().getName());
		assertEquals(2, second.getFromLevel());
		assertEquals(1, second.getCount());
		assertEquals(1, second.getMaterialsUsed().get(10));

		assertEquals(0, route.getUncoveredXp());
		assertEquals(185, route.getFinalXp());
		assertEquals(10_000 - 18, route.getSimulatedBank().get(10));
	}

	/**
	 * Task 59 C: a method the route comes back to after a better one runs dry (here one action of
	 * "Rich" at level 2, then "Cheap" again) is merged into its FIRST step, not listed twice -
	 * count, xp and materials summed, fromLevel the first's, toLevel the last's.
	 */
	@Test
	void sameMethodStepsSeparatedByAnotherMethodMergeIntoTheFirstOccurrence()
	{
		KnowledgeBase kb = new KbBuilder()
			.method(Skill.HERBLORE, "Cheap", 1, 10)
			.material(20, 1)
			.method(Skill.HERBLORE, "Rich", 2, 100)
			.material(21, 1)
			.build();
		Map<Integer, Integer> bank = new HashMap<>();
		bank.put(20, 1000);
		bank.put(21, 1);

		Route route = RoutePlanner.route(Skill.HERBLORE, 0, Experience.getXpForLevel(4), bank, kb);

		assertEquals(2, route.getSteps().size(), "steps: " + route.getSteps());
		RouteStep cheap = route.getSteps().get(0);
		assertEquals("Cheap", cheap.getMethod().getName());
		assertEquals(18, cheap.getCount(), "9 actions to level 2, then 9 more from level 3");
		assertEquals(1, cheap.getFromLevel());
		assertEquals(4, cheap.getToLevel());
		assertEquals(180, cheap.getXpGained());
		assertEquals(18, (int) cheap.getMaterialsUsed().get(20));
		RouteStep rich = route.getSteps().get(1);
		assertEquals("Rich", rich.getMethod().getName());
		assertEquals(1, rich.getCount());
		assertEquals(280, route.getFinalXp());
	}

	@Test
	void whenXpPerActionIsTiedTheLowerLevelReqMethodWins()
	{
		KnowledgeBase kb = new KbBuilder()
			.method(Skill.HERBLORE, "Zzz", 1, 10)
			.material(20, 1)
			.method(Skill.HERBLORE, "Aaa", 2, 10)
			.material(21, 1)
			.build();
		Map<Integer, Integer> bank = new HashMap<>();
		bank.put(20, 1000);
		bank.put(21, 1000);

		Route route = RoutePlanner.route(Skill.HERBLORE, Experience.getXpForLevel(2), Experience.getXpForLevel(2) + 20, bank, kb);

		assertEquals(1, route.getSteps().size());
		assertEquals("Zzz", route.getSteps().get(0).getMethod().getName());
	}

	@Test
	void whenXpPerActionAndLevelReqAreBothTiedNameAscendingWins()
	{
		KnowledgeBase kb = new KbBuilder()
			.method(Skill.HERBLORE, "Bbb", 1, 10)
			.material(20, 1)
			.method(Skill.HERBLORE, "Aaa", 1, 10)
			.material(21, 1)
			.build();
		Map<Integer, Integer> bank = new HashMap<>();
		bank.put(20, 1000);
		bank.put(21, 1000);

		Route route = RoutePlanner.route(Skill.HERBLORE, 0, 20, bank, kb);

		assertEquals(1, route.getSteps().size());
		assertEquals("Aaa", route.getSteps().get(0).getMethod().getName());
	}

	@Test
	void intermediateZeroXpMethodIsCraftedOneLevelToAffordARealMethodAndTheCraftIsRecorded()
	{
		KnowledgeBase kb = new KbBuilder()
			.method(Skill.HERBLORE, "Make unf", 1, 0)
			.material(1, 1)
			.material(2, 1)
			.output(3, 1)
			.intermediate()
			.method(Skill.HERBLORE, "Make potion", 1, 50)
			.material(3, 1)
			.output(4, 1)
			.build();
		Map<Integer, Integer> bank = new HashMap<>();
		bank.put(1, 100);
		bank.put(2, 100);

		Route route = RoutePlanner.route(Skill.HERBLORE, 0, 150, bank, kb);

		assertEquals(1, route.getSteps().size());
		RouteStep step = route.getSteps().get(0);
		assertEquals("Make potion", step.getMethod().getName());
		assertEquals(3, step.getCount());
		assertEquals(3, (int) step.getMaterialsUsed().get(3));

		assertEquals(2, step.getCrafts().size());
		int totalCrafted = step.getCrafts().stream().mapToInt(RouteStep::getCount).sum();
		assertEquals(3, totalCrafted);
		for (RouteStep craft : step.getCrafts())
		{
			assertEquals("Make unf", craft.getMethod().getName());
			assertTrue(craft.getMethod().isIntermediate());
		}

		assertEquals(97, route.getSimulatedBank().get(1));
		assertEquals(97, route.getSimulatedBank().get(2));
		assertEquals(0, route.getSimulatedBank().getOrDefault(3, 0));
		assertEquals(3, route.getSimulatedBank().get(4));
		assertEquals(0, route.getUncoveredXp());
	}

	@Test
	void noAffordableMethodProducesAnEmptyRouteWithTheFullXpDeltaUncovered()
	{
		KnowledgeBase kb = new KbBuilder()
			.method(Skill.HERBLORE, "Prayer potion(3)", 1, 87.5)
			.material(10, 1)
			.build();
		Map<Integer, Integer> bank = new HashMap<>();

		Route route = RoutePlanner.route(Skill.HERBLORE, 0, 1000, bank, kb);

		assertTrue(route.getSteps().isEmpty());
		assertEquals(1000, route.getUncoveredXp());
		assertEquals(0, route.getFinalXp());
	}

	@Test
	void barbarianMixTypedMethodsAreExcludedEvenWhenTheyHaveTheHighestXp()
	{
		KnowledgeBase kb = new KbBuilder()
			.method(Skill.HERBLORE, "Barbarian mix", 1, 1000)
			.material(10, 1)
			.type("Barbarian Mix")
			.method(Skill.HERBLORE, "Normal potion", 1, 10)
			.material(11, 1)
			.build();
		Map<Integer, Integer> bank = new HashMap<>();
		bank.put(10, 1000);
		bank.put(11, 1000);

		Route route = RoutePlanner.route(Skill.HERBLORE, 0, 20, bank, kb);

		assertEquals(1, route.getSteps().size());
		assertEquals("Normal potion", route.getSteps().get(0).getMethod().getName());
	}

	@Test
	void affordabilityAccountsForTwoMaterialsWhoseIntermediatesShareARawIngredientAndNeverGoesNegative()
	{
		// M needs X and Y, one each per action; both are crafted purely from the same raw item R.
		// R=5 covers at most 2 actions of M (2R for X + 2R for Y = 4R, 1 left over) - never 5, which
		// is what independently checking X's and Y's craftability against the same undecremented
		// bank would wrongly report.
		KnowledgeBase kb = new KbBuilder()
			.method(Skill.HERBLORE, "M", 1, 10)
			.material(10, 1)
			.material(11, 1)
			.method(Skill.HERBLORE, "MakeX", 1, 0)
			.material(1, 1)
			.output(10, 1)
			.intermediate()
			.method(Skill.HERBLORE, "MakeY", 1, 0)
			.material(1, 1)
			.output(11, 1)
			.intermediate()
			.build();
		Map<Integer, Integer> bank = new HashMap<>();
		bank.put(1, 5);

		Route route = RoutePlanner.route(Skill.HERBLORE, 0, 1000, bank, kb);

		assertEquals(1, route.getSteps().size());
		RouteStep step = route.getSteps().get(0);
		assertEquals("M", step.getMethod().getName());
		assertEquals(2, step.getCount());
		for (Map.Entry<Integer, Integer> entry : route.getSimulatedBank().entrySet())
		{
			assertTrue(entry.getValue() >= 0, "simulated bank must never go negative: " + route.getSimulatedBank());
		}
		assertEquals(1, route.getSimulatedBank().get(1));
		assertEquals(0, route.getSimulatedBank().getOrDefault(10, 0));
		assertEquals(0, route.getSimulatedBank().getOrDefault(11, 0));
	}

	@Test
	void aNonZeroXpIntermediateCraftCreditsItsOwnXpToTheRoute()
	{
		KnowledgeBase kb = new KbBuilder()
			.method(Skill.HERBLORE, "Potion", 1, 1)
			.material(3, 1)
			.output(4, 1)
			.method(Skill.HERBLORE, "Make unf", 1, 2.5)
			.material(1, 1)
			.material(2, 1)
			.output(3, 1)
			.intermediate()
			.build();
		Map<Integer, Integer> bank = new HashMap<>();
		bank.put(1, 1000);
		bank.put(2, 1000);

		Route route = RoutePlanner.route(Skill.HERBLORE, 0, 10, bank, kb);

		assertEquals(1, route.getSteps().size());
		RouteStep step = route.getSteps().get(0);
		assertEquals(10, step.getCount());
		assertEquals(1, step.getCrafts().size());
		RouteStep craft = step.getCrafts().get(0);
		assertEquals(10, craft.getCount());
		assertEquals(25, craft.getXpGained());

		assertEquals(0, route.getUncoveredXp());
		assertEquals(35, route.getFinalXp(), "finalXp must include the 25 xp from the craft sub-step, not just the 10 from Potion itself");
	}

	@Test
	void sameInputsProduceAnIdenticalRoute()
	{
		KnowledgeBase kb = new KbBuilder()
			.method(Skill.HERBLORE, "Prayer potion(3)", 1, 87.5)
			.material(10, 1)
			.build();
		Map<Integer, Integer> bank = new HashMap<>();
		bank.put(10, 1000);

		Route route1 = RoutePlanner.route(Skill.HERBLORE, 0, 1000, bank, kb);
		Route route2 = RoutePlanner.route(Skill.HERBLORE, 0, 1000, bank, kb);

		assertEquals(route1, route2);
	}
}
