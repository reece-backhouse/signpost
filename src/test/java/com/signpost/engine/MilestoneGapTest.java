package com.signpost.engine;

import com.google.gson.Gson;
import com.signpost.engine.model.DiaryTierGap;
import com.signpost.engine.model.Gap;
import com.signpost.engine.model.GoalStatus;
import com.signpost.engine.model.ItemGap;
import com.signpost.engine.model.PrerequisiteGap;
import com.signpost.engine.model.RankedGoal;
import com.signpost.engine.model.SkillLevelGap;
import com.signpost.kb.KnowledgeBase;
import com.signpost.kb.MilestoneCategory;
import com.signpost.snapshot.AccountType;
import com.signpost.snapshot.DiaryTier;
import com.signpost.snapshot.Snapshot;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.runelite.api.Skill;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * milestone goals (gear/unlock/prayer/spellbook/slayer_target/boss) produced by
 * {@link GapEngine} from {@code milestones.json} entries.
 */
class MilestoneGapTest
{
	private final GapEngine engine = new GapEngine(new BoostTable());

	@Test
	void gearMilestoneDoneWhenEitherOwnedIfIdIsHeld()
	{
		KnowledgeBase kb = new KbBuilder()
			.milestone("milestone:test-gear", MilestoneCategory.GEAR, "Test Gear", 5)
			.ownedIf("Item A", 100)
			.ownedIf("Item B", 200)
			.build();
		Snapshot snapshot = new SnapshotBuilder().inventoryItem(200, "Item B", 1).build();

		List<GoalStatus> statuses = engine.evaluate(snapshot, kb);

		assertFalse(hasGoal(statuses, "milestone:test-gear"), "owned gear milestone should not be emitted: " + statuses);
	}

	@Test
	void gearMilestoneDoneWhenOwnedViaAWikiVariantIdNotThePrimaryId()
	{
		// Real bug: Dragon defender's ownedIf carried only id 12954, so a player holding the
		// trimmed variant (27008) was told to go get one they already owned.
		KnowledgeBase kb = new KbBuilder()
			.milestone("milestone:dragon-defender", MilestoneCategory.GEAR, "Dragon defender", 5)
			.ownedIf("Dragon defender", 12954, 19722, 20463, 24143, 27008)
			.build();
		Snapshot snapshot = new SnapshotBuilder().inventoryItem(27008, "Dragon defender (t)", 1).build();

		List<GoalStatus> statuses = engine.evaluate(snapshot, kb);

		assertFalse(hasGoal(statuses, "milestone:dragon-defender"),
			"owning a variant id should count as owning the gear milestone: " + statuses);
	}

	@Test
	void unknownTargetOwnershipDoesNotMakeKnownAcquisitionRequirementsUnknown()
	{
		KnowledgeBase kb = new KbBuilder()
			.milestone("milestone:test-gear", MilestoneCategory.GEAR, "Test Gear", 5)
			.ownedIf("Item A", 100)
			.build();
		Snapshot snapshot = new SnapshotBuilder().bankUnknown().build();

		GoalStatus status = goalFor(engine.evaluate(snapshot, kb), "milestone:test-gear");

		assertTrue(status.isReady());
		assertFalse(status.isBankUnknown(), "target ownership is separate from requirement readiness");
	}

	/** Gear held only in the group ironman shared storage counts as owned. */
	@Test
	void gearMilestoneDoneWhenOwnedIfIdIsHeldOnlyInGroupStorage()
	{
		KnowledgeBase kb = new KbBuilder()
			.milestone("milestone:test-gear", MilestoneCategory.GEAR, "Test Gear", 5)
			.ownedIf("Item A", 100)
			.build();
		Snapshot snapshot = new SnapshotBuilder().accountType(AccountType.GROUP).groupStorageItem(100, "Item A", 1).build();

		assertFalse(hasGoal(engine.evaluate(snapshot, kb), "milestone:test-gear"), "gear in group storage is owned");
	}

