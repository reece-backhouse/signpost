package com.signpost.engine;

import com.google.gson.Gson;
import com.signpost.engine.model.BringItemStatus;
import com.signpost.engine.model.PlanOffer;
import com.signpost.engine.model.Route;
import com.signpost.engine.model.Shortfall;
import com.signpost.engine.model.ShortfallItem;
import com.signpost.kb.BringItem;
import com.signpost.kb.GatheringAlternative;
import com.signpost.kb.GatheringPlan;
import com.signpost.kb.GatheringRequires;
import com.signpost.kb.GatheringRisk;
import com.signpost.kb.GatheringStep;
import com.signpost.kb.KnowledgeBase;
import com.signpost.kb.SkillReq;
import com.signpost.snapshot.AccountType;
import com.signpost.snapshot.Snapshot;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.runelite.api.Experience;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link ShortfallResolver} resolves missing materials and gathering plans. */
class ShortfallResolverTest
{
	private static final int TOADFLAX_UNF = 100;
	private static final int CRUSHED_NEST = 101;
	private static final int TOADFLAX = 102;
	private static final int VIAL_OF_WATER = 103;

	@Test
	void emptyShortfallWhenTheRouteAlreadyCoversTheFullXpDelta()
	{
		KnowledgeBase kb = new KbBuilder().build();
		Route route = new Route(List.of(), 0, 1000, Map.of());

		Shortfall shortfall = ShortfallResolver.resolve(Skill.HERBLORE, route, kb, new SnapshotBuilder().build());

		assertNull(shortfall.getMethod());
		assertTrue(shortfall.getItems().isEmpty());
	}

	@Test
	void resolvesTheToadflaxCraftingChain()
	{
		KnowledgeBase kb = new KbBuilder()
			.method(Skill.HERBLORE, "Saradomin brew(3)", 1, 180)
			.material(TOADFLAX_UNF, 1)
			.material(CRUSHED_NEST, 1)
			.material("Toadflax potion (unf)", TOADFLAX_UNF)
			.source("craft", "Toadflax potion (unf)")
			.material("Crushed nest", CRUSHED_NEST)
			.source("GE", "Grand Exchange")
			.material("Toadflax", TOADFLAX)
			.source("GE", "Grand Exchange")
			.source("drop", "Hazelmere")
			.source("craft", "Toadflax")
			.material("Vial of water", VIAL_OF_WATER)
			.source("GE", "Grand Exchange")
			.source("shop", "General Store")
			.method(Skill.HERBLORE, "Toadflax potion (unf)", 1, 0)
			.material(TOADFLAX, 1)
			.material(VIAL_OF_WATER, 1)
			.output(TOADFLAX_UNF, 1)
			.intermediate()
			.build();

		Map<Integer, Integer> simulatedBank = new HashMap<>();
		simulatedBank.put(TOADFLAX_UNF, 0);
		simulatedBank.put(CRUSHED_NEST, 1000);
		simulatedBank.put(TOADFLAX, 0);
		simulatedBank.put(VIAL_OF_WATER, 1200);
		Route route = new Route(List.of(), 540, 0, simulatedBank);

		Shortfall shortfall = ShortfallResolver.resolve(Skill.HERBLORE, route, kb, new SnapshotBuilder().build());

		assertEquals("Saradomin brew(3)", shortfall.getMethod().getName());
		assertEquals(2, shortfall.getItems().size());

		ShortfallItem unf = itemNamed(shortfall.getItems(), "Toadflax potion (unf)");
		assertEquals(0, unf.getHave());
		assertEquals(3, unf.getNeed());
		assertEquals(2, unf.getCraftFrom().size());

		ShortfallItem toadflax = itemNamed(unf.getCraftFrom(), "Toadflax");
		assertEquals(0, toadflax.getHave());
		assertEquals(3, toadflax.getNeed());
		assertTrue(toadflax.getCraftFrom().isEmpty());
		assertEquals(3, toadflax.getSources().size());

		ShortfallItem vial = itemNamed(unf.getCraftFrom(), "Vial of water");
		assertEquals(1200, vial.getHave());
		assertEquals(3, vial.getNeed());
		assertTrue(vial.getCraftFrom().isEmpty());

		ShortfallItem nest = itemNamed(shortfall.getItems(), "Crushed nest");
		assertEquals(1000, nest.getHave());
		assertEquals(3, nest.getNeed());
		assertTrue(nest.getCraftFrom().isEmpty(), "a fully-stocked material needs no craft recursion");
	}

