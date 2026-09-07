package dev.reece.nta.engine;

import dev.reece.nta.engine.model.DiaryTierGap;
import dev.reece.nta.engine.model.Gap;
import dev.reece.nta.engine.model.GoalStatus;
import dev.reece.nta.engine.model.ItemGap;
import dev.reece.nta.engine.model.SkillLevelGap;
import dev.reece.nta.kb.KnowledgeBase;
import dev.reece.nta.kb.MilestoneCategory;
import dev.reece.nta.snapshot.DiaryTier;
import dev.reece.nta.snapshot.Snapshot;
import java.util.List;
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
