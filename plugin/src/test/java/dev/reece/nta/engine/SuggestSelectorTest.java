package dev.reece.nta.engine;

import dev.reece.nta.engine.model.Goal;
import dev.reece.nta.engine.model.GoalCategory;
import dev.reece.nta.engine.model.GoalStatus;
import dev.reece.nta.engine.model.RankedGoal;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task 27: {@link SuggestSelector} picks the panel's three "Pick one" suggestions per spec ruling
 * 17 / ticket D4. Every {@code ranked} fixture here is already in the order {@link Ranker} would
 * produce (pinned entries first).
 */
class SuggestSelectorTest
{
	private final SuggestSelector selector = new SuggestSelector();

	@Test
	void slotThreeSkipsAheadToADifferentCategoryWhenSlotsOneAndTwoShareOne()
	{
		RankedGoal r1 = ranked("r1", GoalCategory.MILESTONE, 10, false);
		RankedGoal r2 = ranked("r2", GoalCategory.MILESTONE, 9, false);
		RankedGoal r3 = ranked("r3", GoalCategory.MILESTONE, 8, false);
		RankedGoal r4 = ranked("r4", GoalCategory.QUEST, 7, false);

		List<RankedGoal> picked = selector.pick3(List.of(r1, r2, r3, r4));

		assertEquals(List.of("r1", "r2", "r4"), ids(picked), "r3 (same category as r1/r2) should be skipped for r4: " + ids(picked));
	}

	@Test
	void pinsFillSlotsOneAndTwoFirstThenCategoryRuleDecidesSlotThree()
	{
		RankedGoal pinX = ranked("pinX", GoalCategory.QUEST, 1, true);
		RankedGoal pinY = ranked("pinY", GoalCategory.QUEST, 1, true);
		RankedGoal r3 = ranked("r3", GoalCategory.MILESTONE, 20, false);

		List<RankedGoal> picked = selector.pick3(List.of(pinX, pinY, r3));

		assertEquals(List.of("pinX", "pinY", "r3"), ids(picked));
	}

	@Test
	void threePinsFillAllSlotsRegardlessOfCategory()
	{
		RankedGoal p1 = ranked("p1", GoalCategory.QUEST, 1, true);
		RankedGoal p2 = ranked("p2", GoalCategory.QUEST, 1, true);
		RankedGoal p3 = ranked("p3", GoalCategory.QUEST, 1, true);
		RankedGoal r4 = ranked("r4", GoalCategory.MILESTONE, 20, false);

		List<RankedGoal> picked = selector.pick3(List.of(p1, p2, p3, r4));

		assertEquals(List.of("p1", "p2", "p3"), ids(picked), "pins fill all three slots even though they share a category: " + ids(picked));
	}

	@Test
	void singleCategoryFallsBackToPlainTopThree()
	{
		RankedGoal r1 = ranked("r1", GoalCategory.QUEST, 3, false);
		RankedGoal r2 = ranked("r2", GoalCategory.QUEST, 2, false);
		RankedGoal r3 = ranked("r3", GoalCategory.QUEST, 1, false);

		List<RankedGoal> picked = selector.pick3(List.of(r1, r2, r3));

		assertEquals(List.of("r1", "r2", "r3"), ids(picked));
	}

	@Test
	void returnsFewerThanThreeWhenFewerCandidatesExist()
	{
		RankedGoal onlyOne = ranked("only", GoalCategory.QUEST, 1, false);
		assertEquals(List.of("only"), ids(selector.pick3(List.of(onlyOne))));

		RankedGoal r1 = ranked("r1", GoalCategory.QUEST, 2, false);
		RankedGoal r2 = ranked("r2", GoalCategory.QUEST, 1, false);
		List<RankedGoal> pickedTwo = selector.pick3(List.of(r1, r2));
		assertEquals(2, pickedTwo.size(), "no third category-diverse candidate exists: " + ids(pickedTwo));
		assertEquals(List.of("r1", "r2"), ids(pickedTwo));

		assertTrue(selector.pick3(List.of()).isEmpty());
	}

	@Test
	void restIsRankedMinusPickedOrderPreserved()
	{
		RankedGoal r1 = ranked("r1", GoalCategory.MILESTONE, 10, false);
		RankedGoal r2 = ranked("r2", GoalCategory.MILESTONE, 9, false);
		RankedGoal r3 = ranked("r3", GoalCategory.MILESTONE, 8, false);
		RankedGoal r4 = ranked("r4", GoalCategory.QUEST, 7, false);
		List<RankedGoal> all = List.of(r1, r2, r3, r4);

		List<RankedGoal> picked = selector.pick3(all);
		List<RankedGoal> rest = selector.rest(all, picked);

		assertEquals(List.of("r3"), ids(rest));
	}

	private static RankedGoal ranked(String id, GoalCategory category, double score, boolean pinned)
	{
		Goal goal = new Goal(id, category, id, "https://x", 5);
		GoalStatus status = new GoalStatus(goal, List.of(), true, false, List.of());
		return new RankedGoal(status, score, pinned);
	}

	private static List<String> ids(List<RankedGoal> ranked)
	{
		return ranked.stream().map(r -> r.getStatus().getGoal().getId()).collect(Collectors.toList());
	}
}