	@Test
	void aFullyStockedMaterialHasNoShortfallAndNoCraftRecursionEvenWithACraftSource()
	{
		KnowledgeBase kb = new KbBuilder()
			.method(Skill.HERBLORE, "Only method", 1, 10)
			.material(1, 1)
			.material("Plenty", 1)
			.source("craft", "Plenty")
			.build();
		Map<Integer, Integer> simulatedBank = Map.of(1, 1000);
		Route route = new Route(List.of(), 10, 0, simulatedBank);

		Shortfall shortfall = ShortfallResolver.resolve(Skill.HERBLORE, route, kb, new SnapshotBuilder().build());

		ShortfallItem item = shortfall.getItems().get(0);
		assertEquals("https://oldschool.runescape.wiki/w/" + item.getItem().getName().replace(' ', '_'), item.getWikiUrl());
		assertEquals(1000, item.getHave());
		assertEquals(1, item.getNeed());
		assertTrue(item.getCraftFrom().isEmpty());
	}

	@Test
	void geSourcesAreDroppedForIronAccountsButKeptForNormal()
	{
		KnowledgeBase kb = new KbBuilder()
			.method(Skill.HERBLORE, "Only method", 1, 10)
			.material(1, 1)
			.material("Scarce", 1)
			.source("GE", "Grand Exchange")
			.source("drop", "Some Boss")
			.build();
		Map<Integer, Integer> simulatedBank = Map.of();
		Route route = new Route(List.of(), 10, 0, simulatedBank);

		Shortfall normal = ShortfallResolver.resolve(Skill.HERBLORE, route, kb, new SnapshotBuilder().build());
		Shortfall iron = ShortfallResolver.resolve(Skill.HERBLORE, route, kb, new SnapshotBuilder().iron().build());

		assertEquals(2, normal.getItems().get(0).getSources().size());
		assertEquals(1, iron.getItems().get(0).getSources().size());
		assertEquals("drop", iron.getItems().get(0).getSources().get(0).getType());
	}

	@Test
	void shortfallItemCarriesItsCuratedGatheringPlan()
	{
		final int SNAPE_GRASS = 200;
		KnowledgeBase kb = new KbBuilder()
			.method(Skill.HERBLORE, "Prayer potion(3)", 1, 87.5)
			.material(SNAPE_GRASS, 1)
			.material("Snape grass", SNAPE_GRASS)
			.source("GE", "Grand Exchange")
			.gatheringPlan("Snape grass", SNAPE_GRASS)
			.build();
		Route route = new Route(List.of(), 10, 0, Map.of());

		Shortfall shortfall = ShortfallResolver.resolve(Skill.HERBLORE, route, kb, new SnapshotBuilder().build());

		ShortfallItem item = shortfall.getItems().get(0);
		assertEquals(1, item.getPlans().size());
		PlanOffer offer = item.getPlans().get(0);
		assertEquals("Snape grass", offer.getPlan().getItem());
		assertTrue(offer.isMeetsRequirements());
		assertTrue(offer.getMissing().isEmpty());
	}

	@Test
	void aShortfallReachedOnlyThroughTheCraftChainStillCarriesTheIngredientsPlan()
	{
		final int DUST = 300;
		final int SCALES = 301;
		KnowledgeBase kb = new KbBuilder()
			.method(Skill.HERBLORE, "Antifire potion(3)", 1, 100)
			.material(DUST, 1)
			.method(Skill.HERBLORE, "Grind blue dragon scales", 1, 0)
			.material(SCALES, 1)
			.output(DUST, 1)
			.intermediate()
			.material("Dragon scale dust", DUST)
			.source("craft", "Grind blue dragon scales")
			.material("Blue dragon scales", SCALES)
			.source("spawn", "Taverley Dungeon")
			.gatheringPlan("Blue dragon scales", SCALES)
			.build();
		Route route = new Route(List.of(), 10, 0, Map.of());

		Shortfall shortfall = ShortfallResolver.resolve(Skill.HERBLORE, route, kb, new SnapshotBuilder().build());

		ShortfallItem dust = shortfall.getItems().get(0);
		// Dragon scale dust has no plan of its own in this fixture - it carries the scales plan only
		// via the craftFrom union.
		assertEquals(1, dust.getPlans().size());
		assertEquals("Blue dragon scales", dust.getPlans().get(0).getPlan().getItem());

		ShortfallItem scales = itemNamed(dust.getCraftFrom(), "Blue dragon scales");
		assertEquals(1, scales.getPlans().size());
		assertEquals("Blue dragon scales", scales.getPlans().get(0).getPlan().getItem());
	}

