package dev.reece.nta.engine;

import dev.reece.nta.kb.KnowledgeBase;
import dev.reece.nta.kb.MilestoneCategory;
import dev.reece.nta.snapshot.Snapshot;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Task 42: {@link StageEstimator} derives the account's progression stage (1..4) from combat
 * level, total level, finished quest count, and owned gear milestones, per spec ruling 27.
 * Combat-skill levels below are chosen so {@code Experience.getCombatLevel} lands exactly on (or
 * just below) the threshold being tested; see the class javadoc for the derivation.
 */
class StageEstimatorTest
{
	private static final Skill[] COMBAT_SKILLS =
		{Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.HITPOINTS, Skill.RANGED, Skill.MAGIC, Skill.PRAYER};

	@Test
	void defaultAccountIsStageOne()
	{
		Snapshot snapshot = new SnapshotBuilder().build();
		KnowledgeBase kb = new KbBuilder().build();

		assertEquals(1, StageEstimator.estimate(snapshot, kb));
	}

	@Test
	void combatEightyFiveAloneReachesStageTwo()
	{
		SnapshotBuilder builder = new SnapshotBuilder();
		setCombatSkills(builder, 67); // combat level 85
		Snapshot snapshot = builder.build();
		KnowledgeBase kb = new KbBuilder().build();

		assertEquals(2, StageEstimator.estimate(snapshot, kb));
	}

	@Test
	void totalLevelFourteenHundredAloneReachesStageTwo()
	{
		SnapshotBuilder builder = new SnapshotBuilder();
		for (Skill skill : Skill.values())
		{
			if (!isCombatSkill(skill))
			{
				builder.skill(skill, 90); // 16 non-combat skills * 90 = 1440 >= 1400; combat skills stay level 1.
			}
		}
		Snapshot snapshot = builder.build();
		KnowledgeBase kb = new KbBuilder().build();

		assertEquals(2, StageEstimator.estimate(snapshot, kb));
	}

	@Test
	void oneHundredTwentyQuestsFinishedAloneReachesStageTwo()
	{
		SnapshotBuilder builder = new SnapshotBuilder();
		Quest[] quests = Quest.values();
		for (int i = 0; i < 120; i++)
		{
			builder.quest(quests[i], QuestState.FINISHED);
		}
		Snapshot snapshot = builder.build();
		KnowledgeBase kb = new KbBuilder().build();

		assertEquals(2, StageEstimator.estimate(snapshot, kb));
	}

	@Test
	void owningTwoStageTwoGearMilestonesAloneReachesStageTwo()
	{
		KnowledgeBase kb = new KbBuilder()
			.milestone("g:1", MilestoneCategory.GEAR, "Gear One", 5).ownedIf("Item One", 101).stage(2)
			.milestone("g:2", MilestoneCategory.GEAR, "Gear Two", 5).ownedIf("Item Two", 102).stage(2)
			.build();
		Snapshot snapshot = new SnapshotBuilder().inventoryItem(101, "Item One", 1).inventoryItem(102, "Item Two", 1).build();

		assertEquals(2, StageEstimator.estimate(snapshot, kb));
	}

	@Test
	void owningOnlyOneStageTwoGearMilestoneIsNotEnoughForStageTwo()
	{
		KnowledgeBase kb = new KbBuilder()
			.milestone("g:1", MilestoneCategory.GEAR, "Gear One", 5).ownedIf("Item One", 101).stage(2)
			.build();
		Snapshot snapshot = new SnapshotBuilder().inventoryItem(101, "Item One", 1).build();

		assertEquals(1, StageEstimator.estimate(snapshot, kb));
	}

	@Test
	void combatOneTenAndTotalLevelNineteenHundredReachesStageThree()
	{
		SnapshotBuilder builder = new SnapshotBuilder();
		setCombatSkills(builder, 87); // combat level 110
		for (Skill skill : Skill.values())
		{
			if (!isCombatSkill(skill))
			{
				builder.skill(skill, 85); // 16 * 85 = 1360; plus 7*87=609 combat -> total 1969 >= 1900.
			}
		}
		Snapshot snapshot = builder.build();
		KnowledgeBase kb = new KbBuilder().build();

		assertEquals(3, StageEstimator.estimate(snapshot, kb));
	}

