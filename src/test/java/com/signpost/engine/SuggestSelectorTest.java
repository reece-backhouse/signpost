package com.signpost.engine;

import net.runelite.api.Skill;
import java.util.Map;
import com.signpost.engine.model.SkillLevelGap;
import com.signpost.engine.model.Route;
import com.signpost.engine.model.Goal;
import com.signpost.engine.model.GoalCategory;
import com.signpost.engine.model.GoalStatus;
import com.signpost.engine.model.PrerequisiteGap;
import com.signpost.engine.model.RankedGoal;
import com.signpost.kb.GearLadder;
import com.signpost.kb.KnowledgeBase;
import com.signpost.kb.MilestoneCategory;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SuggestSelector} picks the panel's three "Pick one" suggestions.
 * Every {@code ranked} fixture here is already in the order {@link Ranker} would
 * produce (pinned entries first).
 */
class SuggestSelectorTest
{
	private final SuggestSelector selector = new SuggestSelector();

	@Test
	void categoryDiversityBreaksEqualValueTiesButNeverDisplacesAMoreValuableGoal()
	{
		RankedGoal r1 = ranked("r1", GoalCategory.MILESTONE, 10, false);
		RankedGoal r2 = ranked("r2", GoalCategory.MILESTONE, 9, false);
		RankedGoal r3 = ranked("r3", GoalCategory.MILESTONE, 8, false);
		RankedGoal r4 = ranked("r4", GoalCategory.QUEST, 8, false);

		List<RankedGoal> picked = selector.pick3(List.of(r1, r2, r3, r4));

		assertEquals(List.of("r1", "r2", "r4"), ids(picked), "r3 (same category as r1/r2) should be skipped for r4: " + ids(picked));
		assertEquals(List.of("r1", "r2", "r3"), ids(selector.pick3(List.of(r1, r2, r3,
			ranked("lower-value", GoalCategory.QUEST, 7, false)))));
	}

	/** Every ranked goal "later" (e.g. every in-range goal hidden) must give zero picks, not an exception. */
	@Test
	void allLaterGoalsGiveNoPicks()
	{
		RankedGoal l1 = ranked("l1", GoalCategory.QUEST, 10, false, true);
		RankedGoal l2 = ranked("l2", GoalCategory.MILESTONE, 9, false, true);

		assertEquals(List.of(), selector.pick3(List.of(l1, l2)));
		assertEquals(List.of(), selector.pick3(List.of(l1, l2), null));
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

		assertEquals(List.of("r4"), ids(rest));
	}

	// --- a `later` goal is never picked, but still appears in `rest`. ---

	@Test
	void laterGoalIsSkippedForSlotsOneAndTwo()
	{
		RankedGoal later = ranked("later", GoalCategory.MILESTONE, 100, false, true);
		RankedGoal r1 = ranked("r1", GoalCategory.QUEST, 10, false);
		RankedGoal r2 = ranked("r2", GoalCategory.QUEST, 9, false);

		List<RankedGoal> picked = selector.pick3(List.of(later, r1, r2));

		assertEquals(List.of("r1", "r2"), ids(picked), "the later goal must be skipped despite its high score: " + ids(picked));
	}

	@Test
	void laterGoalIsSkippedForSlotThreeTooIncludingTheDifferentCategoryFallback()
	{
		RankedGoal r1 = ranked("r1", GoalCategory.MILESTONE, 10, false);
		RankedGoal r2 = ranked("r2", GoalCategory.MILESTONE, 9, false);
		RankedGoal laterQuest = ranked("laterQuest", GoalCategory.QUEST, 100, false, true);
		RankedGoal r3 = ranked("r3", GoalCategory.QUEST, 8, false);

		List<RankedGoal> picked = selector.pick3(List.of(r1, r2, laterQuest, r3));

		assertEquals(List.of("r1", "r2", "r3"), ids(picked), "laterQuest must be skipped even as the only different-category candidate ahead of r3: "
			+ ids(picked));
	}