	/** "Ready now" flips from false to true when recommended gear moves from nowhere into group storage. */
	@Test
	void bossReadyFlipsTrueWhenRecommendedGearAppearsInGroupStorage()
	{
		KnowledgeBase kb = new KbBuilder()
			.milestone("milestone:test-boss", MilestoneCategory.BOSS, "Test Boss", 5)
			.recommendedGearGroup("tool", new com.signpost.kb.OwnedItem("Item A", 100))
			.build();
		Snapshot without = new SnapshotBuilder().accountType(AccountType.GROUP).groupStorageKnown().build();
		Snapshot with = new SnapshotBuilder().accountType(AccountType.GROUP).groupStorageItem(100, "Item A", 1).build();

		GoalStatus before = goalFor(engine.evaluate(without, kb), "milestone:test-boss");
		GoalStatus after = goalFor(engine.evaluate(with, kb), "milestone:test-boss");

		assertFalse(before.isReady(), "nothing owned: " + before);
		assertFalse(before.isBankUnknown(), "unseen group storage is treated as empty, never as unknown");
		assertTrue(after.isReady(), "gear in group storage satisfies its required role: " + after);
	}

	/** A manual "Own it" override finishes a gear milestone even though nothing owned-if is held. */
	@Test
	void gearMilestoneDoneWhenManuallyMarkedOwnedEvenWithNoOwnedIfItemHeld()
	{
		KnowledgeBase kb = new KbBuilder()
			.milestone("milestone:test-gear", MilestoneCategory.GEAR, "Test Gear", 5)
			.ownedIf("Item A", 100)
			.build();
		Snapshot snapshot = new SnapshotBuilder().build();

		List<GoalStatus> statuses = engine.evaluate(snapshot, kb, DiaryProgress.compute(snapshot, kb), Set.of("milestone:test-gear"));

		assertFalse(hasGoal(statuses, "milestone:test-gear"), "manually-owned gear milestone should not be emitted: " + statuses);
	}

	/** An outfit milestone with {@code ownedIfMin} = set size is owned only once every piece is held. */
	@Test
	void outfitMilestoneWithOwnedIfMinIsDoneOnlyWhenEnoughDistinctPiecesAreHeld()
	{
		KnowledgeBase kb = new KbBuilder()
			.milestone("milestone:prospector-outfit", MilestoneCategory.GEAR, "Prospector outfit", 6)
			.ownedIf("Prospector helmet", 12013, 25549)
			.ownedIf("Prospector jacket", 12014)
			.ownedIf("Prospector legs", 12015)
			.ownedIf("Prospector boots", 12016)
			.ownedIfMin(4)
			.build();
		Snapshot twoPieces = new SnapshotBuilder().bankItem(25549, "Golden prospector helmet", 5)
			.inventoryItem(12013, "Prospector helmet", 1).bankItem(12014, "Prospector jacket", 1).build();
		Snapshot fullSet = new SnapshotBuilder().accountType(AccountType.GROUP)
			.bankItem(12013, "Prospector helmet", 1).inventoryItem(12014, "Prospector jacket", 1)
			.groupStorageItem(12015, "Prospector legs", 1).equipmentItem(12016, "Prospector boots", 1).build();

		assertTrue(hasGoal(engine.evaluate(twoPieces, kb), "milestone:prospector-outfit"), "two of four pieces is not owned");
		assertFalse(hasGoal(engine.evaluate(fullSet, kb), "milestone:prospector-outfit"), "all four pieces held should finish the outfit");
	}