	@Test
	void aPlanNeedingAHigherSkillThanTheSnapshotIsFlaggedWithWhatsMissing()
	{
		final int SCALES = 400;
		KnowledgeBase kb = new KbBuilder()
			.method(Skill.HERBLORE, "Only method", 1, 10)
			.material(SCALES, 1)
			.material("Blue dragon scales", SCALES)
			.gatheringPlan("Blue dragon scales", SCALES)
			.requiresSkill(Skill.AGILITY, 70)
			.build();
		Route route = new Route(List.of(), 10, 0, Map.of());
		Snapshot snapshot = new SnapshotBuilder().skill(Skill.AGILITY, 60).build();

		Shortfall shortfall = ShortfallResolver.resolve(Skill.HERBLORE, route, kb, snapshot);

		PlanOffer offer = shortfall.getItems().get(0).getPlans().get(0);
		assertFalse(offer.isMeetsRequirements());
		assertEquals(List.of("Agility 70 (have 60)"), offer.getMissing());
	}

	/** A plan's offer keeps only the steps whose own skill/quest requirements the account meets, in order. */
	@Test
	void anOfferDropsTheStepsWhoseRequirementsTheAccountDoesNotMeet()
	{
		final int KWUARM = 263;
		KnowledgeBase kb = new KbBuilder()
			.method(Skill.HERBLORE, "Only method", 1, 10)
			.material(KWUARM, 1)
			.material("Kwuarm", KWUARM)
			.gatheringPlan("Kwuarm", KWUARM)
			.step("Falador patch.")
			.step("Farming Guild patch.", List.of(new SkillReq(Skill.FARMING, 65, false, false)), List.of())
			.step("Weiss patch.", List.of(), List.of(Quest.MAKING_FRIENDS_WITH_MY_ARM))
			.build();
		Route route = new Route(List.of(), 10, 0, Map.of());
		Snapshot lowAccount = new SnapshotBuilder().skill(Skill.FARMING, 56).build();
		Snapshot readyAccount = new SnapshotBuilder().skill(Skill.FARMING, 65)
			.quest(Quest.MAKING_FRIENDS_WITH_MY_ARM, QuestState.FINISHED).build();

		PlanOffer low = ShortfallResolver.resolve(Skill.HERBLORE, route, kb, lowAccount).getItems().get(0).getPlans().get(0);
		PlanOffer ready = ShortfallResolver.resolve(Skill.HERBLORE, route, kb, readyAccount).getItems().get(0).getPlans().get(0);

		assertEquals(List.of("Falador patch."), low.getSteps());
		assertTrue(low.isMeetsRequirements(), "step gating must not flag the whole plan");
		assertEquals(List.of("Falador patch.", "Farming Guild patch.", "Weiss patch."), ready.getSteps());
	}

	/** The fastest method stays primary; an iron who can't gather its materials also gets the best obtainable one. */
	@Test
	void anIronKeepsTheFastestMethodAndGetsTheObtainableOneAsAlternative()
	{
		KnowledgeBase kb = weaponPoisonVsPrayerPotionKb(false);
		Route route = new Route(List.of(), 1000, Experience.getXpForLevel(62), Map.of());

		Shortfall shortfall = ShortfallResolver.resolve(Skill.HERBLORE, route, kb, new SnapshotBuilder().iron().build());

		assertEquals("Weapon poison", shortfall.getMethod().getName());
		assertEquals("Prayer potion(3)", shortfall.getAlternative().getMethod().getName());
		assertEquals(2, shortfall.getAlternative().getItems().size());
		assertNull(shortfall.getAlternative().getAlternative());
	}

	@Test
	void aNormalAccountKeepsTheHigherXpMethodWhenItsMaterialsCanBeBought()
	{
		KnowledgeBase kb = weaponPoisonVsPrayerPotionKb(true);
		Route route = new Route(List.of(), 1000, Experience.getXpForLevel(62), Map.of());

		Shortfall shortfall = ShortfallResolver.resolve(Skill.HERBLORE, route, kb, new SnapshotBuilder().build());

		assertEquals("Weapon poison", shortfall.getMethod().getName());
		assertNull(shortfall.getAlternative(), "the primary is itself obtainable");
	}

