package com.signpost.engine;

import java.util.Map;
import net.runelite.api.Experience;
import com.signpost.engine.model.Route;
import com.signpost.engine.model.GoalRef;
import com.signpost.engine.model.CombatLevelGap;
import com.signpost.engine.model.DiaryTaskGap;
import com.signpost.engine.model.Gap;
import com.signpost.engine.model.Goal;
import com.signpost.engine.model.GoalCategory;
import com.signpost.engine.model.GoalStatus;
import com.signpost.engine.model.ItemGap;
import com.signpost.engine.model.PrerequisiteGap;
import com.signpost.engine.model.QuestPrereqGap;
import com.signpost.engine.model.RankedGoal;
import com.signpost.engine.model.SkillLevelGap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Ranker} orders {@link GoalStatus}es into {@link RankedGoal}s.
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
		// an ItemGap with have == null (bank not seen) counts as 0 unmet in the score,
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

	// --- later-stage penalty. ---

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

	// --- stage-proximity ordering within the ready/rest tiers. ---

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

	// Equal-score ties follow the account's current progression, not the oldest accessible content.

	@Test
	void equalValueGoalsPreferCurrentProgressionInsteadOfEarlierBosses()
	{
		// Both are ready and equally valuable; a stage-3 account should move on rather than
		// automatically preferring the earlier Moons grind.
		GoalStatus godWarsDungeon = status("godWarsDungeon", GoalCategory.BOSS, 8, 3, List.of());
		GoalStatus moonsOfPeril = status("moonsOfPeril", GoalCategory.BOSS, 8, 2, List.of());

		List<RankedGoal> ranked = ranker.rank(List.of(godWarsDungeon, moonsOfPeril), Set.of(), List.of(), 3);

		assertEquals(List.of("godWarsDungeon", "moonsOfPeril"), ids(ranked));
	}

	@Test
	void unequalScoresStillOutrankTheProgressionTieBreak()
	{
		GoalStatus higherScoreNextStage = status("higher", GoalCategory.BOSS, 9, 3, List.of());
		GoalStatus lowerScoreCurrentStage = status("lower", GoalCategory.BOSS, 5, 2, List.of());

		List<RankedGoal> ranked = ranker.rank(List.of(higherScoreNextStage, lowerScoreCurrentStage), Set.of(), List.of(), 3);

		assertEquals(List.of("higher", "lower"), ids(ranked), "score still wins over the stage tie-break: " + ids(ranked));
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

	@Test
	void prerequisiteChainsFollowDependenciesRatherThanAnyFixedScorePenalty()
	{
		GoalStatus house = status("house", GoalCategory.MILESTONE, 1,
			List.of(skillGap(Skill.CONSTRUCTION, 1, 99, 13_000_000)));
		GoalStatus garden = status("garden", GoalCategory.MILESTONE, 6, List.of(new PrerequisiteGap("house", "House")));
		GoalStatus pool = status("pool", GoalCategory.MILESTONE, 10, List.of(new PrerequisiteGap("garden", "Garden")));
		GoalStatus ring = status("ring", GoalCategory.MILESTONE, 9, List.of(new PrerequisiteGap("garden", "Garden")));
		GoalStatus unrelated = status("unrelated", GoalCategory.QUEST, 4, List.of(new CombatLevelGap(1, 2, false)));

		List<String> expected = List.of("unrelated", "house", "garden", "pool", "ring");
		assertEquals(expected, ids(ranker.rank(List.of(pool, ring, garden, house, unrelated), Set.of(), List.of())));
		assertEquals(expected, ids(ranker.rank(List.of(unrelated, house, garden, ring, pool), Set.of(), List.of())),
			"input order must not change the dependency order or unrelated score ordering");
	}

	@Test
	void dependencyOrderingPreservesExplicitPinOrderAndDoesNotResurrectHiddenPrerequisites()
	{
		GoalStatus room = status("room", GoalCategory.MILESTONE, 5, List.of(new ItemGap("Coins", 0, 200000, List.of(), false)));
		GoalStatus box = status("box", GoalCategory.MILESTONE, 7, List.of(new PrerequisiteGap("room", "Room")));
		GoalStatus unrelated = status("unrelated", GoalCategory.QUEST, 8, List.of());
		List<GoalStatus> statuses = List.of(room, box, unrelated);

		List<RankedGoal> pinned = ranker.rank(statuses, Set.of(), List.of("box", "unrelated", "room"));
		assertEquals(List.of("box", "unrelated", "room"), ids(pinned));
		assertTrue(pinned.stream().allMatch(RankedGoal::isPinned));
		assertEquals(List.of("box", "unrelated"), ids(ranker.rank(statuses, Set.of("room"), List.of("box", "room"))));
		assertEquals(List.of("unrelated", "box"), ids(ranker.rank(statuses, Set.of("room"), List.of())));
	}

	@Test
	void otherwiseIdenticalGoalsBreakTiesByIdRegardlessOfInputOrder()
	{
		GoalStatus a = new GoalStatus(new Goal("a", GoalCategory.MILESTONE, "Same name", "https://x", 6, 1),
			List.of(), true, false, List.of());
		GoalStatus b = new GoalStatus(new Goal("b", GoalCategory.MILESTONE, "Same name", "https://x", 6, 1),
			List.of(), true, false, List.of());

		assertEquals(List.of("a", "b"), ids(ranker.rank(List.of(b, a), Set.of(), List.of())));
		assertEquals(List.of("a", "b"), ids(ranker.rank(List.of(a, b), Set.of(), List.of())));
	}

	private static SkillLevelGap skillGap(Skill skill, int have, int need, long xpDelta)
	{
		return new SkillLevelGap(skill, have, need, xpDelta, false, null, false);
	}

	// --- skill targets. ---

	@Test
	void uncoveredSkillTargetScoresAsItsParentAndSortsDirectlyBelowIt()
	{
		SkillLevelGap gap = new SkillLevelGap(Skill.HERBLORE, 61, 70, 500_000, false, null, false);
		GoalStatus parent = status("quest:1", GoalCategory.QUEST, 9, List.of(gap, new CombatLevelGap(1, 2, false)));
		Route shortRoute = new Route(List.of(), 400_000, Experience.getXpForLevel(61), Map.of());
		GoalStatus target = new GoalStatus(new Goal("skill:HERBLORE:70", GoalCategory.SKILL_TARGET, "70 Herblore", "https://x", 9, 1),
			List.of(gap), false, false, List.of(), List.of(), List.of(new GoalRef("quest:1", "quest:1", 70)), shortRoute, Ranker.score(parent));
		GoalStatus between = status("quest:2", GoalCategory.QUEST, 9, List.of(gap)); // one gap: scores above the parent

		List<RankedGoal> ranked = ranker.rank(List.of(target, between, parent), Set.of(), List.of());

		assertEquals(List.of("quest:2", "quest:1", "skill:HERBLORE:70"), ids(ranked));
		assertEquals(ranked.get(1).getScore(), ranked.get(2).getScore(), 1e-9, "uncovered target scores exactly as its parent");
	}

	@Test
	void bankCoveredSkillTargetScoresAsIfReadyAndOutranksAGoalWithTheSameGapButNoRoute()
	{
		SkillLevelGap gap = new SkillLevelGap(Skill.HERBLORE, 61, 70, 500_000, false, null, false);
		Goal targetGoal = new Goal("skill:HERBLORE:70", GoalCategory.SKILL_TARGET, "70 Herblore", "https://x", 9, 3);
		Route coveredRoute = new Route(List.of(), 0, Experience.getXpForLevel(70), Map.of());
		GoalStatus covered = new GoalStatus(targetGoal, List.of(gap), false, false, List.of(), List.of(),
			List.of(new GoalRef("quest:1", "Parent", 70)), coveredRoute, 4.5);
		GoalStatus plain = status("quest:1", GoalCategory.QUEST, 9, List.of(gap));

		// Account stage 2: the stage-3 target is one stage ahead, but a bank-covered target counts as
		// stage-appropriate, so it is not sorted behind the stage-1 quest.
		List<RankedGoal> ranked = ranker.rank(List.of(plain, covered), Set.of(), List.of(), 2);

		assertEquals(List.of("skill:HERBLORE:70", "quest:1"), ids(ranked));
		assertEquals(9.0, ranked.get(0).getScore(), 1e-9, "closeness treated as 1.0 when the bank covers the route");
		assertEquals(RankedGoal.Tier.REST, ranked.get(0).getTier(), "training is never the ready tier");
	}

	// --- quest fan-out. ---

	@Test
	void questUnblockingOtherGoalsOutranksAnOtherwiseEqualOneAndTheBonusCapsAtTwo()
	{
		Quest hub = Quest.WATERFALL_QUEST;
		String hubId = "quest:" + hub.getId();
		// Both quests: priority 5, one combat gap -> closeness 1/2.
		GoalStatus hubStatus = status(hubId, GoalCategory.QUEST, 5, List.of(new CombatLevelGap(1, 2, false)));
		GoalStatus plain = status("quest:1", GoalCategory.QUEST, 5, List.of(new CombatLevelGap(1, 2, false)));
		List<GoalStatus> all = new java.util.ArrayList<>(List.of(plain, hubStatus));
		for (int i = 0; i < 5; i++)
		{
			all.add(status("dep" + i, GoalCategory.MILESTONE, 1 + i, List.of(prereq(hub))));
		}

		List<RankedGoal> ranked = ranker.rank(all, Set.of(), List.of());

		RankedGoal hubRanked = ranked.stream().filter(r -> r.getStatus().getGoal().getId().equals(hubId)).findFirst().orElseThrow();
		RankedGoal plainRanked = ranked.stream().filter(r -> r.getStatus().getGoal().getId().equals("quest:1")).findFirst().orElseThrow();
		assertEquals((5 + 2) * 0.5, hubRanked.getScore(), 1e-9, "5 dependents x 0.5 = 2.5, capped at +2");
		assertEquals(5 * 0.5, plainRanked.getScore(), 1e-9);
		assertTrue(ids(ranked).indexOf(hubId) < ids(ranked).indexOf("quest:1"), ids(ranked).toString());
		// Unblocks are named in their own rank order (dep4 has the highest priority, so ranks first).
		assertEquals(List.of("dep4", "dep3", "dep2", "dep1", "dep0"), hubRanked.getUnblocks());
		assertEquals(List.of(), plainRanked.getUnblocks());
	}

	@Test
	void dependentsCountDistinctGoalsNotGapOccurrencesAndHiddenGoalsNeverCount()
	{
		Quest hub = Quest.WATERFALL_QUEST;
		String hubId = "quest:" + hub.getId();
		GoalStatus hubStatus = status(hubId, GoalCategory.QUEST, 5, List.of(new CombatLevelGap(1, 2, false)));
		// One diary needing the hub in two tasks counts once; a hidden goal counts zero.
		GoalStatus diary = status("diary:X", GoalCategory.DIARY, 4, List.of(
			new DiaryTaskGap(1, "t1", List.of(prereq(hub)), List.of()),
			new DiaryTaskGap(2, "t2", List.of(prereq(hub)), List.of())));
		GoalStatus hidden = status("hidden", GoalCategory.MILESTONE, 4, List.of(prereq(hub)));

		List<RankedGoal> ranked = ranker.rank(List.of(hubStatus, diary, hidden), Set.of("hidden"), List.of());

		RankedGoal hubRanked = ranked.stream().filter(r -> r.getStatus().getGoal().getId().equals(hubId)).findFirst().orElseThrow();
		assertEquals((5 + 0.5) * 0.5, hubRanked.getScore(), 1e-9, "one distinct dependent = +0.5");
		assertEquals(List.of("diary:X"), hubRanked.getUnblocks());
	}

	@Test
	void syntheticDependentsUseVisibleParentIdsAndNeverCountTheQuestItself()
	{
		Quest hub = Quest.WATERFALL_QUEST;
		String hubId = "quest:" + hub.getId();
		GoalStatus hubStatus = status(hubId, GoalCategory.QUEST, 5, List.of());
		GoalStatus parent = status("parent", GoalCategory.MILESTONE, 4, List.of(prereq(hub)));
		GoalStatus hidden = status("hidden", GoalCategory.MILESTONE, 4, List.of());
		GoalStatus target = new GoalStatus(new Goal("skill:ATTACK:40", GoalCategory.SKILL_TARGET,
			"40 Attack", "https://x", 4, 1), List.of(new DiaryTaskGap(1, "nested",
			List.of(prereq(hub)), List.of())), false, false, List.of(), List.of(),
			List.of(new GoalRef("parent", "parent", 40), new GoalRef("hidden", "hidden", 40)), null, 0);
		List<RankedGoal> ranked = ranker.rank(List.of(hubStatus, parent, hidden, target), Set.of("hidden"),
			List.of(), 4, Map.of(hubId, Set.of(hubId, "parent", "hidden")));
		RankedGoal result = ranked.stream().filter(r -> r.getStatus().getGoal().getId().equals(hubId)).findFirst().orElseThrow();
		assertEquals(5.5, result.getScore(), 1e-9);
		assertEquals(List.of("parent"), result.getUnblocks());
	}

	private static QuestPrereqGap prereq(Quest quest)
	{
		return new QuestPrereqGap(quest, QuestState.NOT_STARTED, true, false, "https://x");
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