	/**
	 * a POH room is never auto-detected (still a goal at 99 Construction); a goal whose
	 * prerequisite isn't owned carries a {@link PrerequisiteGap} and isn't ready; "Own it" on the
	 * prerequisite removes it and makes the dependant ready; milestone notes reach the status.
	 */
	@Test
	void pohMilestoneWithUnownedPrerequisiteHasAPrerequisiteGapUntilThePrerequisiteIsMarkedOwned()
	{
		KnowledgeBase kb = new KbBuilder()
			.milestone("poh:portal-chamber", MilestoneCategory.POH, "Portal chamber", 6).skill(Skill.CONSTRUCTION, 50)
			.milestone("poh:portal-nexus", MilestoneCategory.POH, "Portal nexus", 6).skill(Skill.CONSTRUCTION, 72)
			.prerequisite("poh:portal-chamber").milestoneNote("Mark the room owned once built.")
			.build();
		Snapshot snapshot = new SnapshotBuilder().skill(Skill.CONSTRUCTION, 99).build();

		List<GoalStatus> statuses = engine.evaluate(snapshot, kb);
		GoalStatus nexus = goalFor(statuses, "poh:portal-nexus");
		PrerequisiteGap gap = (PrerequisiteGap) onlyGap(nexus);
		assertEquals("poh:portal-chamber", gap.getGoalId());
		assertEquals("Portal chamber", gap.getName());
		assertFalse(nexus.isReady());
		assertEquals(List.of("Mark the room owned once built."), nexus.getNotes());
		assertTrue(goalFor(statuses, "poh:portal-chamber").isReady(), "the room itself: requirements met, still a goal until owned");

		List<GoalStatus> chamberOwned = engine.evaluate(snapshot, kb, DiaryProgress.compute(snapshot, kb), Set.of("poh:portal-chamber"));
		assertFalse(hasGoal(chamberOwned, "poh:portal-chamber"));
		assertTrue(goalFor(chamberOwned, "poh:portal-nexus").isReady(), "owned prerequisite counts as met: " + goalFor(chamberOwned, "poh:portal-nexus"));
	}

	/** With the portal chamber unowned, the nexus is not ready and the chamber is what ranks first. */
	@Test
	void prerequisiteGoalRanksBeforeItsDependantWhileUnowned()
	{
		KnowledgeBase kb = new KbBuilder()
			.milestone("poh:portal-nexus", MilestoneCategory.POH, "Portal nexus", 8).skill(Skill.CONSTRUCTION, 72).prerequisite("poh:portal-chamber")
			.milestone("poh:portal-chamber", MilestoneCategory.POH, "Portal chamber", 6).skill(Skill.CONSTRUCTION, 50)
			.build();
		Snapshot snapshot = new SnapshotBuilder().skill(Skill.CONSTRUCTION, 99).build();

		List<String> ranked = new Ranker().rank(engine.evaluate(snapshot, kb), Set.of(), List.of(), 2).stream()
			.map(r -> r.getStatus().getGoal().getId()).collect(Collectors.toList());

		assertEquals(List.of("poh:portal-chamber", "poh:portal-nexus"), ranked);
	}

	@Test
	void bundledGalleryIsSuggestedBeforeHigherPriorityBasicBoxWhenOnlyRoomCoinsAreMissing()
	{
		KnowledgeBase kb = KnowledgeBase.load(new Gson());
		Snapshot snapshot = new SnapshotBuilder().skill(Skill.CONSTRUCTION, 81)
			.bankItem(8790, "Bolt of cloth", 1).bankItem(2353, "Steel bar", 1)
			.bankItem(2552, "Ring of dueling(8)", 3).bankItem(3853, "Games necklace(8)", 3).build();

		assertPrerequisiteSuggestedFirst(kb, snapshot, "poh:achievement-gallery", "poh:basic-jewellery-box");
	}

	@Test
	void bundledGardenIsSuggestedBeforeHigherPriorityPoolWhenOnlyRoomCoinsAreMissing()
	{
		KnowledgeBase kb = KnowledgeBase.load(new Gson());
		Snapshot snapshot = new SnapshotBuilder().skill(Skill.CONSTRUCTION, 65)
			.bankItem(3420, "Limestone brick", 5).bankItem(1929, "Bucket of water", 5)
			.bankItem(566, "Soul rune", 1000).bankItem(559, "Body rune", 1000).build();

		assertPrerequisiteSuggestedFirst(kb, snapshot, "poh:superior-garden", "poh:restoration-pool");
	}

