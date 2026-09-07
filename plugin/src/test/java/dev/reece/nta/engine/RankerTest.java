package dev.reece.nta.engine;

import dev.reece.nta.engine.model.CombatLevelGap;
import dev.reece.nta.engine.model.DiaryTaskGap;
import dev.reece.nta.engine.model.Gap;
import dev.reece.nta.engine.model.Goal;
import dev.reece.nta.engine.model.GoalCategory;
import dev.reece.nta.engine.model.GoalStatus;
import dev.reece.nta.engine.model.ItemGap;
import dev.reece.nta.engine.model.RankedGoal;
import dev.reece.nta.engine.model.SkillLevelGap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.runelite.api.Skill;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task 26: {@link Ranker} orders {@link GoalStatus}es into {@link RankedGoal}s per spec ruling 14.
 */
class RankerTest
{
	private final Ranker ranker = new Ranker();

	@Test
	void scoreFormulaOnThreeHandComputedGoals()
	{
		// No gaps at all: unmet 0, xpDelta 0 -> closeness 1 -> score = priority.
		GoalStatus zeroGaps = status("g1", GoalCategory.MILESTONE, 5, List.of());
		assertEquals(5.0, Ranker.score(zeroGaps), 1e-9);

		// Two plain top-level gaps (a combat level gap + an item gap) plus one SkillLevelGap with an
		// xpDelta of 125000: unmet = 3, xpDelta = 125000 -> closeness = 1 / (1 + 3 + 0.5) = 1/4.5.
		GoalStatus mixed = status("g2", GoalCategory.QUEST, 5, List.of(
			new CombatLevelGap(50, 60, false),
			new ItemGap("Rune", 0, 1, List.of(), false),
			skillGap(Skill.HERBLORE, 60, 70, 125_000)));
		assertEquals(5.0 * (1.0 / 4.5), Ranker.score(mixed), 1e-9);

		// A DiaryTaskGap with 2 inner gaps counts as 2 unmet (not 1), and its inner SkillLevelGap's
		// xpDelta counts too: unmet = 2, xpDelta = 250000 -> closeness = 1 / (1 + 2 + 1) = 1/4.
		DiaryTaskGap taskGap = new DiaryTaskGap(1, "task", List.of(
			skillGap(Skill.MINING, 40, 50, 250_000),
			new ItemGap("Pickaxe", 0, 1, List.of(), false)), List.of());
		GoalStatus diary = status("g3", GoalCategory.DIARY, 4, List.of(taskGap));
		assertEquals(4.0 * 0.25, Ranker.score(diary), 1e-9);
	}

	@Test
	void hiddenGoalsAreExcluded()
	{
		GoalStatus a = status("a", GoalCategory.QUEST, 5, List.of());
		GoalStatus b = status("b", GoalCategory.QUEST, 5, List.of());

		List<RankedGoal> ranked = ranker.rank(List.of(a, b), Set.of("a"), List.of());

		assertEquals(List.of("b"), ids(ranked));
	}

	@Test
	void pinsComeFirstInPinOrderRegardlessOfScore()
	{
		GoalStatus high = status("high", GoalCategory.QUEST, 5, List.of());
		GoalStatus low = status("low", GoalCategory.DIARY, 1, List.of(new CombatLevelGap(1, 99, false)));

		List<RankedGoal> ranked = ranker.rank(List.of(high, low), Set.of(), List.of("low", "high"));

		assertEquals(List.of("low", "high"), ids(ranked));
		assertTrue(ranked.get(0).isPinned());
		assertTrue(ranked.get(1).isPinned());
	}

	@Test
	void readyGoalsComeBeforeGoalsWithGaps()
	{
		GoalStatus ready = status("ready", GoalCategory.QUEST, 1, List.of());
		GoalStatus gapped = status("gapped", GoalCategory.QUEST, 10, List.of(new CombatLevelGap(1, 2, false)));

		List<RankedGoal> ranked = ranker.rank(List.of(gapped, ready), Set.of(), List.of());

		assertEquals(List.of("ready", "gapped"), ids(ranked));
	}

