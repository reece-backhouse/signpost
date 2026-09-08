package dev.reece.nta.engine;

import dev.reece.nta.engine.model.CombatLevelGap;
import dev.reece.nta.engine.model.DiaryTaskGap;
import dev.reece.nta.engine.model.Gap;
import dev.reece.nta.engine.model.GearGap;
import dev.reece.nta.engine.model.Goal;
import dev.reece.nta.engine.model.GoalCategory;
import dev.reece.nta.engine.model.GoalRef;
import dev.reece.nta.engine.model.GoalStatus;
import dev.reece.nta.engine.model.ItemGap;
import dev.reece.nta.engine.model.Met;
import dev.reece.nta.engine.model.QuestPrereqGap;
import dev.reece.nta.engine.model.RankedGoal;
import dev.reece.nta.engine.model.Route;
import dev.reece.nta.engine.model.SkillLevelGap;
import dev.reece.nta.kb.KnowledgeBase;
import dev.reece.nta.kb.MilestoneCategory;
import dev.reece.nta.kb.OwnedItem;
import dev.reece.nta.snapshot.Snapshot;
import java.util.List;
import java.util.Map;
import net.runelite.api.Experience;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Task 51 (spec ruling 29): {@link WhyBuilder#explain} - the multi-line "Why?" behind a suggestion. */
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
	void recommendedGearLineCountsOwnedAgainstTheProfileAndNamesUpToFourWithinNinetyCharacters()
	{
		KnowledgeBase gearKb = new KbBuilder()
			.milestone("boss:test", MilestoneCategory.BOSS, "Test Boss", 8)
			.recommendedGear("Rune crossbow", 9185).recommendedGear("Diamond bolts (e)", 9243).recommendedGear("Karil's leathertop", 4736)
			.recommendedGear("Karil's leatherskirt", 4738).recommendedGear("Karil's coif", 4732).recommendedGear("Bandos chestplate", 11832)
			.recommendedGear("Barrows gloves", 7462)
			.build();
		List<Met> met = List.of(
			new Met(Met.Kind.RECOMMENDED_GEAR, "Rune crossbow", 5, 4),
			new Met(Met.Kind.RECOMMENDED_GEAR, "Diamond bolts (e)", 5, 4),
			new Met(Met.Kind.RECOMMENDED_GEAR, "Karil's leathertop", 5, 4),
			new Met(Met.Kind.RECOMMENDED_GEAR, "Karil's leatherskirt", 5, 4),
			new Met(Met.Kind.RECOMMENDED_GEAR, "Barrows gloves", 5, 4));
		GoalStatus status = status("boss:test", GoalCategory.BOSS, 1, List.of(), met, true);

		List<String> lines = whyBuilder.explain(rank(status), gearKb, snap, 1);

		// Four names would run to 104 characters, so the 90-character cap drops the fourth.
		assertEquals("Meets 5 of 7 recommended gear: Rune crossbow, Diamond bolts (e), Karil's leathertop…", lines.get(0));
		assertEquals("Ready now", lines.get(1));
		assertEquals(2, lines.size(), lines.toString());
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
	void missingLineNamesUpToFourGapsWithHaveAndNeedAndCountsDiaryTasks()
	{
		Snapshot withQuest = new SnapshotBuilder().quest(Quest.PERILOUS_MOONS, QuestState.NOT_STARTED).build();
		List<Gap> gaps = List.of(
			new SkillLevelGap(Skill.PRAYER, 34, 43, 50_000, false, null, false),
			new GearGap(List.of(new OwnedItem("Rune crossbow", 9185), new OwnedItem("Karil's coif", 4732)), 1, 2, false),
			new QuestPrereqGap(Quest.PERILOUS_MOONS, QuestState.NOT_STARTED, true, false, "https://x"),
			new ItemGap("Rope", 0, 2, List.of(), false),
			new CombatLevelGap(90, 100, true));
		List<Met> met = List.of(new Met(Met.Kind.RECOMMENDED_GEAR, "Rune crossbow", 1, 2));
		GoalStatus status = status("boss:test", GoalCategory.BOSS, 1, gaps, met, false);

		assertEquals("Missing: Prayer 43 (have 34), Karil's coif, quest Perilous Moons, Rope ×2…", whyBuilder.explain(rank(status), kb, withQuest, 1).get(0));

		List<Gap> diaryGaps = List.of(
			new DiaryTaskGap(1, "one", List.of(new SkillLevelGap(Skill.MINING, 1, 15, 2_411, false, null, false)), List.of()),
			new DiaryTaskGap(2, "two", List.of(), List.of()),
			new CombatLevelGap(3, 40, false));
		GoalStatus diary = status("diary:VARROCK_EASY", GoalCategory.DIARY, 1, diaryGaps, List.of(), false);

		assertEquals("Missing: combat 40 (have 3), 2 diary tasks", whyBuilder.explain(rank(diary), kb, snap, 1).get(0));
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

	/** RL-011 AC4: a goal whose item requirements can't be checked because the bank was never seen says so. */
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