	@Test
	void oneHundredNinetyQuestsFinishedAloneReachesStageThree()
	{
		SnapshotBuilder builder = new SnapshotBuilder();
		Quest[] quests = Quest.values();
		for (int i = 0; i < 190; i++)
		{
			builder.quest(quests[i], QuestState.FINISHED);
		}
		Snapshot snapshot = builder.build();
		KnowledgeBase kb = new KbBuilder().build();

		assertEquals(3, StageEstimator.estimate(snapshot, kb));
	}

	@Test
	void owningTwoStageThreeGearMilestonesAloneReachesStageThree()
	{
		KnowledgeBase kb = new KbBuilder()
			.milestone("g:1", MilestoneCategory.GEAR, "Gear One", 5).ownedIf("Item One", 101).stage(3)
			.milestone("g:2", MilestoneCategory.GEAR, "Gear Two", 5).ownedIf("Item Two", 102).stage(3)
			.build();
		Snapshot snapshot = new SnapshotBuilder().inventoryItem(101, "Item One", 1).inventoryItem(102, "Item Two", 1).build();

		assertEquals(3, StageEstimator.estimate(snapshot, kb));
	}

	@Test
	void owningAnyStageFourGearMilestoneAloneReachesStageFour()
	{
		KnowledgeBase kb = new KbBuilder()
			.milestone("g:1", MilestoneCategory.GEAR, "Gear One", 5).ownedIf("Item One", 101).stage(4)
			.build();
		Snapshot snapshot = new SnapshotBuilder().inventoryItem(101, "Item One", 1).build();

		assertEquals(4, StageEstimator.estimate(snapshot, kb));
	}

	@Test
	void combatOneTwentyAndTwoOwnedStageThreeGearMilestonesReachesStageFour()
	{
		KnowledgeBase kb = new KbBuilder()
			.milestone("g:1", MilestoneCategory.GEAR, "Gear One", 5).ownedIf("Item One", 101).stage(3)
			.milestone("g:2", MilestoneCategory.GEAR, "Gear Two", 5).ownedIf("Item Two", 102).stage(3)
			.build();
		SnapshotBuilder builder = new SnapshotBuilder().inventoryItem(101, "Item One", 1).inventoryItem(102, "Item Two", 1);
		setCombatSkills(builder, 95); // combat level 121 >= 120
		Snapshot snapshot = builder.build();

		assertEquals(4, StageEstimator.estimate(snapshot, kb));
	}

	@Test
	void combatOneTwentyAloneWithoutTwoOwnedStageThreeGearStaysBelowStageFour()
	{
		SnapshotBuilder builder = new SnapshotBuilder();
		setCombatSkills(builder, 95); // combat level 121 >= 120, but no owned stage-3 gear at all.
		Snapshot snapshot = builder.build();
		KnowledgeBase kb = new KbBuilder().build();

		// Falls through to the stage-2 combat>=85 branch, not stage 4.
		assertEquals(2, StageEstimator.estimate(snapshot, kb));
	}

	/** Spec ruling 27's motivating profile: combat ~105, total ~1600s, 150 quests, no raid gear -> stage 2. */
	@Test
	void midGameProfileFromTheUserFeedbackIsStageTwo()
	{
		SnapshotBuilder builder = new SnapshotBuilder();
		setCombatSkills(builder, 83); // combat level 105
		for (Skill skill : Skill.values())
		{
			if (!isCombatSkill(skill))
			{
				builder.skill(skill, 66);
			}
		}
		Quest[] quests = Quest.values();
		for (int i = 0; i < 150; i++)
		{
			builder.quest(quests[i], QuestState.FINISHED);
		}
		Snapshot snapshot = builder.build();
		KnowledgeBase kb = new KbBuilder()
			.milestone("boss:tob", MilestoneCategory.BOSS, "Theatre of Blood", 5)
			.milestone("boss:cox", MilestoneCategory.BOSS, "Chambers of Xeric", 5)
			.build();

		assertEquals(2, StageEstimator.estimate(snapshot, kb));
	}

	private static void setCombatSkills(SnapshotBuilder builder, int level)
	{
		for (Skill skill : COMBAT_SKILLS)
		{
			builder.skill(skill, level);
		}
	}

	private static boolean isCombatSkill(Skill skill)
	{
		for (Skill combatSkill : COMBAT_SKILLS)
		{
			if (combatSkill == skill)
			{
				return true;
			}
		}
		return false;
	}
}