	@Test
	void bundledOrnateBoxAcceptsGloriesWithFourFiveOrSixChargesButNotThree()
	{
		KnowledgeBase kb = KnowledgeBase.load(new Gson());
		Snapshot sufficient = new SnapshotBuilder().accountType(AccountType.GROUP).skill(Skill.CONSTRUCTION, 91)
			.bankItem(8784, "Gold leaf", 3).bankItem(11118, "Combat bracelet(4)", 5)
			.bankItem(11105, "Skills necklace(4)", 5).bankItem(11980, "Ring of wealth (5)", 8)
			.bankItem(1712, "Amulet of glory(4)", 2).inventoryItem(11976, "Amulet of glory(5)", 3)
			.groupStorageItem(11978, "Amulet of glory(6)", 3).build();
		GoalStatus ready = goalFor(engine.evaluate(sufficient, kb, DiaryProgress.compute(sufficient, kb),
			Set.of("poh:basic-jewellery-box")), "poh:ornate-jewellery-box");
		assertTrue(ready.isReady(), "all eight glories with four or more charges should satisfy the box: " + ready);

		Snapshot insufficient = new SnapshotBuilder().bankItem(1710, "Amulet of glory(3)", 8).build();
		GoalStatus shortBox = goalFor(engine.evaluate(insufficient, kb), "poh:ornate-jewellery-box");
		ItemGap glories = shortBox.getGaps().stream().filter(g -> g instanceof ItemGap)
			.map(g -> (ItemGap) g).filter(g -> g.getName().equals("Amulet of glory(4)")).findFirst().orElseThrow();
		assertEquals(0, glories.getHave());
		assertEquals(8, glories.getNeed());
	}

	private void assertPrerequisiteSuggestedFirst(KnowledgeBase kb, Snapshot snapshot, String prerequisite, String dependant)
	{
		Set<String> ids = Set.of(prerequisite, dependant);
		List<GoalStatus> statuses = engine.evaluate(snapshot, kb, DiaryProgress.compute(snapshot, kb), Set.of("poh:house"))
			.stream().filter(s -> ids.contains(s.getGoal().getId())).collect(Collectors.toList());
		assertTrue(onlyGap(goalFor(statuses, prerequisite)) instanceof ItemGap);
		assertTrue(onlyGap(goalFor(statuses, dependant)) instanceof PrerequisiteGap);
		assertTrue(Ranker.score(goalFor(statuses, dependant)) > Ranker.score(goalFor(statuses, prerequisite)),
			"regression requires the dependant to win on raw score");
		List<RankedGoal> ranked = new Ranker().rank(statuses, Set.of(), List.of());
		assertEquals(List.of(prerequisite, dependant), ranked.stream().map(r -> r.getStatus().getGoal().getId()).collect(Collectors.toList()));
		assertEquals(List.of(prerequisite, dependant), new SuggestSelector().pick3(ranked, kb).stream()
			.map(r -> r.getStatus().getGoal().getId()).collect(Collectors.toList()));
	}

	/** A manual override finishes a slayer target even below the required level. */
	@Test
	void slayerTargetDoneWhenManuallyMarkedOwnedEvenBelowRequiredLevel()
	{
		KnowledgeBase kb = new KbBuilder()
			.milestone("slayer:test", MilestoneCategory.SLAYER_TARGET, "Test Slayer Target", 5)
			.skill(Skill.SLAYER, 85)
			.build();
		Snapshot snapshot = new SnapshotBuilder().skill(Skill.SLAYER, 50).build();

		List<GoalStatus> statuses = engine.evaluate(snapshot, kb, DiaryProgress.compute(snapshot, kb), Set.of("slayer:test"));

		assertFalse(hasGoal(statuses, "slayer:test"), "manually-owned slayer target should not be emitted: " + statuses);
	}

	/** A boss's "Done it" override finishes it, even though a boss is otherwise never done. */
	@Test
	void bossMilestoneDoneWhenManuallyMarkedDone()
	{
		KnowledgeBase kb = new KbBuilder()
			.milestone("boss:test", MilestoneCategory.BOSS, "Test Boss", 5)
			.skill(Skill.STRENGTH, 60)
			.build();
		Snapshot snapshot = new SnapshotBuilder().skill(Skill.STRENGTH, 60).build();

		List<GoalStatus> statuses = engine.evaluate(snapshot, kb, DiaryProgress.compute(snapshot, kb), Set.of("boss:test"));

		assertFalse(hasGoal(statuses, "boss:test"), "manually-marked-done boss should not be emitted: " + statuses);
	}