	@Test
	void offersNoAlternativeWhenNoCandidateIsObtainable()
	{
		KnowledgeBase kb = new KbBuilder()
			.method(Skill.HERBLORE, "High", 1, 100)
			.material(1, 1)
			.method(Skill.HERBLORE, "Low", 1, 10)
			.material(2, 1)
			.material("Drop only A", 1)
			.source("drop", "Boss A")
			.material("Drop only B", 2)
			.source("drop", "Boss B")
			.build();
		Route route = new Route(List.of(), 1000, 0, Map.of());

		Shortfall shortfall = ShortfallResolver.resolve(Skill.HERBLORE, route, kb, new SnapshotBuilder().iron().build());

		assertEquals("High", shortfall.getMethod().getName());
		assertNull(shortfall.getAlternative());
	}

	@Test
	void carriesTheXpShortAndActionsNeededForTheChosenMethod()
	{
		KnowledgeBase kb = weaponPoisonVsPrayerPotionKb(false);
		Route route = new Route(List.of(), 1000, Experience.getXpForLevel(62), Map.of());

		Shortfall shortfall = ShortfallResolver.resolve(Skill.HERBLORE, route, kb, new SnapshotBuilder().iron().build());

		assertEquals(1000, shortfall.getXpShort());
		assertEquals(8, shortfall.getActionsNeeded(), "ceil(1000 / 137.5)");
		assertEquals(8, itemNamed(shortfall.getItems(), "Kwuarm potion (unf)").getNeed());
		Shortfall alternative = shortfall.getAlternative();
		assertEquals(1000, alternative.getXpShort());
		assertEquals(12, alternative.getActionsNeeded(), "ceil(1000 / 87.5)");
		assertEquals(12, itemNamed(alternative.getItems(), "Snape grass").getNeed());

		Shortfall covered = ShortfallResolver.resolve(Skill.HERBLORE, new Route(List.of(), 0, 1000, Map.of()), kb,
			new SnapshotBuilder().build());
		assertNull(covered.getMethod());
		assertTrue(covered.getItems().isEmpty());
		assertEquals(0, covered.getXpShort());
		assertEquals(0, covered.getActionsNeeded());
		assertNull(covered.getAlternative());
	}

	/** The primary names the materials with no known source - the craft-chain leaf, not the intermediate it makes. */
	@Test
	void namesTheUnobtainableLeafMaterialsOfThePrimary()
	{
		KnowledgeBase kb = weaponPoisonVsPrayerPotionKb(false);
		Route route = new Route(List.of(), 1000, Experience.getXpForLevel(62), Map.of());

		Shortfall shortfall = ShortfallResolver.resolve(Skill.HERBLORE, route, kb, new SnapshotBuilder().iron().build());

		assertEquals(List.of("Kwuarm"), shortfall.getUnobtainable(), "Kwuarm potion (unf) crafts from Kwuarm (drop only) + Vial of water (plan)");
		assertTrue(shortfall.getAlternative().getUnobtainable().isEmpty(), "the alternative is fully obtainable by construction");

		Shortfall bought = ShortfallResolver.resolve(Skill.HERBLORE, route, weaponPoisonVsPrayerPotionKb(true), new SnapshotBuilder().build());
		assertTrue(bought.getUnobtainable().isEmpty(), "a normal account buys Kwuarm from the shop");
	}

	/** A leaf reached twice (here, the same material listed twice) is named once, in first-seen order. */
	@Test
	void unobtainableNamesAreDeduplicated()
	{
		final int KWUARM = 263;
		KnowledgeBase kb = new KbBuilder()
			.method(Skill.HERBLORE, "Double kwuarm", 1, 10)
			.material(KWUARM, 1)
			.material(KWUARM, 1)
			.material("Kwuarm", KWUARM)
			.source("drop", "Chaos druids")
			.build();

		Shortfall shortfall = ShortfallResolver.resolve(Skill.HERBLORE, new Route(List.of(), 10, 0, Map.of()), kb, new SnapshotBuilder().iron().build());

		assertEquals(List.of("Kwuarm"), shortfall.getUnobtainable());
	}

