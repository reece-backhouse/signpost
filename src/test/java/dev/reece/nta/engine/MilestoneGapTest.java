package dev.reece.nta.engine;

import dev.reece.nta.engine.model.DiaryTierGap;
import dev.reece.nta.engine.model.Gap;
import dev.reece.nta.engine.model.GoalStatus;
import dev.reece.nta.engine.model.ItemGap;
import dev.reece.nta.engine.model.PrerequisiteGap;
import dev.reece.nta.engine.model.SkillLevelGap;
import dev.reece.nta.kb.KnowledgeBase;
import dev.reece.nta.kb.MilestoneCategory;
import dev.reece.nta.snapshot.DiaryTier;
import dev.reece.nta.snapshot.Snapshot;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.runelite.api.Skill;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task 25: milestone goals (gear/unlock/prayer/spellbook/slayer_target/boss) produced by
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
	void gearMilestoneNotDoneAndBankUnknownWhenBankUnseenAndNothingOwnedElsewhere()
	{
		KnowledgeBase kb = new KbBuilder()
			.milestone("milestone:test-gear", MilestoneCategory.GEAR, "Test Gear", 5)
			.ownedIf("Item A", 100)
			.build();
		Snapshot snapshot = new SnapshotBuilder().bankUnknown().build();

		GoalStatus status = goalFor(engine.evaluate(snapshot, kb), "milestone:test-gear");

		assertTrue(status.isBankUnknown());
	}

	/** Ticket 55: a manual "Own it" override finishes a gear milestone even though nothing owned-if is held. */
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

	/** RL-006: an outfit milestone with {@code ownedIfMin} = set size is owned only once every piece is held. */
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
		Snapshot twoPieces = new SnapshotBuilder().bankItem(25549, "Golden prospector helmet", 1).bankItem(12014, "Prospector jacket", 1).build();
		Snapshot fullSet = new SnapshotBuilder().bankItem(12013, "Prospector helmet", 1).bankItem(12014, "Prospector jacket", 1)
			.bankItem(12015, "Prospector legs", 1).equipmentItem(12016, "Prospector boots", 1).build();

		assertTrue(hasGoal(engine.evaluate(twoPieces, kb), "milestone:prospector-outfit"), "two of four pieces is not owned");
		assertFalse(hasGoal(engine.evaluate(fullSet, kb), "milestone:prospector-outfit"), "all four pieces held should finish the outfit");
	}

	/**
	 * RL-007 AC2/AC5: a POH room is never auto-detected (still a goal at 99 Construction); a goal whose
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

	/** RL-007 AC2: with the portal chamber unowned, the nexus is not ready and the chamber is what ranks first. */
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

	/** Ticket 55: a manual override finishes a slayer target even below the required level. */
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

	/** Ticket 55: a boss's "Done it" override finishes it, even though a boss is otherwise never done. */
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