	@Test
	void slayerTargetDoneWhenSlayerLevelMeetsRequirement()
	{
		KnowledgeBase kb = new KbBuilder()
			.milestone("slayer:test", MilestoneCategory.SLAYER_TARGET, "Test Slayer Target", 5)
			.skill(Skill.SLAYER, 85)
			.build();
		Snapshot snapshot = new SnapshotBuilder().skill(Skill.SLAYER, 85).build();

		List<GoalStatus> statuses = engine.evaluate(snapshot, kb);

		assertFalse(hasGoal(statuses, "slayer:test"), "slayer target at required level should not be emitted: " + statuses);
	}

	@Test
	void slayerTargetEmittedBelowRequiredLevelWithSkillLevelGap()
	{
		KnowledgeBase kb = new KbBuilder()
			.milestone("slayer:test", MilestoneCategory.SLAYER_TARGET, "Test Slayer Target", 5)
			.skill(Skill.SLAYER, 85)
			.build();
		Snapshot snapshot = new SnapshotBuilder().skill(Skill.SLAYER, 50).build();

		GoalStatus status = goalFor(engine.evaluate(snapshot, kb), "slayer:test");
		SkillLevelGap gap = (SkillLevelGap) onlyGap(status);

		assertEquals(Skill.SLAYER, gap.getSkill());
		assertEquals(50, gap.getHave());
		assertEquals(85, gap.getNeed());
	}

	@Test
	void unlockMilestoneDoneWhenAllRequirementsMet()
	{
		KnowledgeBase kb = new KbBuilder()
			.milestone("milestone:test-unlock", MilestoneCategory.UNLOCK, "Test Unlock", 5)
			.skill(Skill.AGILITY, 40)
			.build();
		Snapshot snapshot = new SnapshotBuilder().skill(Skill.AGILITY, 40).build();

		List<GoalStatus> statuses = engine.evaluate(snapshot, kb);

		assertFalse(hasGoal(statuses, "milestone:test-unlock"), "unlock with requirements met should not be emitted: " + statuses);
	}

	@Test
	void unlockMilestoneEmittedWithGapsWhenRequirementsUnmet()
	{
		KnowledgeBase kb = new KbBuilder()
			.milestone("milestone:test-unlock", MilestoneCategory.UNLOCK, "Test Unlock", 5)
			.skill(Skill.AGILITY, 40)
			.build();
		Snapshot snapshot = new SnapshotBuilder().build();

		GoalStatus status = goalFor(engine.evaluate(snapshot, kb), "milestone:test-unlock");
		SkillLevelGap gap = (SkillLevelGap) onlyGap(status);

		assertEquals(Skill.AGILITY, gap.getSkill());
		assertFalse(status.isReady());
	}

	@Test
	void bossMilestoneWithRequirementsMetIsEmittedAndReady()
	{
		KnowledgeBase kb = new KbBuilder()
			.milestone("boss:test", MilestoneCategory.BOSS, "Test Boss", 5)
			.skill(Skill.STRENGTH, 60)
			.build();
		Snapshot snapshot = new SnapshotBuilder().skill(Skill.STRENGTH, 60).build();

		GoalStatus status = goalFor(engine.evaluate(snapshot, kb), "boss:test");

		assertTrue(status.getGaps().isEmpty());
		assertTrue(status.isReady());
	}

	@Test
	void diaryTierGapProducedForIncompleteDiaryRequirement()
	{
		KnowledgeBase kb = new KbBuilder()
			.milestone("milestone:test-diary", MilestoneCategory.UNLOCK, "Test Diary Unlock", 5)
			.diary(DiaryTier.WESTERN_HARD)
			.build();
		Snapshot snapshot = new SnapshotBuilder().build();

		GoalStatus status = goalFor(engine.evaluate(snapshot, kb), "milestone:test-diary");
		DiaryTierGap gap = (DiaryTierGap) onlyGap(status);

		assertEquals(DiaryTier.WESTERN_HARD, gap.getTier());
	}