	/** On the bundled KB with the Kwuarm farming loop, every Weapon poison material is plannable. */
	@Test
	void anIronAtHerbloreSixtyTwoOnTheRealKbKeepsWeaponPoisonWithNoAlternative()
	{
		KnowledgeBase kb = KnowledgeBase.load(new Gson());
		long fromXp = Experience.getXpForLevel(62);
		long toXp = Experience.getXpForLevel(70);
		Route route = new Route(List.of(), toXp - fromXp, fromXp, Map.of());

		Shortfall shortfall = ShortfallResolver.resolve(Skill.HERBLORE, route, kb, new SnapshotBuilder().iron().build());

		assertEquals("Weapon poison", shortfall.getMethod().getName());
		// Kwuarm potion (unf) crafts from Kwuarm (farm loop plan) + Vial of water (plan); Dragon scale dust has a plan.
		assertNull(shortfall.getAlternative(), "the primary is itself obtainable");
		assertEquals(toXp - fromXp, shortfall.getXpShort());
		assertTrue(shortfall.getActionsNeeded() > 0);
	}

	/** Real-data alternative path: at 55 the fastest method is Goading potion (Aldarium, no plan); Super strength is fully plannable. */
	@Test
	void anIronAtHerbloreFiftyFiveOnTheRealKbIsOfferedSuperStrengthAsTheAlternative()
	{
		KnowledgeBase kb = KnowledgeBase.load(new Gson());
		long fromXp = Experience.getXpForLevel(55);
		long toXp = Experience.getXpForLevel(60);
		Route route = new Route(List.of(), toXp - fromXp, fromXp, Map.of());

		Shortfall shortfall = ShortfallResolver.resolve(Skill.HERBLORE, route, kb,
			new SnapshotBuilder().iron().skill(Skill.HERBLORE, 55).skill(Skill.FARMING, 56)
				.quest(Quest.PLAGUE_CITY, QuestState.FINISHED).build());

		assertEquals("Goading potion(3)", shortfall.getMethod().getName());
		Shortfall alternative = shortfall.getAlternative();
		assertEquals("Super strength(3)", alternative.getMethod().getName());
		assertFalse(alternative.getItems().isEmpty());
		for (ShortfallItem item : alternative.getItems())
		{
			assertFalse(item.getPlans().isEmpty(), item.getItem().getName() + " has no gathering plan");
		}
		assertEquals(toXp - fromXp, alternative.getXpShort());
		assertTrue(alternative.getActionsNeeded() > 0);
		assertNull(alternative.getAlternative());
	}

	@Test
	void aBankThatCoversThePrimarysNeedMakesItObtainableWithoutAnyPlan()
	{
		KnowledgeBase kb = weaponPoisonVsPrayerPotionKb(false);
		long finalXp = Experience.getXpForLevel(62);
		Snapshot iron = new SnapshotBuilder().iron().build();

		// ceil(1000 / 137.5) = 8 Kwuarm potion (unf): exactly enough covers Weapon poison, one short does not.
		Shortfall covered = ShortfallResolver.resolve(Skill.HERBLORE, new Route(List.of(), 1000, finalXp, Map.of(KWUARM_UNF, 8)), kb, iron);
		Shortfall oneShort = ShortfallResolver.resolve(Skill.HERBLORE, new Route(List.of(), 1000, finalXp, Map.of(KWUARM_UNF, 7)), kb, iron);

		assertEquals("Weapon poison", covered.getMethod().getName());
		assertNull(covered.getAlternative());
		assertEquals("Prayer potion(3)", oneShort.getAlternative().getMethod().getName());
	}

	@Test
	void aCraftChainIsNotFollowedPastOneLevel()
	{
		final int TOP = 600;
		final int MID = 601;
		final int RAW = 602;
		final int PLANNED = 603;
		KnowledgeBase kb = new KbBuilder()
			.method(Skill.HERBLORE, "Fast", 1, 100)
			.material(TOP, 1)
			.method(Skill.HERBLORE, "Slow", 1, 10)
			.material(PLANNED, 1)
			.method(Skill.HERBLORE, "Make top", 1, 0)
			.material(MID, 1)
			.output(TOP, 1)
			.intermediate()
			.method(Skill.HERBLORE, "Make mid", 1, 0)
			.material(RAW, 1)
			.output(MID, 1)
			.intermediate()
			.material("Top", TOP)
			.source("craft", "Make top")
			.material("Mid", MID)
			.source("craft", "Make mid")
			.material("Raw", RAW)
			.material("Planned", PLANNED)
			.gatheringPlan("Raw", RAW)
			.gatheringPlan("Planned", PLANNED)
			.build();
		Route route = new Route(List.of(), 1000, 0, Map.of());

		Shortfall shortfall = ShortfallResolver.resolve(Skill.HERBLORE, route, kb, new SnapshotBuilder().iron().build());

		assertEquals("Fast", shortfall.getMethod().getName());
		// Top -> Mid is followed; Mid -> Raw (whose plan would rescue it) is not.
		assertEquals("Slow", shortfall.getAlternative().getMethod().getName());
	}