	@Test
	void unknownHaveItemGapCountsAsZeroUnmetButStillBlocksReady()
	{
		// Ruling 14: an ItemGap with have == null (bank not seen) counts as 0 unmet in the score,
		// but the goal is still never "Ready now" (that's bankUnknown's job, not the gap count's).
		GoalStatus status = status("g1", GoalCategory.MILESTONE, 5, List.of(new ItemGap("Rune", null, 1, List.of(), false)));
		assertEquals(5.0, Ranker.score(status), 1e-9, "unknown-have item gap should not count toward unmet");

		GoalStatus bankUnknown = new GoalStatus(status.getGoal(), status.getGaps(), false, true, List.of());
		List<RankedGoal> ranked = ranker.rank(List.of(bankUnknown, status("ready", GoalCategory.QUEST, 1, List.of())), Set.of(), List.of());
		assertEquals("ready", ranked.get(0).getStatus().getGoal().getId(), "bank-unknown goal must not rank as ready: " + ranked);
	}

	@Test
	void diaryTaskGapWithOnlyUnknownHaveItemGapsFloorsAtOneUnmetAndIsNotReady()
	{
		// Every inner gap is an unknown-have ItemGap (0 unmet each), but the DiaryTaskGap floor
		// still counts the task itself as 1 unmet, not 0.
		DiaryTaskGap taskGap = new DiaryTaskGap(1, "task", List.of(
			new ItemGap("Rune", null, 1, List.of(), false),
			new ItemGap("Feather", null, 5, List.of(), false)), List.of());
		GoalStatus status = new GoalStatus(new Goal("g1", GoalCategory.DIARY, "g1", "https://x", 4, 1), List.of(taskGap), false, true, List.of());

		assertEquals(4.0 * 0.5, Ranker.score(status), 1e-9, "floor: an all-unknown diary task should still count as 1 unmet, not 0");

		List<RankedGoal> ranked = ranker.rank(List.of(status, status("ready", GoalCategory.QUEST, 1, List.of())), Set.of(), List.of());
		assertEquals("ready", ranked.get(0).getStatus().getGoal().getId(), "goal with only unknown-have gaps must not rank as ready: " + ranked);
	}

	@Test
	void hiddenWinsOverPinned()
	{
		GoalStatus a = status("a", GoalCategory.QUEST, 5, List.of());
		GoalStatus b = status("b", GoalCategory.QUEST, 5, List.of());

		List<RankedGoal> ranked = ranker.rank(List.of(a, b), Set.of("a"), List.of("a", "b"));

		assertEquals(List.of("b"), ids(ranked), "a pinned-and-hidden goal must be absent, not merely unpinned: " + ranked);
	}

	@Test
	void bankUnknownWithNoGapsIsNeverReady()
	{
		Goal goal = new Goal("bu", GoalCategory.MILESTONE, "Bank Unknown Goal", "https://x", 5, 1);
		GoalStatus bankUnknown = new GoalStatus(goal, List.of(), true, true, List.of());
		GoalStatus ready = status("ready", GoalCategory.QUEST, 5, List.of());

		List<RankedGoal> ranked = ranker.rank(List.of(bankUnknown, ready), Set.of(), List.of());

		// Same score (both zero gaps, same priority) but "ready" must sort first: a bank-unknown
		// status is excluded from the ready group even though its own gap list is empty.
		assertEquals(List.of("ready", "bu"), ids(ranked));
	}

