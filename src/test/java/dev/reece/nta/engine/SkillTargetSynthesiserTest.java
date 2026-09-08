package dev.reece.nta.engine;

import dev.reece.nta.engine.model.GoalCategory;
import dev.reece.nta.engine.model.GoalRef;
import dev.reece.nta.engine.model.GoalStatus;
import dev.reece.nta.engine.model.SkillLevelGap;
import dev.reece.nta.kb.KnowledgeBase;
import dev.reece.nta.kb.MilestoneCategory;
import dev.reece.nta.snapshot.DiaryTier;
import dev.reece.nta.snapshot.Snapshot;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.runelite.api.Experience;
import net.runelite.api.Skill;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Task 51 (spec ruling 28): {@link SkillTargetSynthesiser} turns the skill gaps of upcoming goals into "&lt;level&gt; &lt;Skill&gt;" goals. */
class SkillTargetSynthesiserTest
{
	private final GapEngine gapEngine = new GapEngine(new BoostTable());

	@Test
	void lowestUnmetLevelWinsWithPriorityAndStageFromTheParentsNeedingThatLevelAndEveryParentListed()
	{
		// quest:0 priority 9 (stage 3) needs Herblore 78; quest:9 priority 7 (stage 2) and quest:14
		// priority 3 (stage 1) need Herblore 70. The best-scoring 70-parent (quest:9) lends its
		// priority, stage and score; quest:0 is still listed as a parent.
		KnowledgeBase kb = new KbBuilder()
			.quest(0, "Animal Magnetism").skill(Skill.HERBLORE, 78)
			.quest(9, "Biohazard").skill(Skill.HERBLORE, 70)
			.quest(14, "Clock Tower").skill(Skill.HERBLORE, 70)
			.priorityOverride("quest:0", 9)
			.priorityOverride("quest:9", 7)
			.priorityOverride("quest:14", 3)
			.build();
		Snapshot snapshot = new SnapshotBuilder().skill(Skill.HERBLORE, 61).build();

		List<GoalStatus> targets = SkillTargetSynthesiser.synthesise(gapEngine.evaluate(snapshot, kb), Set.of(), 4, snapshot, kb);

		assertEquals(1, targets.size(), ids(targets).toString());
		GoalStatus target = targets.get(0);
		assertEquals("skill:HERBLORE:70", target.getGoal().getId());
		assertEquals("70 Herblore", target.getGoal().getName());
		assertEquals(GoalCategory.SKILL_TARGET, target.getGoal().getCategory());
		assertEquals("https://oldschool.runescape.wiki/w/Herblore_training", target.getGoal().getWikiUrl());
		assertEquals(7, target.getGoal().getPriority(), "priority of the best parent needing level 70");
		assertEquals(2, target.getGoal().getStage(), "stage of the best parent needing level 70");
		GoalStatus biohazard = gapEngine.evaluate(snapshot, kb).stream().filter(s -> s.getGoal().getId().equals("quest:9")).findFirst().orElseThrow();
		assertEquals(Ranker.score(biohazard), target.getParentScore(), 1e-9, "score cap = the best parent's score");
		assertFalse(target.isReady(), "training is never ready now");
		assertEquals(List.of(new GoalRef("quest:9", "Biohazard", 70), new GoalRef("quest:14", "Clock Tower", 70),
			new GoalRef("quest:0", "Animal Magnetism", 78)), target.getParents());

		assertEquals(1, target.getGaps().size());
		SkillLevelGap gap = (SkillLevelGap) target.getGaps().get(0);
		assertEquals(Skill.HERBLORE, gap.getSkill());
		assertEquals(61, gap.getHave());
		assertEquals(70, gap.getNeed());
		assertEquals(Experience.getXpForLevel(70) - Experience.getXpForLevel(61), gap.getXpDelta());
	}

	@Test
	void hiddenAndLaterParentsAreExcludedAndNoTargetWhenNoSkillGaps()
	{
		KnowledgeBase kb = new KbBuilder()
			.quest(0, "Animal Magnetism").skill(Skill.HERBLORE, 70)
			.quest(9, "Biohazard").skill(Skill.MINING, 50).item("Rope", 1)
			.milestone("boss:late", MilestoneCategory.BOSS, "Late Boss", 10).stage(4).skill(Skill.PRAYER, 77)
			.build();
		Snapshot snapshot = new SnapshotBuilder().bankItem(954, "Rope", 1).build();

		// Account stage 1: the stage-4 boss is "later"; quest:0 is hidden.
		List<GoalStatus> targets = SkillTargetSynthesiser.synthesise(gapEngine.evaluate(snapshot, kb), Set.of("quest:0"), 1, snapshot, kb);

		assertEquals(List.of("skill:MINING:50"), ids(targets));
	}

	@Test
	void diaryTaskAndRecommendedSkillGapsCountAsParents()
	{
		KnowledgeBase kb = new KbBuilder()
			.diary(DiaryTier.VARROCK_EASY).task(1, "Mine some iron").skill(Skill.MINING, 15)
			.milestone("boss:test", MilestoneCategory.BOSS, "Test Boss", 8).recommendedSkill(Skill.RANGED, 75)
			.build();
		Snapshot snapshot = new SnapshotBuilder().build();

		List<GoalStatus> targets = SkillTargetSynthesiser.synthesise(gapEngine.evaluate(snapshot, kb), Set.of(), 4, snapshot, kb);

		// Targets come out in Skill enum order (Ranged precedes Mining).
		assertEquals(List.of("skill:RANGED:75", "skill:MINING:15"), ids(targets));
		assertEquals(List.of(new GoalRef("boss:test", "Test Boss", 75)), targets.get(0).getParents());
		assertEquals(List.of(new GoalRef("diary:VARROCK_EASY", "Varrock Easy Diary", 15)), targets.get(1).getParents());
	}

	@Test
	void bankCoveredWhenTheRouteFromTheBankReachesTheTarget()
	{
		KnowledgeBase kb = new KbBuilder()
			.quest(0, "Animal Magnetism").skill(Skill.HERBLORE, 3)
			.method(Skill.HERBLORE, "Prayer potion(3)", 1, 5).material(10, 1)
			.build();
		Snapshot rich = new SnapshotBuilder().bankItem(10, "Ranarr weed", 10_000).build();
		Snapshot poor = new SnapshotBuilder().bankItem(10, "Ranarr weed", 3).build();

		GoalStatus covered = SkillTargetSynthesiser.synthesise(gapEngine.evaluate(rich, kb), Set.of(), 4, rich, kb).get(0);
		GoalStatus uncovered = SkillTargetSynthesiser.synthesise(gapEngine.evaluate(poor, kb), Set.of(), 4, poor, kb).get(0);

		assertTrue(covered.isBankCovered());
		assertNotNull(covered.getBankRoute());
		assertEquals(0, covered.getBankRoute().getUncoveredXp());
		assertFalse(uncovered.isBankCovered());
		assertEquals(Experience.getXpForLevel(3) - 15, uncovered.getBankRoute().getUncoveredXp(), "3 potions at 5 xp each");
	}

	private static List<String> ids(List<GoalStatus> statuses)
	{
		return statuses.stream().map(s -> s.getGoal().getId()).collect(Collectors.toList());
	}
}
