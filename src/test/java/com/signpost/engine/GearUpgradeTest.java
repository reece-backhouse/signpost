package com.signpost.engine;

import com.google.gson.Gson;
import com.signpost.engine.model.Advice;
import com.signpost.kb.KnowledgeBase;
import com.signpost.engine.model.GoalStatus;
import com.signpost.engine.model.ItemGap;
import com.signpost.kb.GearLadder;
import com.signpost.snapshot.AccountType;
import com.signpost.snapshot.Snapshot;
import com.signpost.store.AccountDataMutations;
import java.util.List;
import java.util.Set;
import com.signpost.store.AccountData;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class GearUpgradeTest
{
	@Test
	void advancesEachSlotFromTheBestOwnedRung()
	{
		Advice advice = new Engine(new BoostTable()).run(new SnapshotBuilder().iron()
			.bankItem(8850, "Rune defender", 1).bankItem(11832, "Bandos chestplate", 1).build(), KnowledgeBase.load(new Gson()),
			AccountData.empty(), Instant.parse("2026-09-09T12:00:00Z"));
		assertTrue(advice.getStatuses().stream().anyMatch(s -> s.getGoal().getId().equals("gear:melee:offhand:12954")));
		assertFalse(advice.getStatuses().stream().anyMatch(s -> s.getGoal().getId().equals("gear:melee:body:10551")));
	}

	@Test
	void anotherOwnedPieceDoesNotCompleteTheTargetAndItsProviderGapsStayVisible()
	{
		String target = "gear:melee:body:11832";
		Advice advice = new Engine(new BoostTable()).run(new SnapshotBuilder().iron()
			.bankItem(10551, "Fighter torso", 1).bankItem(11834, "Bandos tassets", 1).build(),
			KnowledgeBase.load(new Gson()), AccountDataMutations.focus(AccountData.empty(), target), Instant.EPOCH);
		assertTrue(advice.getFocus() != null && advice.getFocus().getStatus().getGoal().getId().equals(target));
		assertFalse(advice.getFocus().getStatus().isReady());
		assertFalse(advice.getFocus().getSkillPlans().isEmpty(), "the missing provider skills must have focus routes");
	}

	@Test
	void manualUpgradeOwnershipAlsoCompletesItsExistingMilestone()
	{
		String target = "gear:melee:offhand:12954";
		Advice advice = new Engine(new BoostTable()).run(new SnapshotBuilder().build(), KnowledgeBase.load(new Gson()),
			AccountDataMutations.markOwned(AccountData.empty(), target), Instant.EPOCH);
		assertFalse(advice.getStatuses().stream().anyMatch(s -> s.getGoal().getId().equals(target)
			|| s.getGoal().getId().equals("milestone:dragon-defender")));
		assertTrue(advice.getOwnedManuallyNames().containsKey(target));
	}

	@Test
	void onlyTheFirstPinnedUpgradeOfAStyleIsSuggested()
	{
		AccountData data = AccountDataMutations.pin(AccountData.empty(), "gear:melee:body:1127");
		data = AccountDataMutations.pin(data, "gear:melee:offhand:8850");
		Advice advice = new Engine(new BoostTable()).run(new SnapshotBuilder().build(), KnowledgeBase.load(new Gson()), data, Instant.EPOCH);
		org.junit.jupiter.api.Assertions.assertEquals("gear:melee:body:1127", advice.getPicked().get(0).getStatus().getGoal().getId());
		org.junit.jupiter.api.Assertions.assertEquals(1, advice.getPicked().stream().filter(r -> r.getStatus().getGoal().getUpgrade() != null
			&& r.getStatus().getGoal().getUpgrade().getStyle().equals("melee")).count());
	}

	@Test
	void chargedHybridJewellerySatisfiesLowerRungsAcrossStyles()
	{
		Advice advice = new Engine(new BoostTable()).run(new SnapshotBuilder().iron()
			.equipmentItem(11978, "Amulet of glory(6)", 1).bankItem(11972, "Combat bracelet(6)", 1).build(),
			KnowledgeBase.load(new Gson()), AccountData.empty(), Instant.EPOCH);
		assertFalse(advice.getStatuses().stream().anyMatch(s -> s.getGoal().getUpgrade() != null
			&& (s.getGoal().getUpgrade().getTarget().getId() == 1704
				|| s.getGoal().getUpgrade().getTarget().getId() == 1727
				|| s.getGoal().getUpgrade().getTarget().getId() == 11126)));
		assertTrue(advice.getStatuses().stream().anyMatch(s -> s.getGoal().getId().equals("gear:magic:neck:6585")));
	}

	@Test
	void assemblerCosmeticsAndManualCanonicalOwnershipCompleteTheProvider()
	{
		KnowledgeBase kb = KnowledgeBase.load(new Gson());
		Engine engine = new Engine(new BoostTable());
		Advice equipped = engine.run(new SnapshotBuilder().equipmentItem(27374, "Masori assembler", 1).build(),
			kb, AccountData.empty(), Instant.EPOCH);
		assertFalse(equipped.getStatuses().stream().anyMatch(s -> s.getGoal().getId().equals("gear:ranged:cape:22109")));
		Advice manual = engine.run(new SnapshotBuilder().build(), kb,
			AccountDataMutations.markOwned(AccountData.empty(), "gear:ranged:cape:22109"), Instant.EPOCH);
		assertFalse(manual.getStatuses().stream().anyMatch(s -> s.getGoal().getId().equals("milestone:avas-assembler")));
	}

	@Test
	void defenderFocusShowsItsProvidersMissingGuildTokens()
	{
		Advice advice = new Engine(new BoostTable()).run(new SnapshotBuilder().iron()
			.bankItem(8850, "Rune defender", 1).build(), KnowledgeBase.load(new Gson()),
			AccountDataMutations.focus(AccountData.empty(), "gear:melee:offhand:12954"), Instant.EPOCH);
		assertTrue(advice.getFocus().getStatus().getGaps().stream().filter(ItemGap.class::isInstance)
			.map(ItemGap.class::cast).anyMatch(gap -> gap.getName().equals("Warrior guild token") && gap.getNeed() == 100));
	}

	@Test
	void skipsOutclassedRewardsEvenWhenTheSuperiorItemIsOutsideTheLadder()
	{
		KnowledgeBase kb = KnowledgeBase.load(new Gson());
		List<GearLadder> ladders = List.of(new GearLadder("melee", "feet", List.of(
			new GearLadder.Rung("Dragon boots", 11840, List.of(11840), "milestone:dragon-boots", false))));
		Snapshot snapshot = new SnapshotBuilder().equipmentItem(13239, "Primordial boots", 1).build();
		List<GoalStatus> upgrades = GearUpgradeSynthesiser.run(ladders, snapshot, kb, Set.of(),
			new GapEngine(new BoostTable()), new GearComparison(snapshot, kb, Set.of()));
		assertTrue(upgrades.isEmpty(), "Primordial boots cover the ladder's best rung without occupying a rung themselves");
	}

	@Test
	void ironAccountsSkipGeOnlyRungsButStillRecogniseOwnedVariants()
	{
		KnowledgeBase kb = KnowledgeBase.load(new Gson());
		List<GearLadder> ladders = List.of(new GearLadder("melee", "offhand", List.of(
			new GearLadder.Rung("Rune defender", 8850, List.of(8850), "milestone:dragon-defender", true),
			new GearLadder.Rung("Dragon defender", 12954, List.of(12954, 27008), "milestone:dragon-defender", false))));
		Snapshot normalSnapshot = new SnapshotBuilder().build();
		List<GoalStatus> normal = GearUpgradeSynthesiser.run(ladders, normalSnapshot, kb, Set.of(),
			new GapEngine(new BoostTable()), new GearComparison(normalSnapshot, kb, Set.of()));
		org.junit.jupiter.api.Assertions.assertEquals("Rune defender", normal.get(0).getGoal().getName());
		for (AccountType type : AccountType.values())
		{
			if (!type.isIron()) continue;
			Snapshot ironSnapshot = new SnapshotBuilder().accountType(type).build();
			List<GoalStatus> iron = GearUpgradeSynthesiser.run(ladders, ironSnapshot, kb, Set.of(),
				new GapEngine(new BoostTable()), new GearComparison(ironSnapshot, kb, Set.of()));
			org.junit.jupiter.api.Assertions.assertEquals("Dragon defender", iron.get(0).getGoal().getName());
		}
		Snapshot variant = new SnapshotBuilder().equipmentItem(27008, "Dragon defender (t)", 1).build();
		assertTrue(GearUpgradeSynthesiser.run(ladders, variant, kb, Set.of(), new GapEngine(new BoostTable()),
			new GearComparison(variant, kb, Set.of())).isEmpty());
	}

	@Test
	void knownSuperiorEquipmentRemovesInferiorStandaloneAndSynthesisedTargets()
	{
		KnowledgeBase kb = KnowledgeBase.load(new Gson());
		Snapshot snapshot = new SnapshotBuilder().bankItem(11832, "Bandos chestplate", 1)
			.bankItem(13239, "Primordial boots", 1).bankItem(13235, "Eternal boots", 1).build();
		Advice advice = new Engine(new BoostTable()).run(snapshot, kb, AccountData.empty(), Instant.EPOCH);
		Set<String> obsolete = Set.of("milestone:fighter-torso", "milestone:dragon-boots", "milestone:infinity-boots",
			"gear:melee:body:10551", "gear:melee:feet:11840", "gear:magic:feet:6920");
		assertFalse(advice.getStatuses().stream().anyMatch(s -> obsolete.contains(s.getGoal().getId())));
	}

	@Test
	void aliasAbsenceAndPositiveEvidenceAreCanonicalButReplacementsAreNotOwnership()
	{
		KnowledgeBase kb = KnowledgeBase.load(new Gson());
		Snapshot absent = new SnapshotBuilder().bankUnknown().build().toBuilder().confirmedAbsentItems(Set.of(12954)).build();
		GearComparison missing = new GearComparison(absent, kb, Set.of());
		assertTrue(missing.ownershipKnown(List.of(27008)));
		assertFalse(missing.owns(List.of(27008)));
		Snapshot held = absent.toBuilder().bank(java.util.Map.of(27008, 1, 13239, 1, 6920, 0)).build();
		GearComparison positive = new GearComparison(held, kb, Set.of());
		assertTrue(positive.owns(List.of(12954)));
		assertTrue(positive.covers(List.of(11840)));
		assertFalse(positive.owns(List.of(11840)));
		assertFalse(positive.ownershipKnown(List.of(6920)), "zero bank placeholders do not assert absence");
	}

	@Test
	void corruptedBowfaDeadmanCrystalAndEquivalentCapesSupplyTheirActualLoadout()
	{
		KnowledgeBase kb = KnowledgeBase.load(new Gson());
		Snapshot snapshot = new SnapshotBuilder().bankItem(33021, "Bow of faerdhinen (c) (Deadman)", 1)
			.bankItem(33031, "Crystal helm (Deadman)", 1).bankItem(33023, "Crystal body (Deadman)", 1)
			.bankItem(33027, "Crystal legs (Deadman)", 1).equipmentItem(21791, "Imbued saradomin cape", 1)
			.build().toBuilder().bankKnown(false).build();
		GearComparison comparison = new GearComparison(snapshot, kb, Set.of());
		assertTrue(comparison.owns(List.of(25865)));
		assertTrue(comparison.owns(List.of(23971)));
		assertTrue(comparison.owns(List.of(23975)));
		assertTrue(comparison.owns(List.of(23979)));
		assertTrue(comparison.owns(List.of(21793)));
		assertFalse(comparison.owns(List.of(27238)), "Crystal ownership does not invent Masori armour");
	}

	@Test
	void specialistReplacementsPreserveHybridTargetsUntilEveryCombatRoleIsCovered()
	{
		KnowledgeBase kb = KnowledgeBase.load(new Gson());
		Snapshot melee = new SnapshotBuilder().bankItem(22981, "Ferocious gloves", 1)
			.bankItem(29801, "Amulet of rancour", 1).build();
		GearComparison specialist = new GearComparison(melee, kb, Set.of());
		assertTrue(specialist.covers(List.of(7462), "melee"));
		assertFalse(specialist.covers(List.of(7462), "ranged"));
		assertFalse(specialist.covers(List.of(7462), "magic"));
		assertFalse(specialist.covers(List.of(7462)));
		assertFalse(specialist.covers(List.of(6585)));
		assertTrue(specialist.coveringName(List.of(6585)) == null);
		List<GearLadder> hands = List.of(new GearLadder("ranged", "hands", List.of(
			new GearLadder.Rung("Barrows gloves", 7462, List.of(7462), "milestone:barrows-gloves", false))));
		assertTrue(GearUpgradeSynthesiser.run(hands, melee, kb, Set.of(), new GapEngine(new BoostTable()), specialist)
			.stream().anyMatch(g -> g.getGoal().getId().equals("gear:ranged:hands:7462")));

		Snapshot everyNeckRole = melee.toBuilder().inventory(java.util.Map.of(19547, 1, 12002, 1)).build();
		GearComparison complete = new GearComparison(everyNeckRole, kb, Set.of());
		assertTrue(complete.covers(List.of(6585)));
		assertFalse(complete.owns(List.of(6585)));
		assertTrue(complete.coveringName(List.of(6585)).contains("Amulet of rancour"));
	}
}