	@Test
	void tiesBrokenByPriorityThenName()
	{
		// Same score-driving inputs (no gaps -> score == priority) but different priority/name.
		GoalStatus zebra = status("zebra", GoalCategory.QUEST, 5, List.of());
		GoalStatus apple = status("apple", GoalCategory.QUEST, 5, List.of());
		GoalStatus higherPriority = status("higher", GoalCategory.QUEST, 8, List.of());

		List<RankedGoal> ranked = ranker.rank(List.of(zebra, apple, higherPriority), Set.of(), List.of());

		assertEquals(List.of("higher", "apple", "zebra"), ids(ranked));
	}

	// --- Task 42: later-stage penalty (spec ruling 27). ---

	@Test
	void goalMoreThanOneStageAboveAccountStageIsPenalisedAndMarkedLater()
	{
		GoalStatus stageFour = status("boss", GoalCategory.BOSS, 5, 4, List.of());

		List<RankedGoal> ranked = ranker.rank(List.of(stageFour), Set.of(), List.of(), 2);

		RankedGoal r = ranked.get(0);
		assertTrue(r.isLater(), "stage 4 goal is more than one stage above account stage 2");
		assertEquals(5.0 * 0.05, r.getScore(), 1e-9);
	}

	@Test
	void laterGoalNeverEntersTheReadyTierEvenWhenItsGapsAreEmpty()
	{
		// Ready (no gaps) but stage 4, more than one stage above account stage 2: score 5 * 1 * 0.05 = 0.25.
		GoalStatus readyButLater = status("later", GoalCategory.BOSS, 5, 4, List.of());
		// Not ready (one gap) but current stage 2: score 9 * 1/(1+1) = 4.5 - higher priority, lower score than
		// readyButLater's unpenalised score would be, but readyButLater must still rank last.
		GoalStatus currentStageWithGaps = status("current", GoalCategory.QUEST, 9, 2, List.of(new CombatLevelGap(1, 2, false)));

		List<RankedGoal> ranked = ranker.rank(List.of(readyButLater, currentStageWithGaps), Set.of(), List.of(), 2);

		assertEquals(List.of("current", "later"), ids(ranked), "a later goal must never outrank a stage-appropriate goal: " + ids(ranked));
		assertEquals(9.0 * 0.5, ranked.get(0).getScore(), 1e-9);
		assertEquals(5.0 * 0.05, ranked.get(1).getScore(), 1e-9);
		assertTrue(ranked.get(1).isLater());
	}

	@Test
	void goalExactlyOneStageAboveAccountStageIsNotLater()
	{
		GoalStatus stageThree = status("m", GoalCategory.MILESTONE, 5, 3, List.of());

		List<RankedGoal> ranked = ranker.rank(List.of(stageThree), Set.of(), List.of(), 2);

		RankedGoal r = ranked.get(0);
		assertFalse(r.isLater());
		assertEquals(5.0, r.getScore(), 1e-9, "not later: score should be unpenalised");
	}

	@Test
	void goalAtOrBelowAccountStageIsNotLater()
	{
		GoalStatus sameStage = status("m", GoalCategory.MILESTONE, 5, 2, List.of());

		List<RankedGoal> ranked = ranker.rank(List.of(sameStage), Set.of(), List.of(), 2);

		assertFalse(ranked.get(0).isLater());
	}

	@Test
	void pinnedGoalIsNeverMarkedLaterEvenWhenFarAboveAccountStage()
	{
		GoalStatus stageFour = status("boss", GoalCategory.BOSS, 5, 4, List.of());

		List<RankedGoal> ranked = ranker.rank(List.of(stageFour), Set.of(), List.of("boss"), 1);

		RankedGoal r = ranked.get(0);
		assertTrue(r.isPinned());
		assertFalse(r.isLater(), "a pin is an explicit user override");
		assertEquals(5.0, r.getScore(), 1e-9, "pinned score is not later-penalised");
	}

	@Test
	void threeArgRankOverloadNeverMarksAnythingLater()
	{
		GoalStatus stageFour = status("boss", GoalCategory.BOSS, 5, 4, List.of());

		List<RankedGoal> ranked = ranker.rank(List.of(stageFour), Set.of(), List.of());

		assertFalse(ranked.get(0).isLater());
	}

