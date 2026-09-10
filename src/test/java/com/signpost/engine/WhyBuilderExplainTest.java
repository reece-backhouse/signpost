package com.signpost.engine;

import com.signpost.engine.model.CombatLevelGap;
import com.signpost.engine.model.DiaryTaskGap;
import com.signpost.engine.model.Gap;
import com.signpost.engine.model.GearGap;
import com.signpost.engine.model.Goal;
import com.signpost.engine.model.GoalCategory;
import com.signpost.engine.model.GoalRef;
import com.signpost.engine.model.GoalStatus;
import com.signpost.engine.model.ItemGap;
import com.signpost.engine.model.Met;
import com.signpost.engine.model.QuestPrereqGap;
import com.signpost.engine.model.QuestXp;
import com.signpost.engine.model.RankedGoal;
import com.signpost.engine.model.Route;
import com.signpost.engine.model.SkillLevelGap;
import com.signpost.kb.KnowledgeBase;
import com.signpost.kb.MilestoneCategory;
import com.signpost.kb.OwnedItem;
import com.signpost.snapshot.Snapshot;
import java.util.List;
import java.util.Map;
import net.runelite.api.Experience;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link WhyBuilder#explain} - the multi-line "Why?" behind a suggestion. */
class WhyBuilderExplainTest
{
	private final WhyBuilder whyBuilder = new WhyBuilder();
	private final KnowledgeBase kb = new KbBuilder().build();
	private final Snapshot snap = new SnapshotBuilder().build();

	@Test
	void skillTargetListsUpToThreeParentsThenTheBankCoverage()
	{
		Route covered = new Route(List.of(), 0, Experience.getXpForLevel(70), Map.of());
		GoalStatus status = skillTarget(
			List.of(new GoalRef("quest:137", "Song of the Elves", 70), new GoalRef("quest:1", "Second", 70),
				new GoalRef("quest:2", "Third", 74), new GoalRef("quest:3", "Fourth", 78)),
			covered, 1);

		assertEquals(List.of(
			"Needed for Song of the Elves (70 Herblore)",
			"Needed for Second (70 Herblore)",
			"Needed for Third (74 Herblore)",
			"Bank covers 61-70"), whyBuilder.explain(rank(status), kb, snap, 1));
	}

	@Test
	void skillTargetShortRouteSaysHowFarTheBankGetsAndTheShortfall()
	{
		long reached = Experience.getXpForLevel(68) + 10;
		Route partial = new Route(List.of(), Experience.getXpForLevel(70) - reached, reached, Map.of());
		Route empty = new Route(List.of(), Experience.getXpForLevel(70) - Experience.getXpForLevel(61), Experience.getXpForLevel(61), Map.of());
		GoalStatus some = skillTarget(List.of(new GoalRef("quest:137", "Song of the Elves", 70)), partial, 1);
		GoalStatus none = skillTarget(List.of(new GoalRef("quest:137", "Song of the Elves", 70)), empty, 1);

		assertEquals("Bank covers 61-68; short " + (Experience.getXpForLevel(70) - reached) + " xp", whyBuilder.explain(rank(some), kb, snap, 1).get(1));
		assertEquals("Bank covers nothing; short " + (Experience.getXpForLevel(70) - Experience.getXpForLevel(61)) + " xp",
			whyBuilder.explain(rank(none), kb, snap, 1).get(1));
	}


	@Test
	void statsMetListsEntryAndRecommendedSkillsUpToFive()
	{
		List<Met> met = List.of(
			new Met(Met.Kind.SKILL, "Slayer 50 (have 60)"),
			new Met(Met.Kind.QUEST, "Biohazard"),
			new Met(Met.Kind.RECOMMENDED_SKILL, "Attack 70 (have 92)"),
			new Met(Met.Kind.RECOMMENDED_SKILL, "Strength 70 (have 92)"),
			new Met(Met.Kind.RECOMMENDED_SKILL, "Defence 70 (have 78)"),
			new Met(Met.Kind.RECOMMENDED_SKILL, "Hitpoints 70 (have 88)"),
			new Met(Met.Kind.RECOMMENDED_SKILL, "Ranged 70 (have 72)"));
		GoalStatus status = status("boss:test", GoalCategory.BOSS, 1, List.of(), met, true);

		assertEquals("Stats met: Slayer 50, Attack 70, Strength 70, Defence 70, Hitpoints 70…", whyBuilder.explain(rank(status), kb, snap, 1).get(0));
	}