	@Test
	void laterGoalStillAppearsInRest()
	{
		RankedGoal later = ranked("later", GoalCategory.MILESTONE, 100, false, true);
		RankedGoal r1 = ranked("r1", GoalCategory.QUEST, 10, false);
		RankedGoal r2 = ranked("r2", GoalCategory.QUEST, 9, false);
		RankedGoal r3 = ranked("r3", GoalCategory.QUEST, 8, false);
		List<RankedGoal> all = List.of(later, r1, r2, r3);

		List<RankedGoal> picked = selector.pick3(all);
		List<RankedGoal> rest = selector.rest(all, picked);

		assertEquals(List.of("later"), ids(rest), "a later goal is excluded from picked but still shows up in rest: " + ids(rest));
	}

	// --- a boss is never picked alongside one of its own drops. ---

	@Test
	void gearGoalSkippedWhenItsObtainedFromBossIsAlreadyPicked()
	{
		KnowledgeBase kb = new KbBuilder()
			.milestone("boss:gwd", MilestoneCategory.BOSS, "God Wars Dungeon", 8)
			.milestone("milestone:bandos", MilestoneCategory.GEAR, "Bandos armour", 7).ownedIf("Bandos chestplate", 1)
			.obtainedFrom("boss:gwd")
			.build();
		RankedGoal boss = ranked("boss:gwd", GoalCategory.BOSS, 10, false);
		RankedGoal bandos = ranked("milestone:bandos", GoalCategory.MILESTONE, 9, false);
		RankedGoal r3 = ranked("r3", GoalCategory.QUEST, 8, false);

		List<RankedGoal> picked = selector.pick3(List.of(boss, bandos, r3), kb);

		assertEquals(List.of("boss:gwd", "r3"), ids(picked), "bandos armour must be skipped: its own boss (gwd) is already picked: " + ids(picked));
	}

	@Test
	void bossGoalSkippedWhenOneOfItsDropsIsAlreadyPicked()
	{
		KnowledgeBase kb = new KbBuilder()
			.milestone("boss:gwd", MilestoneCategory.BOSS, "God Wars Dungeon", 8)
			.milestone("milestone:bandos", MilestoneCategory.GEAR, "Bandos armour", 7).ownedIf("Bandos chestplate", 1)
			.obtainedFrom("boss:gwd")
			.build();
		RankedGoal bandos = ranked("milestone:bandos", GoalCategory.MILESTONE, 10, false);
		RankedGoal boss = ranked("boss:gwd", GoalCategory.BOSS, 9, false);
		RankedGoal r3 = ranked("r3", GoalCategory.QUEST, 8, false);

		List<RankedGoal> picked = selector.pick3(List.of(bandos, boss, r3), kb);

		assertEquals(List.of("milestone:bandos", "r3"), ids(picked), "gwd must be skipped: its own drop (bandos) is already picked: " + ids(picked));
	}

	@Test
	void pairingRuleAppliesToSlotThreeToo()
	{
		KnowledgeBase kb = new KbBuilder()
			.milestone("boss:gwd", MilestoneCategory.BOSS, "God Wars Dungeon", 8)
			.milestone("milestone:bandos", MilestoneCategory.GEAR, "Bandos armour", 7).ownedIf("Bandos chestplate", 1)
			.obtainedFrom("boss:gwd")
			.build();
		RankedGoal r1 = ranked("r1", GoalCategory.QUEST, 10, false);
		RankedGoal bandos = ranked("milestone:bandos", GoalCategory.MILESTONE, 9, false);
		RankedGoal boss = ranked("boss:gwd", GoalCategory.BOSS, 8, false);
		RankedGoal r4 = ranked("r4", GoalCategory.QUEST, 7, false);

		// Slots 1/2 fill with r1 and bandos (different categories, so slot 3 goes via nextUnused, not
		// the different-category fallback); boss is next in rank order but must be skipped there too.
		List<RankedGoal> picked = selector.pick3(List.of(r1, bandos, boss, r4), kb);

		assertEquals(List.of("r1", "milestone:bandos", "r4"), ids(picked), "gwd must be skipped in slot 3 too - its drop is already picked: "
			+ ids(picked));
	}

	@Test
	void pairingRuleDoesNotApplyToPinnedGoals()
	{
		KnowledgeBase kb = new KbBuilder()
			.milestone("boss:gwd", MilestoneCategory.BOSS, "God Wars Dungeon", 8)
			.milestone("milestone:bandos", MilestoneCategory.GEAR, "Bandos armour", 7).ownedIf("Bandos chestplate", 1)
			.obtainedFrom("boss:gwd")
			.build();
		RankedGoal pinnedBoss = ranked("boss:gwd", GoalCategory.BOSS, 1, true);
		RankedGoal pinnedBandos = ranked("milestone:bandos", GoalCategory.MILESTONE, 1, true);
		RankedGoal r3 = ranked("r3", GoalCategory.QUEST, 20, false);

		List<RankedGoal> picked = selector.pick3(List.of(pinnedBoss, pinnedBandos, r3), kb);

		assertEquals(List.of("boss:gwd", "milestone:bandos", "r3"), ids(picked), "a pin is an explicit user override: " + ids(picked));
	}