	@Test
	void itemGapByIdMustObtainWhenOnlyGeSourceExistsForAnIron()
	{
		KnowledgeBase kb = new KbBuilder()
			.milestone("milestone:test-item", MilestoneCategory.UNLOCK, "Test Item Unlock", 5)
			.item("Special item", 999, 1, "GE")
			.build();
		Snapshot snapshot = new SnapshotBuilder().iron().build();

		GoalStatus status = goalFor(engine.evaluate(snapshot, kb), "milestone:test-item");
		ItemGap gap = (ItemGap) onlyGap(status);

		assertEquals("Special item", gap.getName());
		assertEquals(0, gap.getHave());
		assertEquals(1, gap.getNeed());
		assertTrue(gap.getSources().isEmpty(), "GE source should be filtered out for an iron: " + gap.getSources());
		assertTrue(gap.isMustObtain());
	}

	@Test
	void milestoneItemRequirementSumsHaveAcrossVariantIds()
	{
		KnowledgeBase kb = new KbBuilder()
			.milestone("milestone:test-item-ids", MilestoneCategory.UNLOCK, "Test Item Ids", 5)
			.itemIds("Mithril arrow", 884, 5, 9236, 9237)
			.build();
		Snapshot snapshot = new SnapshotBuilder()
			.inventoryItem(884, "Mithril arrow", 2)
			.bankItem(9236, "Mithril arrow(p)", 3)
			.build();

		List<GoalStatus> statuses = engine.evaluate(snapshot, kb);

		assertFalse(hasGoal(statuses, "milestone:test-item-ids"),
			"2 + 3 across variant ids should meet a requirement of 5: " + statuses);
	}

	@Test
	void milestoneItemRequirementGapByIdIsStillShortWhenVariantsDontSumEnough()
	{
		KnowledgeBase kb = new KbBuilder()
			.milestone("milestone:test-item-ids", MilestoneCategory.UNLOCK, "Test Item Ids", 5)
			.itemIds("Mithril arrow", 884, 5, 9236, 9237)
			.build();
		Snapshot snapshot = new SnapshotBuilder()
			.inventoryItem(884, "Mithril arrow", 2)
			.bankItem(9236, "Mithril arrow(p)", 1)
			.build();

		GoalStatus status = goalFor(engine.evaluate(snapshot, kb), "milestone:test-item-ids");
		ItemGap gap = (ItemGap) onlyGap(status);

		assertEquals(3, gap.getHave());
		assertEquals(5, gap.getNeed());
	}

	@Test
	void priorityOverrideAppliedInsteadOfMilestonesOwnPriority()
	{
		KnowledgeBase kb = new KbBuilder()
			.milestone("milestone:test-priority", MilestoneCategory.UNLOCK, "Test Priority", 5)
			.skill(Skill.AGILITY, 40)
			.priorityOverride("milestone:test-priority", 9)
			.build();
		Snapshot snapshot = new SnapshotBuilder().build();

		GoalStatus status = goalFor(engine.evaluate(snapshot, kb), "milestone:test-priority");

		assertEquals(9, status.getGoal().getPriority());
	}