	@Test
	void stageLineOnlyWhenTheGoalIsAboveTheAccountAndReadyLineOnlyWhenReady()
	{
		GoalStatus stageThree = status("boss:test", GoalCategory.BOSS, 3, List.of(new CombatLevelGap(90, 100, false)), List.of(), false);
		GoalStatus stageTwoReady = status("boss:ready", GoalCategory.BOSS, 2, List.of(), List.of(), true);

		assertEquals(List.of("Missing: combat 100 (have 90)", "Stage 3 goal, you are stage 2"), whyBuilder.explain(rank(stageThree), kb, snap, 2));
		assertEquals(List.of("Ready now"), whyBuilder.explain(rank(stageTwoReady), kb, snap, 2));
		assertEquals(List.of("Missing: combat 100 (have 90)"), whyBuilder.explain(rank(stageThree), kb, snap, 3), "not above the account: no stage line");
	}

	/** A goal whose item requirements can't be checked because the bank was never seen says so. */
	@Test
	void bankUnknownGoalGetsTheMaterialsAssumedMissingNote()
	{
		Goal goal = new Goal("quest:1", GoalCategory.QUEST, "quest:1", "https://x", 5, 1);
		List<Gap> gaps = List.of(new ItemGap("Rope", null, 1, List.of(), false));
		GoalStatus status = new GoalStatus(goal, gaps, false, true, List.of(), List.of(), List.of(), null, 0);

		assertEquals(List.of("Missing: Rope", "Bank not seen yet, materials assumed missing"), whyBuilder.explain(rank(status), kb, snap, 1));
	}

	@Test
	void laterGoalWithNoGapsIsNotReadyNow()
	{
		GoalStatus status = status("boss:test", GoalCategory.BOSS, 4, List.of(), List.of(), true);

		assertEquals(List.of("Stage 4 goal, you are stage 1"), whyBuilder.explain(new RankedGoal(status, 0.1, false, true), kb, snap, 1));
	}

	@Test
	void everyLineIsAtMostNinetyCharactersWithNoTrailingPeriod()
	{
		List<Gap> gaps = List.of(
			new ItemGap("A".repeat(38), 0, 1, List.of(), false),
			new ItemGap("B".repeat(38), 0, 1, List.of(), false),
			new ItemGap("C".repeat(38), 0, 1, List.of(), false));
		GoalStatus status = status("quest:1", GoalCategory.QUEST, 1, gaps, List.of(), false);

		for (String line : whyBuilder.explain(rank(status), kb, snap, 1))
		{
			assertTrue(line.length() <= 90, line.length() + ": " + line);
			assertTrue(!line.endsWith("."), line);
		}
		assertEquals("Missing: " + "A".repeat(38) + ", " + "B".repeat(38) + "…", whyBuilder.explain(rank(status), kb, snap, 1).get(0));
	}

	@Test
	void unblocksLineNamesTheTopThreeDependentsInRankOrderThenCountsTheRest()
	{
		GoalStatus quest = status("quest:1", GoalCategory.QUEST, 1, List.of(new CombatLevelGap(3, 40, false)), List.of(), false);
		RankedGoal five = new RankedGoal(quest, 1.0, false, false,
			List.of("Desert Treasure I", "Lunar Diplomacy", "Fremennik Hard Diary", "Barrows", "Vorkath"));
		RankedGoal two = new RankedGoal(quest, 1.0, false, false, List.of("Desert Treasure I", "Lunar Diplomacy"));

		assertEquals(List.of("Unblocks: Desert Treasure I, Lunar Diplomacy, Fremennik Hard Diary +2 more", "Missing: combat 40 (have 3)"),
			whyBuilder.explain(five, kb, snap, 1));
		assertEquals("Unblocks: Desert Treasure I, Lunar Diplomacy", whyBuilder.explain(two, kb, snap, 1).get(0));
		assertEquals(List.of("Missing: combat 40 (have 3)"), whyBuilder.explain(rank(quest), kb, snap, 1), "no dependents: no line");
	}

