package dev.reece.nta.engine;

import dev.reece.nta.engine.model.DiaryTaskGap;
import dev.reece.nta.engine.model.Gap;
import dev.reece.nta.engine.model.Goal;
import dev.reece.nta.engine.model.GoalCategory;
import dev.reece.nta.engine.model.GoalStatus;
import dev.reece.nta.engine.model.ItemGap;
import dev.reece.nta.engine.model.NextStep;
import dev.reece.nta.engine.model.NextStepType;
import dev.reece.nta.engine.model.QuestPrereqGap;
import dev.reece.nta.engine.model.SkillLevelGap;
import dev.reece.nta.kb.KnowledgeBase;
import dev.reece.nta.snapshot.Snapshot;
import java.util.List;
import net.runelite.api.Experience;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/** Task 36: {@link NextStepPicker}, ticket E3 rule a-d. */
class NextStepPickerTest
{
	private final NextStepPicker picker = new NextStepPicker();

	@Test
	void branchAPicksAStartHereQuestPrereqOverEverythingElse()
	{
		QuestPrereqGap questGap = new QuestPrereqGap(Quest.BIOHAZARD, QuestState.NOT_STARTED, true, false, "https://example.test/w/Biohazard");
		SkillLevelGap skillGap = new SkillLevelGap(Skill.HERBLORE, 1, 10, 1000, false, null, false);
		GoalStatus status = status(List.of(questGap, skillGap));

		NextStep step = picker.next(status, new SnapshotBuilder().build(), new KbBuilder().build());

		assertEquals(NextStepType.QUEST, step.getType());
		assertSame(questGap, step.getQuestGap());
	}

	@Test
	void branchAFindsAStartHereQuestPrereqNestedInsideADiaryTaskGap()
	{
		QuestPrereqGap questGap = new QuestPrereqGap(Quest.BIOHAZARD, QuestState.NOT_STARTED, true, false, "https://example.test/w/Biohazard");
		DiaryTaskGap taskGap = new DiaryTaskGap(1, "task", List.of(questGap), List.of());
		GoalStatus status = status(List.of(taskGap));

		NextStep step = picker.next(status, new SnapshotBuilder().build(), new KbBuilder().build());

		assertEquals(NextStepType.QUEST, step.getType());
		assertSame(questGap, step.getQuestGap());
	}

	@Test
	void aQuestPrereqThatIsNotStartHereIsSkippedInFavourOfALaterBranch()
	{
		QuestPrereqGap notStartHere = new QuestPrereqGap(Quest.BIOHAZARD, QuestState.NOT_STARTED, false, false, "https://example.test/w/Biohazard");
		ItemGap itemGap = new ItemGap("Rope", 0, 1, List.of(), false);
		GoalStatus status = status(List.of(notStartHere, itemGap));

		NextStep step = picker.next(status, new SnapshotBuilder().build(), new KbBuilder().build());

		assertEquals(NextStepType.ITEM, step.getType());
	}

	@Test
	void branchBPicksTheSmallestXpDeltaSkillGapAmongThoseWithAFullyCoveredRoute()
	{
		KnowledgeBase kb = new KbBuilder()
			.method(Skill.HERBLORE, "H", 1, 10)
			.material(1, 1)
			.method(Skill.PRAYER, "P", 1, 10)
			.material(2, 1)
			.build();
		Snapshot snapshot = new SnapshotBuilder().bankItem(2, "P material", 100_000).build();

		// Smaller xpDelta, but no bank materials for HERBLORE -> its route can't be covered.
		SkillLevelGap smallerButUncoverable = new SkillLevelGap(Skill.HERBLORE, 1, 10, Experience.getXpForLevel(10), false, null, false);
		// Larger xpDelta, but fully covered from the bank.
		SkillLevelGap largerButCovered = new SkillLevelGap(Skill.PRAYER, 1, 20, Experience.getXpForLevel(20), false, null, false);
		GoalStatus status = status(List.of(smallerButUncoverable, largerButCovered));

		NextStep step = picker.next(status, snapshot, kb);

		assertEquals(NextStepType.SKILL, step.getType());
		assertSame(largerButCovered, step.getSkillGap());
		assertEquals(0, step.getRoute().getUncoveredXp());
	}

	@Test
	void branchCPicksTheSmallestXpDeltaSkillGapOverallWhenNoneHaveAFullyCoveredRoute()
	{
		KnowledgeBase kb = new KbBuilder()
			.method(Skill.HERBLORE, "H", 1, 10)
			.material(1, 1)
			.method(Skill.PRAYER, "P", 1, 10)
			.material(2, 1)
			.build();
		Snapshot snapshot = new SnapshotBuilder().build();

		SkillLevelGap smaller = new SkillLevelGap(Skill.HERBLORE, 1, 10, Experience.getXpForLevel(10), false, null, false);
		SkillLevelGap larger = new SkillLevelGap(Skill.PRAYER, 1, 20, Experience.getXpForLevel(20), false, null, false);
		GoalStatus status = status(List.of(larger, smaller));

		NextStep step = picker.next(status, snapshot, kb);

		assertEquals(NextStepType.SKILL, step.getType());
		assertSame(smaller, step.getSkillGap());
		assertEquals(smaller.getXpDelta(), step.getRoute().getUncoveredXp());
	}

	@Test
	void branchDPicksTheFirstItemGapWhenThereAreNoQuestOrSkillGaps()
	{
		ItemGap first = new ItemGap("Rope", 0, 1, List.of(), false);
		ItemGap second = new ItemGap("Bucket", 0, 1, List.of(), false);
		GoalStatus status = status(List.of(first, second));

		NextStep step = picker.next(status, new SnapshotBuilder().build(), new KbBuilder().build());

		assertEquals(NextStepType.ITEM, step.getType());
		assertSame(first, step.getItemGap());
	}

	@Test
	void branchDFindsAnItemGapNestedInsideADiaryTaskGap()
	{
		ItemGap itemGap = new ItemGap("Rope", 0, 1, List.of(), false);
		DiaryTaskGap taskGap = new DiaryTaskGap(1, "task", List.of(itemGap), List.of());
		GoalStatus status = status(List.of(taskGap));

		NextStep step = picker.next(status, new SnapshotBuilder().build(), new KbBuilder().build());

		assertEquals(NextStepType.ITEM, step.getType());
		assertSame(itemGap, step.getItemGap());
	}

	@Test
	void noGapsProducesNone()
	{
		GoalStatus status = status(List.of());

		NextStep step = picker.next(status, new SnapshotBuilder().build(), new KbBuilder().build());

		assertEquals(NextStepType.NONE, step.getType());
	}

	private static GoalStatus status(List<Gap> gaps)
	{
		Goal goal = new Goal("quest:0", GoalCategory.QUEST, "Test Goal", "https://example.test", 5, 1);
		return new GoalStatus(goal, gaps, gaps.isEmpty(), false, List.of());
	}

	/** RL-003 AC3: the simulated bank every route and shortfall draws on sums bank, containers and group storage. */
	@Test
	void bankAllSumsGroupStorageIntoThePool()
	{
		Snapshot snapshot = new SnapshotBuilder()
			.bankItem(10, "Ranarr weed", 3)
			.inventoryItem(10, "Ranarr weed", 2)
			.groupStorageItem(10, "Ranarr weed", 7)
			.groupStorageItem(11, "Vial of water", 5)
			.build();

		assertEquals(12, NextStepPicker.bankAll(snapshot).get(10));
		assertEquals(5, NextStepPicker.bankAll(snapshot).get(11));
	}
}