	@Test
	void superiorEquipmentSuppressesObsoleteGoalsWithoutInventingPrerequisitesOrIngredients()
	{
		KnowledgeBase kb = new KbBuilder()
			.milestone("milestone:torso", MilestoneCategory.GEAR, "Fighter torso", 5).ownedIf("Fighter torso", 10551)
			.milestone("milestone:boots", MilestoneCategory.GEAR, "Dragon boots", 5).ownedIf("Dragon boots", 11840)
			.milestone("milestone:infinity", MilestoneCategory.GEAR, "Infinity boots", 5).ownedIf("Infinity boots", 6920)
			.milestone("milestone:dependent", MilestoneCategory.GEAR, "Dependent", 5).ownedIf("Target", 999)
			.prerequisite("milestone:boots").item("Dragon boots", 11840, 1, "GE")
			.build();
		Snapshot snapshot = new SnapshotBuilder().bankItem(11832, "Bandos chestplate", 1)
			.equipmentItem(13239, "Primordial boots", 1).bankItem(13235, "Eternal boots", 1).build();
		GearComparison comparison = new GearComparison(snapshot, KnowledgeBase.load(new Gson()), Set.of());
		List<GoalStatus> goals = engine.evaluate(snapshot, kb, DiaryProgress.compute(snapshot, kb), Set.of(), comparison);
		assertFalse(hasGoal(goals, "milestone:torso"));
		assertFalse(hasGoal(goals, "milestone:boots"));
		assertFalse(hasGoal(goals, "milestone:infinity"));
		assertFalse(GapEngine.isOwned(kb.milestoneById("milestone:boots"), snapshot, Set.of()));
		GoalStatus dependent = goalFor(goals, "milestone:dependent");
		assertTrue(dependent.getGaps().stream().anyMatch(PrerequisiteGap.class::isInstance));
		assertTrue(dependent.getGaps().stream().filter(ItemGap.class::isInstance).map(ItemGap.class::cast)
			.anyMatch(g -> g.getName().equals("Dragon boots") && g.getHave() == 0));
	}

	@Test
	void partialSetCoverageRequiresEachPieceRatherThanCopiesOfASuperiorBody()
	{
		KnowledgeBase kb = new KbBuilder().milestone("milestone:set", MilestoneCategory.GEAR, "Armour set", 5)
			.ownedIf("Rune platebody", 1127).ownedIf("Rune platelegs", 1079).ownedIfMin(2).build();
		KnowledgeBase catalog = KnowledgeBase.load(new Gson());
		Snapshot bodyOnly = new SnapshotBuilder().bankItem(11832, "Bandos chestplate", 6).build();
		assertTrue(hasGoal(engine.evaluate(bodyOnly, kb, DiaryProgress.compute(bodyOnly, kb), Set.of(),
			new GearComparison(bodyOnly, catalog, Set.of())), "milestone:set"));
		Snapshot bothRoles = bodyOnly.toBuilder().equipment(java.util.Map.of(11834, 1)).build();
		assertFalse(hasGoal(engine.evaluate(bothRoles, kb, DiaryProgress.compute(bothRoles, kb), Set.of(),
			new GearComparison(bothRoles, catalog, Set.of())), "milestone:set"));
		assertEquals(0, GapEngine.ownedIfHeld(kb.milestoneById("milestone:set"), bothRoles));
	}

	@Test
	void disabledSharedStorageAndBroadManualMilestonesDoNotInventSpecificEquipment()
	{
		KnowledgeBase kb = new KbBuilder().milestone("milestone:drops", MilestoneCategory.GEAR, "Drops", 5)
			.ownedIf("Body", 100).ownedIf("Legs", 200)
			.milestone("boss:test", MilestoneCategory.BOSS, "Boss", 5).recommendedGearGroup("melee-legs", new com.signpost.kb.OwnedItem("Legs", 200)).build();
		Snapshot snapshot = new SnapshotBuilder().accountType(AccountType.GROUP).groupStorageItem(200, "Legs", 1)
			.build().toBuilder().groupStorageEnabled(false).build();
		Set<String> manual = Set.of("milestone:drops");
		GoalStatus boss = goalFor(engine.evaluate(snapshot, kb, DiaryProgress.compute(snapshot, kb), manual), "boss:test");
		assertFalse(boss.isReady());
		assertFalse(new GearComparison(snapshot, kb, manual).owns(List.of(200)));
	}

	// --- helpers ---

	private static boolean hasGoal(List<GoalStatus> statuses, String goalId)
	{
		return statuses.stream().anyMatch(s -> s.getGoal().getId().equals(goalId));
	}

	private static GoalStatus goalFor(List<GoalStatus> statuses, String goalId)
	{
		return statuses.stream().filter(s -> s.getGoal().getId().equals(goalId)).findFirst()
			.orElseThrow(() -> new AssertionError("no goal " + goalId + " in " + statuses));
	}

	private static Gap onlyGap(GoalStatus status)
	{
		assertEquals(1, status.getGaps().size(), "expected exactly one gap: " + status.getGaps());
		return status.getGaps().get(0);
	}
}