	@Test
	void curatedPriorityReasonIsItsOwnFirstLine()
	{
		KnowledgeBase withReason = new KbBuilder().priorityOverride("quest:1", 9, "Unlocks the route to Chivalry and Piety").build();
		GoalStatus quest = status("quest:1", GoalCategory.QUEST, 1, List.of(new CombatLevelGap(3, 40, false)), List.of(), false);

		assertEquals(List.of("Unlocks the route to Chivalry and Piety", "Missing: combat 40 (have 3)"), whyBuilder.explain(rank(quest), withReason, snap, 1));
	}

	@Test
	void skillTargetNamesTheQuestsWhoseXpItCountsLargestFirstUpToThree()
	{
		List<QuestXp> quests = List.of(
			new QuestXp("quest:2", "The Grand Tree", "https://x", QuestState.NOT_STARTED, 18_400),
			new QuestXp("quest:158", "Waterfall Quest", "https://x", QuestState.NOT_STARTED, 13_750),
			new QuestXp("quest:1", "Fight Arena", "https://x", QuestState.NOT_STARTED, 12_175),
			new QuestXp("quest:150", "Tree Gnome Village", "https://x", QuestState.NOT_STARTED, 11_450));
		Goal goal = new Goal("skill:ATTACK:40", GoalCategory.SKILL_TARGET, "40 Attack", "https://x", 5, 1);
		SkillLevelGap gap = new SkillLevelGap(Skill.ATTACK, 1, 40, 1_000, false, null, false);
		Route route = new Route(List.of(), 1_000, 36_224, Map.of());
		GoalStatus target = new GoalStatus(goal, List.of(gap), false, false, List.of(), List.of(),
			List.of(new GoalRef("quest:0", "Animal Magnetism", 40)), route, 1.0, quests);

		List<String> lines = whyBuilder.explain(rank(target), kb, snap, 1);

		assertEquals("Needed for Animal Magnetism (40 Attack)", lines.get(0));
		assertTrue(lines.get(1).contains("55,775 Attack xp"));
		assertTrue(lines.get(1).contains("The Grand Tree, Waterfall Quest, Fight Arena +1 more"));
		assertFalse(lines.get(1).contains("Tree Gnome Village"));
		assertEquals("Bank covers 1-39; short 1000 xp", lines.get(2));

		List<QuestXp> shortNames = List.of(
			new QuestXp("quest:1", "A", "https://x", QuestState.NOT_STARTED, 40),
			new QuestXp("quest:2", "B", "https://x", QuestState.NOT_STARTED, 30),
			new QuestXp("quest:3", "C", "https://x", QuestState.NOT_STARTED, 20),
			new QuestXp("quest:4", "D", "https://x", QuestState.NOT_STARTED, 10));
		GoalStatus four = new GoalStatus(goal, List.of(gap), false, false, List.of(), List.of(), List.of(), route, 1.0, shortNames);
		assertEquals("Quests you can do now give 100 Attack xp: A, B, C +1 more", whyBuilder.explain(rank(four), kb, snap, 1).get(0));
		GoalStatus two = new GoalStatus(goal, List.of(gap), false, false, List.of(), List.of(), List.of(), route, 1.0, shortNames.subList(0, 2));
		assertEquals("Quests you can do now give 70 Attack xp: A, B", whyBuilder.explain(rank(two), kb, snap, 1).get(0));
	}

	private static GoalStatus skillTarget(List<GoalRef> parents, Route route, int stage)
	{
		Goal goal = new Goal("skill:HERBLORE:70", GoalCategory.SKILL_TARGET, "70 Herblore", "https://x", 9, stage);
		SkillLevelGap gap = new SkillLevelGap(Skill.HERBLORE, 61, 70, Experience.getXpForLevel(70) - Experience.getXpForLevel(61), false, null, false);
		return new GoalStatus(goal, List.of(gap), false, false, List.of(), List.of(), parents, route, 1.0);
	}

	private static GoalStatus status(String id, GoalCategory category, int stage, List<Gap> gaps, List<Met> met, boolean ready)
	{
		Goal goal = new Goal(id, category, id, "https://x", 5, stage);
		return new GoalStatus(goal, gaps, ready, false, List.of(), met, List.of(), null, 0);
	}

	private static RankedGoal rank(GoalStatus status)
	{
		return new RankedGoal(status, 1.0, false, false);
	}
}