	/** A skill target is its own category for the slot-three diversity rule. */
	@Test
	void skillTargetCountsAsItsOwnCategoryForSlotThree()
	{
		RankedGoal r1 = ranked("r1", GoalCategory.QUEST, 10, false);
		RankedGoal r2 = ranked("r2", GoalCategory.QUEST, 9, false);
		RankedGoal r3 = ranked("r3", GoalCategory.QUEST, 8, false);
		RankedGoal r4 = ranked("skill:HERBLORE:70", GoalCategory.SKILL_TARGET, 8, false);

		List<RankedGoal> picked = selector.pick3(List.of(r1, r2, r3, r4));

		assertEquals(List.of("r1", "r2", "skill:HERBLORE:70"), ids(picked), ids(picked).toString());
	}

	/** An uncovered skill target is never picked, even as the only other category; a covered one still is. */
	@Test
	void uncoveredSkillTargetIsNeverPickedButACoveredOneIs()
	{
		RankedGoal r1 = ranked("r1", GoalCategory.QUEST, 10, false);
		RankedGoal r2 = ranked("r2", GoalCategory.QUEST, 9, false);
		RankedGoal uncovered = skillTarget("skill:PRAYER:52", 8, false);
		RankedGoal r4 = ranked("r4", GoalCategory.QUEST, 7, false);

		assertEquals(List.of("r1", "r2", "r4"), ids(selector.pick3(List.of(r1, r2, uncovered, r4))));
		assertEquals(List.of("r1", "r2"), ids(selector.pick3(List.of(r1, r2, uncovered))), "nothing else eligible: two picks, not three");

		RankedGoal covered = skillTarget("skill:HERBLORE:70", 8, true);
		assertEquals(List.of("r1", "r2", "skill:HERBLORE:70"), ids(selector.pick3(List.of(r1, r2, covered, r4))));
	}

	@Test
	void hiddenOrLaterPrerequisiteCannotPromoteItsDependantIntoAutomaticPicks()
	{
		RankedGoal room = ranked("room", GoalCategory.MILESTONE, 1, false, true);
		RankedGoal box = dependant("box", GoalCategory.MILESTONE, "room", false);

		assertEquals(List.of(), selector.pick3(List.of(box)), "hidden prerequisite must not be bypassed");
		assertEquals(List.of(), selector.pick3(List.of(room, box)), "later prerequisite must not be bypassed");
		assertEquals(List.of("box"), ids(selector.rest(List.of(box), selector.pick3(List.of(box)))));
		assertEquals(List.of("box"), ids(selector.pick3(List.of(dependant("box", GoalCategory.MILESTONE, "room", true)))),
			"an explicit pin still overrides prerequisite filtering");
	}

	@Test
	void diversityCannotSkipAPrerequisiteToPickItsDifferentCategoryDependant()
	{
		RankedGoal first = ranked("first", GoalCategory.MILESTONE, 10, false);
		RankedGoal second = ranked("second", GoalCategory.MILESTONE, 9, false);
		RankedGoal prerequisite = ranked("prerequisite", GoalCategory.MILESTONE, 8, false);
		RankedGoal dependant = dependant("dependant", GoalCategory.BOSS, "prerequisite", false);

		assertEquals(List.of("first", "second", "prerequisite"),
			ids(selector.pick3(List.of(first, second, prerequisite, dependant))),
			"slot three must take the prerequisite rather than bypass it for diversity");
	}

	@Test
	void multiStepChainIsPickedInPrerequisiteOrder()
	{
		RankedGoal house = ranked("house", GoalCategory.MILESTONE, 1, false);
		RankedGoal garden = dependant("garden", GoalCategory.MILESTONE, "house", false);
		RankedGoal pool = dependant("pool", GoalCategory.MILESTONE, "garden", false);
		RankedGoal upgrade = dependant("upgrade", GoalCategory.MILESTONE, "pool", false);

		assertEquals(List.of("house", "garden", "pool"), ids(selector.pick3(List.of(house, garden, pool, upgrade))));
	}