	@Test
	void wildernessOptInAndHardcoreProtectionFilterPrimaryAndAlternativePlansIndependently()
	{
		GatheringPlan safe = riskPlan("Safe", GatheringRisk.NONE, List.of(
			riskAlternative("Wild alternative", GatheringRisk.WILDERNESS),
			riskAlternative("Unsafe alternative", GatheringRisk.HARDCORE_UNSAFE)));
		KnowledgeBase kb = riskKb(List.of(safe,
			riskPlan("Wild", GatheringRisk.WILDERNESS, List.of()),
			riskPlan("Unsafe", GatheringRisk.HARDCORE_UNSAFE, List.of())));
		for (AccountType account : AccountType.values())
		{
			for (boolean includeWilderness : List.of(false, true))
			{
				Snapshot snapshot = new SnapshotBuilder().accountType(account).build();
				Shortfall shortfall = ShortfallResolver.resolve(Skill.HERBLORE,
					new Route(List.of(), 10, 0, Map.of()), kb, snapshot, includeWilderness);
				boolean hardcore = account == AccountType.HARDCORE || account == AccountType.HARDCORE_GROUP;
				List<String> expected = new java.util.ArrayList<>(List.of("Safe"));
				List<String> alternativeTitles = new java.util.ArrayList<>();
				List<String> notes = new java.util.ArrayList<>();
				if (includeWilderness)
				{
					expected.add("Wild");
					alternativeTitles.add("Wild alternative");
				}
				else
				{
					notes.add("2 wilderness plans hidden (enable in settings)");
				}
				if (!hardcore)
				{
					expected.add("Unsafe");
					alternativeTitles.add("Unsafe alternative");
				}
				else
				{
					notes.add("2 hardcore-unsafe plans hidden (hardcore account)");
				}
				List<PlanOffer> offers = shortfall.getItems().get(0).getPlans();
				String context = account + " includeWilderness=" + includeWilderness;
				assertEquals(expected, offers.stream().map(o -> o.getPlan().getTitle()).collect(Collectors.toList()), context);
				assertEquals(alternativeTitles, offers.get(0).getPlan().getAlternatives().stream()
					.map(GatheringAlternative::getTitle).collect(Collectors.toList()), context);
				assertEquals(notes, shortfall.getNotes(), context);
			}
		}
	}

	@Test
	void aHiddenRiskyPrimaryPromotesItsSafeAlternativeWithoutLeakingOtherRiskyRoutes()
	{
		for (GatheringRisk risk : List.of(GatheringRisk.WILDERNESS, GatheringRisk.HARDCORE_UNSAFE))
		{
			KnowledgeBase kb = riskKb(List.of(riskPlan("Risky", risk, List.of(
				riskAlternative("Safe alternative", GatheringRisk.NONE),
				riskAlternative("Hidden alternative", risk)))));
			Shortfall shortfall = ShortfallResolver.resolve(Skill.HERBLORE,
				new Route(List.of(), 10, 0, Map.of()), kb,
				new SnapshotBuilder().accountType(AccountType.HARDCORE_GROUP).build());
			List<PlanOffer> offers = shortfall.getItems().get(0).getPlans();
			assertEquals(1, offers.size());
			assertEquals("Safe alternative", offers.get(0).getPlan().getTitle());
			assertEquals(GatheringRisk.NONE, offers.get(0).getPlan().getRisk());
			assertEquals(List.of("Safe alternative step"), offers.get(0).getSteps());
			assertTrue(offers.get(0).getPlan().getAlternatives().isEmpty());
			assertTrue(shortfall.getUnobtainable().isEmpty(), "the promoted route still makes the material obtainable");
			assertEquals(List.of(risk == GatheringRisk.WILDERNESS
				? "2 wilderness plans hidden (enable in settings)"
				: "2 hardcore-unsafe plans hidden (hardcore account)"), shortfall.getNotes());
		}
	}

