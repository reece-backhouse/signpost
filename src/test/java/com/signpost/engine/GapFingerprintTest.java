package com.signpost.engine;

import com.signpost.engine.model.CombatLevelGap;
import com.signpost.engine.model.DiaryTaskGap;
import com.signpost.engine.model.Gap;
import com.signpost.engine.model.GearGap;
import com.signpost.engine.model.Goal;
import com.signpost.engine.model.GoalCategory;
import com.signpost.engine.model.GoalStatus;
import com.signpost.engine.model.ItemGap;
import com.signpost.engine.model.PrerequisiteGap;
import com.signpost.engine.model.KudosGap;
import com.signpost.engine.model.QuestPointsGap;
import com.signpost.engine.model.QuestPrereqGap;
import com.signpost.engine.model.SkillLevelGap;
import com.signpost.kb.OwnedItem;
import java.util.List;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * {@link GapFingerprint} is a stable, order-independent digest over a goal's gap kinds
 * and keys, used by {@link PrefsResolver} to end a snooze early when the underlying gap changes.
 */
class GapFingerprintTest
{
	@Test
	void sameGapsInDifferentOrderProduceTheSameFingerprint()
	{
		GoalStatus a = status(List.of(
			skillGap(Skill.HERBLORE, 61, 70),
			new ItemGap("Snape grass", 0, 5, List.of(), false)));
		GoalStatus b = status(List.of(
			new ItemGap("Snape grass", 0, 5, List.of(), false),
			skillGap(Skill.HERBLORE, 61, 70)));

		assertEquals(GapFingerprint.of(a), GapFingerprint.of(b));
	}

	@Test
	void aChangedNeedProducesADifferentFingerprint()
	{
		GoalStatus before = status(List.of(skillGap(Skill.HERBLORE, 61, 70)));
		GoalStatus after = status(List.of(skillGap(Skill.HERBLORE, 65, 70)));

		// have changing but need staying the same: same fingerprint, need is the key, not have.
		assertEquals(GapFingerprint.of(before), GapFingerprint.of(after));

		GoalStatus needChanged = status(List.of(skillGap(Skill.HERBLORE, 61, 72)));
		assertNotEquals(GapFingerprint.of(before), GapFingerprint.of(needChanged));
	}

	@Test
	void anAddedOrRemovedGapProducesADifferentFingerprint()
	{
		GoalStatus one = status(List.of(skillGap(Skill.HERBLORE, 61, 70)));
		GoalStatus two = status(List.of(
			skillGap(Skill.HERBLORE, 61, 70),
			new ItemGap("Toadflax", 0, 10, List.of(), false)));

		assertNotEquals(GapFingerprint.of(one), GapFingerprint.of(two));
	}

	@Test
	void gearGapContributesAKeyOrderIndependentOfAcceptableItemOrder()
	{
		GoalStatus a = status(List.of(new GearGap("melee weapon", List.of(new OwnedItem("A", 1), new OwnedItem("B", 2)), false)));
		GoalStatus b = status(List.of(new GearGap("melee weapon", List.of(new OwnedItem("B", 2), new OwnedItem("A", 1)), false)));

		assertEquals(GapFingerprint.of(a), GapFingerprint.of(b));
	}

	@Test
	void aChangedGearGapAcceptableSetProducesADifferentFingerprint()
	{
		GoalStatus before = status(List.of(new GearGap("melee weapon", List.of(new OwnedItem("A", 1)), false)));
		GoalStatus after = status(List.of(new GearGap("melee weapon", List.of(new OwnedItem("A", 1), new OwnedItem("B", 2)), false)));

		assertNotEquals(GapFingerprint.of(before), GapFingerprint.of(after));
	}

	@Test
	void noGapsAtAllIsAStableEmptyFingerprint()
	{
		assertEquals(GapFingerprint.of(status(List.of())), GapFingerprint.of(status(List.of())));
	}

	@Test
	void everyGapKindContributesAKey()
	{
		GoalStatus status = status(List.of(
			skillGap(Skill.MINING, 20, 30),
			new QuestPrereqGap(Quest.COOKS_ASSISTANT, QuestState.NOT_STARTED, true, false, "https://example.test/w/Cook's_Assistant"),
			new ItemGap("Egg", 0, 1, List.of(), false),
			new QuestPointsGap(5, 10),
			new KudosGap(0, 5),
			new CombatLevelGap(50, 60, false),
			new DiaryTaskGap(1, "text", List.of(skillGap(Skill.FISHING, 1, 5)), List.of())));

		String fingerprint = GapFingerprint.of(status);
		assertNotEquals("", fingerprint);
	}

	private static SkillLevelGap skillGap(Skill skill, int have, int need)
	{
		return new SkillLevelGap(skill, have, need, 0, false, null, false);
	}

	/** A prerequisite gap is keyed by the prerequisite's id, so a different prerequisite is a different fingerprint. */
	@Test
	void prerequisiteGapIsKeyedByThePrerequisiteGoalId()
	{
		GoalStatus chamber = status(List.of(new PrerequisiteGap("poh:portal-chamber", "Portal chamber")));
		GoalStatus garden = status(List.of(new PrerequisiteGap("poh:superior-garden", "Superior garden")));

		assertEquals("prereq:poh:portal-chamber", GapFingerprint.of(chamber));
		assertNotEquals(GapFingerprint.of(chamber), GapFingerprint.of(garden));
	}

	private static GoalStatus status(List<Gap> gaps)
	{
		Goal goal = new Goal("g1", GoalCategory.QUEST, "g1", "https://x", 5, 1);
		return new GoalStatus(goal, gaps, gaps.isEmpty(), false, List.of());
	}
}
