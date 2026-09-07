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
			new CombatLevelGap(50, 60),
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
		GoalStatus low = status("low", GoalCategory.DIARY, 1, List.of(new CombatLevelGap(1, 99)));

		List<RankedGoal> ranked = ranker.rank(List.of(high, low), Set.of(), List.of("low", "high"));

		assertEquals(List.of("low", "high"), ids(ranked));
		assertTrue(ranked.get(0).isPinned());
		assertTrue(ranked.get(1).isPinned());
	}

	@Test
	void readyGoalsComeBeforeGoalsWithGaps()
	{
		GoalStatus ready = status("ready", GoalCategory.QUEST, 1, List.of());
		GoalStatus gapped = status("gapped", GoalCategory.QUEST, 10, List.of(new CombatLevelGap(1, 2)));

		List<RankedGoal> ranked = ranker.rank(List.of(gapped, ready), Set.of(), List.of());

		assertEquals(List.of("ready", "gapped"), ids(ranked));
	}

	@Test
	void bankUnknownWithNoGapsIsNeverReady()
	{
		Goal goal = new Goal("bu", GoalCategory.MILESTONE, "Bank Unknown Goal", "https://x", 5);
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

	private static SkillLevelGap skillGap(Skill skill, int have, int need, long xpDelta)
	{
		return new SkillLevelGap(skill, have, need, xpDelta, false, null);
	}

	private static GoalStatus status(String id, GoalCategory category, int priority, List<Gap> gaps)
	{
		Goal goal = new Goal(id, category, id, "https://x", priority);
		return new GoalStatus(goal, gaps, gaps.isEmpty(), false, List.of());
	}

	private static List<String> ids(List<RankedGoal> ranked)
	{
		return ranked.stream().map(r -> r.getStatus().getGoal().getId()).collect(Collectors.toList());
	}
}
