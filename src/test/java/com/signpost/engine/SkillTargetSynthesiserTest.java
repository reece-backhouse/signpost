package com.signpost.engine;

import com.signpost.engine.model.GoalCategory;
import com.signpost.engine.model.GoalRef;
import com.signpost.engine.model.GoalStatus;
import com.signpost.engine.model.QuestXp;
import com.signpost.engine.model.SkillLevelGap;
import com.signpost.kb.KnowledgeBase;
import com.signpost.kb.MilestoneCategory;
import com.signpost.snapshot.DiaryTier;
import com.signpost.snapshot.Snapshot;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.runelite.api.Experience;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link SkillTargetSynthesiser} turns the skill gaps of upcoming goals into "&lt;level&gt; &lt;Skill&gt;" goals. */
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

	// --- quest reward xp covers skill targets. ---

	@Test
	void targetFullyCoveredByReadyQuestXpIsNotSynthesisedAndTheQuestUnblocksItsParents()
	{
		// 1 xp short of 40 Attack; Waterfall Quest (no requirements, so ready) gives 13,750 Attack xp.
		KnowledgeBase kb = new KbBuilder()
			.quest(158, "Waterfall Quest").rewardXp(Skill.ATTACK, 13_750)
			.quest(0, "Animal Magnetism").skill(Skill.ATTACK, 40)
			.build();
		Snapshot snapshot = new SnapshotBuilder().xp(Skill.ATTACK, Experience.getXpForLevel(40) - 1).build();

		SkillTargetSynthesiser.Synthesis synthesis = SkillTargetSynthesiser.run(gapEngine.evaluate(snapshot, kb), Set.of(), 4, snapshot, kb);

		assertEquals(List.of(), ids(synthesis.getTargets()), "covered by a quest you can do now: no grind target");
		assertEquals(Map.of("quest:158", Set.of("quest:0")), synthesis.getQuestUnblocks());
	}

	@Test
	void partialCoverageReducesTheGapAndListsTheQuestsLargestFirstWithQuestStepsLeadingTheRoute()
	{
		KnowledgeBase kb = new KbBuilder()
			.quest(150, "Tree Gnome Village").rewardXp(Skill.ATTACK, 11_450)
			.quest(158, "Waterfall Quest").rewardXp(Skill.ATTACK, 13_750).rewardXp(Skill.STRENGTH, 13_750)
			.quest(0, "Animal Magnetism").skill(Skill.ATTACK, 40)
			.build();
		Snapshot snapshot = new SnapshotBuilder().build();

		SkillTargetSynthesiser.Synthesis synthesis = SkillTargetSynthesiser.run(gapEngine.evaluate(snapshot, kb), Set.of(), 4, snapshot, kb);

		assertEquals(List.of("skill:ATTACK:40"), ids(synthesis.getTargets()));
		assertEquals(Map.of(), synthesis.getQuestUnblocks(), "partial coverage: the quests don't count as unblocking");
		GoalStatus target = synthesis.getTargets().get(0);
		long delta = Experience.getXpForLevel(40) - 13_750 - 11_450;
		assertEquals(delta, ((SkillLevelGap) target.getGaps().get(0)).getXpDelta(), "xpDelta net of quest xp");
		QuestXp waterfall = new QuestXp("quest:158", "Waterfall Quest", "https://oldschool.runescape.wiki/w/Waterfall_Quest", QuestState.NOT_STARTED, 13_750);
		QuestXp village = new QuestXp("quest:150", "Tree Gnome Village", "https://oldschool.runescape.wiki/w/Tree_Gnome_Village", QuestState.NOT_STARTED, 11_450);
		assertEquals(List.of(waterfall, village), target.getQuestXp(), "largest reward first");
		assertEquals(waterfall, target.getBankRoute().getSteps().get(0).getQuest());
		assertEquals(village, target.getBankRoute().getSteps().get(1).getQuest());
		assertEquals(delta, target.getBankRoute().getUncoveredXp(), "no methods in this KB: the rest is uncovered");
	}

	@Test
	void lampsCountOnlyForAllowedSkillsAtTheirMinimumLevelAndAreSpentOnceInParentScoreOrder()
	{
		// One 5,000 xp lamp usable in Attack or Magic; a 3,000 Attack lamp that needs level 30.
		// Magic's parent (priority 9) outscores Attack's (priority 3), so Magic takes the shared lamp.
		KnowledgeBase kb = new KbBuilder()
			.quest(158, "Waterfall Quest").lamp(5_000, 0, Skill.ATTACK, Skill.MAGIC).lamp(3_000, 30, Skill.ATTACK)
			.quest(0, "Animal Magnetism").skill(Skill.MAGIC, 5)
			.quest(9, "Biohazard").skill(Skill.ATTACK, 5)
			.priorityOverride("quest:0", 9)
			.priorityOverride("quest:9", 3)
			.build();
		Snapshot snapshot = new SnapshotBuilder().build();

		SkillTargetSynthesiser.Synthesis synthesis = SkillTargetSynthesiser.run(gapEngine.evaluate(snapshot, kb), Set.of(), 4, snapshot, kb);

		assertEquals(List.of("skill:ATTACK:5"), ids(synthesis.getTargets()), "Magic is covered by the shared lamp; Attack gets no lamp");
		assertEquals(Map.of("quest:158", Set.of("quest:0")), synthesis.getQuestUnblocks());
		GoalStatus attack = synthesis.getTargets().get(0);
		assertEquals(List.of(), attack.getQuestXp());
		assertEquals(Experience.getXpForLevel(5), ((SkillLevelGap) attack.getGaps().get(0)).getXpDelta());

		// At Attack 30 the level-30 lamp qualifies and counts towards a 40 Attack target.
		KnowledgeBase kb30 = new KbBuilder()
			.quest(158, "Waterfall Quest").lamp(3_000, 30, Skill.ATTACK)
			.quest(9, "Biohazard").skill(Skill.ATTACK, 40)
			.build();
		Snapshot at30 = new SnapshotBuilder().skill(Skill.ATTACK, 30).build();
		GoalStatus target = SkillTargetSynthesiser.run(gapEngine.evaluate(at30, kb30), Set.of(), 4, at30, kb30).getTargets().get(0);
		assertEquals(3_000, target.getQuestXp().get(0).getXp());
	}

	@Test
	void makingHistoryFixedPrayerXpUnlocksItsLevel20LampFromLevel19()
	{
		KnowledgeBase kb = new KbBuilder()
			.quest(92, "Making History").rewardXp(Skill.PRAYER, 1_000).lamp(1_000, 20, Skill.PRAYER)
			.quest(0, "Animal Magnetism").skill(Skill.PRAYER, 30)
			.build();
		Snapshot snapshot = new SnapshotBuilder().skill(Skill.PRAYER, 19).build();

		GoalStatus target = SkillTargetSynthesiser.run(
			gapEngine.evaluate(snapshot, kb), Set.of(), 4, snapshot, kb).getTargets().get(0);

		assertEquals(List.of(new QuestXp("quest:92", "Making History",
			"https://oldschool.runescape.wiki/w/Making_History", QuestState.NOT_STARTED, 2_000)), target.getQuestXp());
		long remaining = Experience.getXpForLevel(30) - Experience.getXpForLevel(19) - 2_000;
		assertEquals(remaining, ((SkillLevelGap) target.getGaps().get(0)).getXpDelta());
		assertEquals(remaining, target.getBankRoute().getUncoveredXp());
	}

	@Test
	void lampsUnlockLargerLampsWithoutReusingThemOrBypassingSkillAndLevelRestrictions()
	{
		KnowledgeBase kb = new KbBuilder()
			.quest(92, "Making History").rewardXp(Skill.PRAYER, 1_000)
				.lamp(1_000, 20, Skill.PRAYER, Skill.MAGIC)
				.lamp(3_000, 22, Skill.PRAYER, Skill.MAGIC)
				.lamp(20_000, 40, Skill.PRAYER)
				.lamp(50_000, 20, Skill.HERBLORE)
			.quest(0, "Animal Magnetism").skill(Skill.PRAYER, 30)
			.quest(9, "Biohazard").skill(Skill.MAGIC, 30)
			.priorityOverride("quest:0", 9).priorityOverride("quest:9", 3)
			.build();
		Snapshot snapshot = new SnapshotBuilder().skill(Skill.PRAYER, 19).skill(Skill.MAGIC, 22).build();

		List<GoalStatus> targets = SkillTargetSynthesiser.run(
			gapEngine.evaluate(snapshot, kb), Set.of(), 4, snapshot, kb).getTargets();

		GoalStatus prayer = targets.stream().filter(s -> s.getGoal().getId().equals("skill:PRAYER:30")).findFirst().orElseThrow();
		GoalStatus magic = targets.stream().filter(s -> s.getGoal().getId().equals("skill:MAGIC:30")).findFirst().orElseThrow();
		assertEquals(5_000, prayer.getQuestXp().stream().mapToLong(QuestXp::getXp).sum());
		assertEquals(Experience.getXpForLevel(30) - Experience.getXpForLevel(19) - 5_000,
			((SkillLevelGap) prayer.getGaps().get(0)).getXpDelta());
		assertEquals(List.of(), magic.getQuestXp(), "both shared lamps were spent on the higher-ranked Prayer target");
	}

	@Test
	void equalValuedLampsAreIndependentRewardsAcrossTargets()
	{
		KnowledgeBase kb = new KbBuilder()
			.quest(158, "Waterfall Quest").lamp(5_000, 0, Skill.ATTACK, Skill.MAGIC)
				.lamp(5_000, 0, Skill.ATTACK, Skill.MAGIC)
			.quest(150, "Tree Gnome Village").lamp(5_000, 0, Skill.ATTACK, Skill.MAGIC)
			.quest(0, "Animal Magnetism").skill(Skill.MAGIC, 5)
			.quest(9, "Biohazard").skill(Skill.ATTACK, 30)
			.priorityOverride("quest:0", 9).priorityOverride("quest:9", 3)
			.build();
		Snapshot snapshot = new SnapshotBuilder().build();
		SkillTargetSynthesiser.Synthesis result = SkillTargetSynthesiser.run(
			gapEngine.evaluate(snapshot, kb), Set.of(), 4, snapshot, kb);
		assertEquals(List.of("skill:ATTACK:30"), ids(result.getTargets()));
		assertEquals(10_000, result.getTargets().get(0).getQuestXp().stream().mapToLong(QuestXp::getXp).sum());
	}

	@Test
	void rewardCoverageDoesNotClaimToUnblockHigherLevelParents()
	{
		KnowledgeBase kb = new KbBuilder()
			.quest(158, "Waterfall Quest").rewardXp(Skill.ATTACK, 500)
			.quest(0, "Animal Magnetism").skill(Skill.ATTACK, 5)
			.quest(9, "Biohazard").skill(Skill.ATTACK, 40)
			.build();
		Snapshot snapshot = new SnapshotBuilder().build();
		SkillTargetSynthesiser.Synthesis result = SkillTargetSynthesiser.run(
			gapEngine.evaluate(snapshot, kb), Set.of(), 4, snapshot, kb);
		assertEquals(Map.of("quest:158", Set.of("quest:0")), result.getQuestUnblocks());
	}

	@Test
	void onlyUnfinishedVisibleQuestsWithTheirOwnRequirementsMetSupplyXp()
	{
		KnowledgeBase kb = new KbBuilder()
			.quest(158, "Waterfall Quest").rewardXp(Skill.ATTACK, 500)
			.quest(150, "Tree Gnome Village").rewardXp(Skill.ATTACK, 700).skill(Skill.MAGIC, 30)
			.quest(9, "Biohazard").rewardXp(Skill.ATTACK, 900)
			.quest(0, "Animal Magnetism").skill(Skill.ATTACK, 40)
			.build();
		Snapshot snapshot = new SnapshotBuilder().quest(Quest.WATERFALL_QUEST, QuestState.IN_PROGRESS)
			.quest(Quest.BIOHAZARD, QuestState.FINISHED).build();
		List<GoalStatus> statuses = gapEngine.evaluate(snapshot, kb);
		GoalStatus attack = SkillTargetSynthesiser.run(statuses, Set.of(), 4, snapshot, kb).getTargets().stream()
			.filter(s -> s.getGoal().getId().equals("skill:ATTACK:40")).findFirst().orElseThrow();
		assertEquals(List.of(new QuestXp("quest:158", "Waterfall Quest",
			"https://oldschool.runescape.wiki/w/Waterfall_Quest", QuestState.IN_PROGRESS, 500)), attack.getQuestXp());
		GoalStatus hiddenAttack = SkillTargetSynthesiser.run(statuses, Set.of("quest:158"), 4, snapshot, kb).getTargets().stream()
			.filter(s -> s.getGoal().getId().equals("skill:ATTACK:40")).findFirst().orElseThrow();
		assertEquals(List.of(), hiddenAttack.getQuestXp());
	}

	private static List<String> ids(List<GoalStatus> statuses)
	{
		return statuses.stream().map(s -> s.getGoal().getId()).collect(Collectors.toList());
	}
}