	@Test
	void blockedUpgradeDoesNotReserveItsStyleBeforeAnEligibleUpgrade()
	{
		RankedGoal blocked = upgrade("blocked", "melee", "provider:blocked",
			List.of(new PrerequisiteGap("hidden", "Hidden prerequisite")));
		RankedGoal available = upgrade("available", "melee", "provider:available", List.of());

		assertEquals(List.of("available"), ids(selector.pick3(List.of(blocked, available))));
	}

	@Test
	void bossPairingDoesNotHideAnUnrelatedUpgradeOfTheSameStyle()
	{
		KnowledgeBase kb = new KbBuilder()
			.milestone("boss:gwd", MilestoneCategory.BOSS, "God Wars Dungeon", 8)
			.milestone("milestone:bandos", MilestoneCategory.GEAR, "Bandos armour", 7).obtainedFrom("boss:gwd")
			.milestone("milestone:defender", MilestoneCategory.GEAR, "Dragon defender", 6)
			.build();
		RankedGoal boss = ranked("boss:gwd", GoalCategory.BOSS, 10, false);
		RankedGoal drop = upgrade("bandos", "melee", "milestone:bandos", List.of());
		RankedGoal defender = upgrade("defender", "melee", "milestone:defender", List.of());

		assertEquals(List.of("boss:gwd", "defender"), ids(selector.pick3(List.of(boss, drop, defender), kb)));
	}

	@Test
	void gearUpgradesShareTheMilestoneCategoryForPickDiversity()
	{
		RankedGoal milestone = ranked("milestone", GoalCategory.MILESTONE, 10, false);
		RankedGoal melee = upgrade("melee", "melee", "provider:melee", List.of());
		RankedGoal ranged = upgrade("ranged", "ranged", "provider:ranged", List.of());
		RankedGoal quest = ranked("quest", GoalCategory.QUEST, 7, false);

		assertEquals(List.of("milestone", "melee", "quest"), ids(selector.pick3(List.of(milestone, melee, ranged, quest))));
	}

	private static RankedGoal upgrade(String id, String style, String provider, List<com.signpost.engine.model.Gap> gaps)
	{
		GearLadder.Rung rung = new GearLadder.Rung(id, 1, List.of(1), provider, false);
		Goal goal = new Goal(id, GoalCategory.GEAR_UPGRADE, id, "https://x", 7, 1,
			new Goal.Upgrade(style, "body", rung, null));
		return new RankedGoal(new GoalStatus(goal, gaps, gaps.isEmpty(), false, List.of()), 7, false, false);
	}

	private static RankedGoal dependant(String id, GoalCategory category, String prerequisite, boolean pinned)
	{
		Goal goal = new Goal(id, category, id, "https://x", 7, 1);
		GoalStatus status = new GoalStatus(goal, List.of(new PrerequisiteGap(prerequisite, prerequisite)), false, false, List.of());
		return new RankedGoal(status, 3.5, pinned, false);
	}

	private static RankedGoal skillTarget(String id, double score, boolean covered)
	{
		Goal goal = new Goal(id, GoalCategory.SKILL_TARGET, id, "https://x", 9, 1);
		Route route = new Route(List.of(), covered ? 0 : 1000, 0, Map.of());
		GoalStatus status = new GoalStatus(goal, List.of(new SkillLevelGap(Skill.PRAYER, 45, 52, 1000, false, null, false)),
			false, false, List.of(), List.of(), List.of(), route, score);
		return new RankedGoal(status, score, false, false);
	}

	private static RankedGoal ranked(String id, GoalCategory category, double score, boolean pinned)
	{
		return ranked(id, category, score, pinned, false);
	}

	private static RankedGoal ranked(String id, GoalCategory category, double score, boolean pinned, boolean later)
	{
		Goal goal = new Goal(id, category, id, "https://x", 5, 1);
		GoalStatus status = new GoalStatus(goal, List.of(), true, false, List.of());
		return new RankedGoal(status, score, pinned, later);
	}

	private static List<String> ids(List<RankedGoal> ranked)
	{
		return ranked.stream().map(r -> r.getStatus().getGoal().getId()).collect(Collectors.toList());
	}
}