	// --- Task 46: stage-proximity ordering within the ready/rest tiers. ---

	@Test
	void withinTheReadyTierAStageAppropriateGoalOutranksANextStageGoalEvenWithALowerScore()
	{
		// Both ready (no gaps). "nextStage" (stage 3) scores higher (priority 8) than "current" (stage
		// 2, priority 6), but must still sort after it - real-world case: Moons of Peril (stage 2,
		// same as the account) must outrank God Wars Dungeon (stage 3) even though GWD's score is higher.
		GoalStatus nextStage = status("nextStage", GoalCategory.BOSS, 8, 3, List.of());
		GoalStatus current = status("current", GoalCategory.BOSS, 6, 2, List.of());

		List<RankedGoal> ranked = ranker.rank(List.of(nextStage, current), Set.of(), List.of(), 2);

		assertEquals(List.of("current", "nextStage"), ids(ranked), "same-stage goal must sort before a next-stage one: " + ids(ranked));
	}

	@Test
	void withinTheRestTierAStageAppropriateGoalOutranksANextStageGoalEvenWithALowerScore()
	{
		// Neither ready (both have a gap), so both land in the rest tier.
		GoalStatus nextStage = status("nextStage", GoalCategory.BOSS, 8, 3, List.of(new CombatLevelGap(1, 2, false)));
		GoalStatus current = status("current", GoalCategory.BOSS, 6, 2, List.of(new CombatLevelGap(1, 2, false)));

		List<RankedGoal> ranked = ranker.rank(List.of(nextStage, current), Set.of(), List.of(), 2);

		assertEquals(List.of("current", "nextStage"), ids(ranked));
	}

	@Test
	void stageProximityOrderingLeavesScoreAsTheTieBreakWithinTheSameProximityGroup()
	{
		GoalStatus lowerScoreSameStage = status("low", GoalCategory.BOSS, 5, 2, List.of());
		GoalStatus higherScoreSameStage = status("high", GoalCategory.BOSS, 9, 2, List.of());

		List<RankedGoal> ranked = ranker.rank(List.of(lowerScoreSameStage, higherScoreSameStage), Set.of(), List.of(), 2);

		assertEquals(List.of("high", "low"), ids(ranked), "same stage-proximity group: falls back to score: " + ids(ranked));
	}

	@Test
	void laterTierOrderingIsUnaffectedByStageProximity()
	{
		// Both "later" (more than one stage above account stage 1): plain score ordering, unchanged.
		GoalStatus stageFour = status("four", GoalCategory.BOSS, 5, 4, List.of());
		GoalStatus stageThree = status("three", GoalCategory.BOSS, 9, 3, List.of());

		List<RankedGoal> ranked = ranker.rank(List.of(stageFour, stageThree), Set.of(), List.of(), 1);

		assertEquals(List.of("three", "four"), ids(ranked), "later tier: plain score ordering, not stage-proximity: " + ids(ranked));
	}

	private static SkillLevelGap skillGap(Skill skill, int have, int need, long xpDelta)
	{
		return new SkillLevelGap(skill, have, need, xpDelta, false, null, false);
	}

	private static GoalStatus status(String id, GoalCategory category, int priority, List<Gap> gaps)
	{
		return status(id, category, priority, 1, gaps);
	}

	private static GoalStatus status(String id, GoalCategory category, int priority, int stage, List<Gap> gaps)
	{
		Goal goal = new Goal(id, category, id, "https://x", priority, stage);
		return new GoalStatus(goal, gaps, gaps.isEmpty(), false, List.of());
	}

	private static List<String> ids(List<RankedGoal> ranked)
	{
		return ranked.stream().map(r -> r.getStatus().getGoal().getId()).collect(Collectors.toList());
	}
}