	@Test
	void hidingTheOnlyGatheringRouteMakesTheMaterialUnobtainableUntilOptedIn()
	{
		KnowledgeBase kb = riskKb(List.of(riskPlan("Wild", GatheringRisk.WILDERNESS, List.of())));
		Route route = new Route(List.of(), 10, 0, Map.of());
		Snapshot iron = new SnapshotBuilder().iron().build();
		Shortfall hidden = ShortfallResolver.resolve(Skill.HERBLORE, route, kb, iron);
		assertTrue(hidden.getItems().get(0).getPlans().isEmpty());
		assertEquals(List.of("Snape grass"), hidden.getUnobtainable());
		assertEquals(List.of("1 wilderness plan hidden (enable in settings)"), hidden.getNotes());

		Shortfall allowed = ShortfallResolver.resolve(Skill.HERBLORE, route, kb, iron, true);
		assertEquals("Wild", allowed.getItems().get(0).getPlans().get(0).getPlan().getTitle());
		assertTrue(allowed.getUnobtainable().isEmpty());
		assertTrue(allowed.getNotes().isEmpty());
		Shortfall covered = ShortfallResolver.resolve(Skill.HERBLORE,
			new Route(List.of(), 10, 0, Map.of(SNAPE_GRASS, 1)), kb, iron);
		assertTrue(covered.getNotes().isEmpty(), "no hidden-plan warning for a covered material");
	}

	@Test
	void hiddenCountsFollowIdLookupRatherThanUnrelatedPlansWithTheSameName()
	{
		GatheringPlan unrelated = new GatheringPlan("Snape grass", 999, "Unrelated", NO_GATHERING_REQUIRES,
			null, List.of(new GatheringStep("Unrelated step", NO_GATHERING_REQUIRES)), List.of(), null,
			GatheringRisk.WILDERNESS);
		KnowledgeBase kb = riskKb(List.of(riskPlan("Safe", GatheringRisk.NONE, List.of()), unrelated));
		Shortfall shortfall = ShortfallResolver.resolve(Skill.HERBLORE,
			new Route(List.of(), 10, 0, Map.of()), kb, new SnapshotBuilder().iron().build());
		assertEquals("Safe", shortfall.getItems().get(0).getPlans().get(0).getPlan().getTitle());
		assertTrue(shortfall.getNotes().isEmpty(), "the id-selected plan excludes unrelated name matches");
	}

	@Test
	void gatheringBringTicksUseAllOwnershipSourcesAndRespectRequiredQuantities()
	{
		List<BringItem> items = List.of(new BringItem("Knife", 946), new BringItem("Spade", 952),
			new BringItem("Staff of air", 1381), new BringItem("Hammer", 2347), new BringItem("Law rune", 563, 4),
			new BringItem("Chisel", 1755));
		GatheringRequires requires = new GatheringRequires(List.of(), null, List.of(), items, null);
		GatheringPlan plan = new GatheringPlan("Snape grass", SNAPE_GRASS, "Gather", requires, null,
			List.of(new GatheringStep("Gather step", NO_GATHERING_REQUIRES)),
			List.of(new GatheringAlternative("Alternative", List.of(new GatheringStep("Alternative step", NO_GATHERING_REQUIRES)),
				requires)), null);
		KnowledgeBase kb = riskKb(List.of(plan));
		SnapshotBuilder snapshot = new SnapshotBuilder().accountType(AccountType.GROUP)
			.bankItem(946, "Knife", 1).inventoryItem(952, "Spade", 1).equipmentItem(1381, "Staff of air", 1)
			.groupStorageItem(2347, "Hammer", 1).bankItem(563, "Law rune", 1).inventoryItem(563, "Law rune", 1)
			.groupStorageItem(563, "Law rune", 1);
		Route route = new Route(List.of(), 10, 0, Map.of());
		PlanOffer offer = ShortfallResolver.resolve(Skill.HERBLORE, route, kb, snapshot.build()).getItems().get(0).getPlans().get(0);
		List<Boolean> expected = List.of(true, true, true, true, false, false);
		assertEquals(expected, offer.getItems().stream().map(BringItemStatus::isOwned).collect(Collectors.toList()));
		assertEquals(expected, offer.getAlternativeItems().get(0).stream().map(BringItemStatus::isOwned).collect(Collectors.toList()));

		PlanOffer covered = ShortfallResolver.resolve(Skill.HERBLORE, route, kb,
			snapshot.groupStorageItem(563, "Law rune", 1).build()).getItems().get(0).getPlans().get(0);
		assertTrue(covered.getItems().get(4).isOwned(), "four pooled runes exactly cover the requirement");
		assertTrue(covered.getAlternativeItems().get(0).get(4).isOwned());
	}

