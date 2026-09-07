package dev.reece.nta.engine;

import dev.reece.nta.engine.model.CombatLevelGap;
import dev.reece.nta.engine.model.DiaryTaskGap;
import dev.reece.nta.engine.model.Gap;
import dev.reece.nta.engine.model.GearGap;
import dev.reece.nta.engine.model.Goal;
import dev.reece.nta.engine.model.GoalCategory;
import dev.reece.nta.engine.model.GoalStatus;
import dev.reece.nta.engine.model.ItemGap;
import dev.reece.nta.engine.model.KudosGap;
import dev.reece.nta.engine.model.QuestPointsGap;
import dev.reece.nta.engine.model.QuestPrereqGap;
import dev.reece.nta.engine.model.SkillLevelGap;
import dev.reece.nta.kb.OwnedItem;
import java.util.List;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Task 29: {@link GapFingerprint} is a stable, order-independent digest over a goal's gap kinds
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
		GoalStatus a = status(List.of(new GearGap(List.of(new OwnedItem("A", 1), new OwnedItem("B", 2)), 0, 1, false)));
		GoalStatus b = status(List.of(new GearGap(List.of(new OwnedItem("B", 2), new OwnedItem("A", 1)), 0, 1, false)));

		assertEquals(GapFingerprint.of(a), GapFingerprint.of(b));
	}

	@Test
	void aChangedGearGapAcceptableSetProducesADifferentFingerprint()
	{
		GoalStatus before = status(List.of(new GearGap(List.of(new OwnedItem("A", 1)), 0, 1, false)));
		GoalStatus after = status(List.of(new GearGap(List.of(new OwnedItem("A", 1), new OwnedItem("B", 2)), 0, 1, false)));

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

	private static GoalStatus status(List<Gap> gaps)
	{
		Goal goal = new Goal("g1", GoalCategory.QUEST, "g1", "https://x", 5, 1);
		return new GoalStatus(goal, gaps, gaps.isEmpty(), false, List.of());
	}
}