	private static final GatheringRequires NO_GATHERING_REQUIRES =
		new GatheringRequires(List.of(), null, List.of(), List.of(), null);

	private static GatheringAlternative riskAlternative(String title, GatheringRisk risk)
	{
		return new GatheringAlternative(title, List.of(new GatheringStep(title + " step", NO_GATHERING_REQUIRES)),
			NO_GATHERING_REQUIRES, risk);
	}

	private static GatheringPlan riskPlan(String title, GatheringRisk risk, List<GatheringAlternative> alternatives)
	{
		return new GatheringPlan("Snape grass", SNAPE_GRASS, title, NO_GATHERING_REQUIRES, null,
			List.of(new GatheringStep(title + " step", NO_GATHERING_REQUIRES)), alternatives, null, risk);
	}

	private static KnowledgeBase riskKb(List<GatheringPlan> plans)
	{
		KnowledgeBase base = new KbBuilder().method(Skill.HERBLORE, "Only method", 1, 10)
			.material(SNAPE_GRASS, 1).material("Snape grass", SNAPE_GRASS).build();
		return KnowledgeBase.of("test", "test", List.of(), List.of(), List.of(), Map.of(),
			base.getMethods(), base.getMaterials(), plans);
	}

	private static final int KWUARM_UNF = 500;
	private static final int KWUARM = 501;
	private static final int RANARR_UNF = 502;
	private static final int RANARR = 503;
	private static final int SNAPE_GRASS = 504;

	/** Weapon poison (137.5 xp, kwuarm unf crafted only from a drop-only kwuarm, no GE anywhere so {@code kwuarmInShop} is the sole
	 * non-iron route) vs Prayer potion (87.5 xp, ranarr + snape grass, both with plans). */
	private static KnowledgeBase weaponPoisonVsPrayerPotionKb(boolean kwuarmInShop)
	{
		KbBuilder kb = new KbBuilder()
			.method(Skill.HERBLORE, "Weapon poison", 60, 137.5)
			.material(KWUARM_UNF, 1)
			.method(Skill.HERBLORE, "Kwuarm potion (unf)", 55, 0)
			.material(KWUARM, 1)
			.material(VIAL_OF_WATER, 1)
			.output(KWUARM_UNF, 1)
			.intermediate()
			.method(Skill.HERBLORE, "Prayer potion(3)", 38, 87.5)
			.material(RANARR_UNF, 1)
			.material(SNAPE_GRASS, 1)
			.method(Skill.HERBLORE, "Ranarr potion (unf)", 30, 0)
			.material(RANARR, 1)
			.material(VIAL_OF_WATER, 1)
			.output(RANARR_UNF, 1)
			.intermediate()
			.material("Kwuarm potion (unf)", KWUARM_UNF)
			.source("craft", "Kwuarm potion (unf)")
			.material("Kwuarm", KWUARM)
			.source("drop", "Sorceress's Garden");
		if (kwuarmInShop)
		{
			kb.source("shop", "Herblore shop");
		}
		return kb
			.material("Ranarr potion (unf)", RANARR_UNF)
			.source("GE", "Grand Exchange")
			.source("craft", "Ranarr potion (unf)")
			.material("Ranarr weed", RANARR)
			.source("GE", "Grand Exchange")
			.material("Snape grass", SNAPE_GRASS)
			.source("spawn", "Waterbirth Island")
			.material("Vial of water", VIAL_OF_WATER)
			.source("shop", "Herblore shop")
			.gatheringPlan("Ranarr weed", RANARR)
			.gatheringPlan("Snape grass", SNAPE_GRASS)
			.gatheringPlan("Vial of water", VIAL_OF_WATER)
			.build();
	}

	private static ShortfallItem itemNamed(List<ShortfallItem> items, String name)
	{
		return items.stream().filter(i -> i.getItem().getName().equals(name)).findFirst()
			.orElseThrow(() -> new AssertionError("no shortfall item named " + name));
	}
}
