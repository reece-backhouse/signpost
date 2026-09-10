package com.signpost.engine;

import com.signpost.engine.model.Advice;
import com.signpost.engine.model.FocusDetail;
import com.signpost.engine.model.NextStepType;
import com.signpost.engine.model.PrerequisiteGap;
import com.signpost.engine.model.SkillPlan;
import com.signpost.kb.KnowledgeBase;
import com.signpost.kb.MilestoneCategory;
import com.signpost.snapshot.DiaryTier;
import com.signpost.snapshot.Snapshot;
import com.signpost.store.AccountData;
import com.signpost.store.AccountDataMutations;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link Engine#run} computes {@link Advice#getFocus()} for the goal detail view. */
class EngineFocusTest
{
	private final Engine engine = new Engine(new BoostTable());
	private final Instant now = Instant.parse("2026-09-07T12:00:00Z");

	@Test
	void focusIsNullWithoutAFocusGoalId()
	{
		KnowledgeBase kb = new KbBuilder().quest(0, "Animal Magnetism").skill(Skill.WOODCUTTING, 10).build();
		Snapshot snapshot = new SnapshotBuilder().build();

		Advice advice = engine.run(snapshot, kb, AccountData.empty(), now);

		assertNull(advice.getFocus());
	}

	@Test
	void focusedQuestWithAHerbloreGapAndABankFixtureGetsASkillStepWithANonEmptyRoute()
	{
		KnowledgeBase kb = new KbBuilder()
			.quest(0, "Song of the Elves").skill(Skill.HERBLORE, 10)
			.method(Skill.HERBLORE, "Prayer potion(3)", 1, 100)
			.material(10, 1)
			.build();
		Snapshot snapshot = new SnapshotBuilder().bankItem(10, "Ranarr potion (unf)", 10_000).build();
		AccountData data = focusOn("quest:0");

		Advice advice = engine.run(snapshot, kb, data, now);

		FocusDetail focus = advice.getFocus();
		assertNotNull(focus);
		assertEquals("quest:0", focus.getStatus().getGoal().getId());
		assertEquals(NextStepType.SKILL, focus.getNext().getType());
		assertEquals(Skill.HERBLORE, focus.getNext().getSkillGap().getSkill());
		assertNotNull(focus.getRoute());
		assertFalse(focus.getRoute().getSteps().isEmpty());
		assertSame(focus.getRoute(), focus.getNext().getRoute(), "FocusDetail.route must be the same object NextStepPicker computed, not a second computation");
		assertEquals(0, focus.getRoute().getUncoveredXp());
		assertNull(focus.getShortfall(), "route fully covers the xp delta, so there is no shortfall");
		assertEquals(1, focus.getFromLevel());
		assertEquals(10, focus.getToLevel());
	}

	@Test
	void focusedGoalAbsentFromStatusesIsNull()
	{
		// "quest:999" isn't in the kb at all, so it can never appear among the evaluated statuses.
		KnowledgeBase kb = new KbBuilder().quest(0, "Animal Magnetism").skill(Skill.WOODCUTTING, 10).build();
		Snapshot snapshot = new SnapshotBuilder().build();
		AccountData data = focusOn("quest:999");

		Advice advice = engine.run(snapshot, kb, data, now);

		assertNull(advice.getFocus());
	}

	@Test
	void blockedDependantRemainsFocusableWhenItsPrerequisiteIsHidden()
	{
		KnowledgeBase kb = new KbBuilder()
			.milestone("room", MilestoneCategory.POH, "Room", 5).item("Coins", 995, 200000)
			.milestone("box", MilestoneCategory.POH, "Box", 7).prerequisite("room")
			.build();
		AccountData data = AccountDataMutations.ignore(focusOn("box"), "room");

		Advice advice = engine.run(new SnapshotBuilder().build(), kb, data, now);

		assertTrue(advice.getPicked().isEmpty(), "hiding the room must not cause an automatic box suggestion");
		assertEquals(List.of("box"), advice.getRanked().stream().map(r -> r.getStatus().getGoal().getId()).collect(Collectors.toList()));
		assertNotNull(advice.getFocus());
		assertEquals("box", advice.getFocus().getStatus().getGoal().getId());
		assertFalse(advice.getFocus().getStatus().isReady());
		assertTrue(advice.getFocus().getStatus().getGaps().stream().anyMatch(g -> g instanceof PrerequisiteGap));
	}

	private static final int BIOHAZARD_ID = 9;    // Quest.BIOHAZARD
	private static final int CLOCK_TOWER_ID = 14; // Quest.CLOCK_TOWER

	/** {@link Advice#getFocus()} computing one {@link SkillPlan} per distinct skill gap of the focused goal. */
	@Test
	void focusedGoalWithThreeSkillGapsProducesThreeSkillPlansSortedByXpDeltaAscending()
	{
		// Declared out of target-level order to prove the result is sorted, not insertion order.
		KnowledgeBase kb = new KbBuilder()
			.quest(0, "Test Quest").skill(Skill.MINING, 20).skill(Skill.HERBLORE, 10).skill(Skill.WOODCUTTING, 5)
			.build();
		Snapshot snapshot = new SnapshotBuilder().build();
		AccountData data = focusOn("quest:0");

		Advice advice = engine.run(snapshot, kb, data, now);

		FocusDetail focus = advice.getFocus();
		assertNotNull(focus);
		List<SkillPlan> plans = focus.getSkillPlans();
		assertEquals(3, plans.size());
		assertEquals(Skill.WOODCUTTING, plans.get(0).getSkill());
		assertEquals(5, plans.get(0).getToLevel());
		assertEquals(Skill.HERBLORE, plans.get(1).getSkill());
		assertEquals(10, plans.get(1).getToLevel());
		assertEquals(Skill.MINING, plans.get(2).getSkill());
		assertEquals(20, plans.get(2).getToLevel());
	}

	@Test
	void pickedSkillsSkillPlanRouteIsTheSameObjectAsNextRoute()
	{
		KnowledgeBase kb = new KbBuilder()
			.quest(0, "Test Quest").skill(Skill.HERBLORE, 10).skill(Skill.WOODCUTTING, 5)
			.method(Skill.HERBLORE, "Prayer potion(3)", 1, 100)
			.material(10, 1)
			.build();
		// Only Herblore's route is bank-covered; Woodcutting has no methods at all, so it can never
		// be "covered" and NextStepPicker branch B must pick Herblore despite its larger xpDelta.
		Snapshot snapshot = new SnapshotBuilder().bankItem(10, "Ranarr potion (unf)", 10_000).build();
		AccountData data = focusOn("quest:0");

		Advice advice = engine.run(snapshot, kb, data, now);

		FocusDetail focus = advice.getFocus();
		assertNotNull(focus);
		assertEquals(NextStepType.SKILL, focus.getNext().getType());
		assertEquals(Skill.HERBLORE, focus.getNext().getSkillGap().getSkill());
		assertNotNull(focus.getNextSkillPlan());
		assertEquals(Skill.HERBLORE, focus.getNextSkillPlan().getSkill());
		assertSame(focus.getNext().getRoute(), focus.getNextSkillPlan().getRoute(),
			"the next skill's SkillPlan.route must be the same object NextStepPicker already computed, not a second computation");
		assertSame(focus.getRoute(), focus.getNextSkillPlan().getRoute());
	}

	@Test
	void duplicateSkillGapAtTwoLevelsKeepsTheHigherTargetLevel()
	{
		// A boss milestone's hard skill requirement and its recommended profile both name Herblore,
		// at different levels - both land as separate SkillLevelGaps in the same GoalStatus.
		KnowledgeBase kb = new KbBuilder()
			.milestone("boss:test", MilestoneCategory.BOSS, "Test Boss", 1)
			.skill(Skill.HERBLORE, 30)
			.recommendedSkill(Skill.HERBLORE, 50)
			.build();
		Snapshot snapshot = new SnapshotBuilder().build();
		AccountData data = focusOn("boss:test");

		Advice advice = engine.run(snapshot, kb, data, now);

		FocusDetail focus = advice.getFocus();
		assertNotNull(focus);
		List<SkillPlan> herblorePlans = focus.getSkillPlans().stream()
			.filter(p -> p.getSkill() == Skill.HERBLORE)
			.collect(Collectors.toList());
		assertEquals(1, herblorePlans.size(), "the two Herblore gaps must collapse into one SkillPlan");
		assertEquals(50, herblorePlans.get(0).getToLevel(), "the higher of the two target levels must win");
	}

	@Test
	void diaryTaskInnerSkillGapGetsDiaryTaskSourceWithOrdinal()
	{
		KnowledgeBase kb = new KbBuilder()
			.diary(DiaryTier.VARROCK_EASY).task(3, "Mine some tin").skill(Skill.MINING, 10)
			.build();
		Snapshot snapshot = new SnapshotBuilder().build();
		AccountData data = focusOn("diary:VARROCK_EASY");

		Advice advice = engine.run(snapshot, kb, data, now);

		FocusDetail focus = advice.getFocus();
		assertNotNull(focus);
		assertEquals(1, focus.getSkillPlans().size());
		SkillPlan plan = focus.getSkillPlans().get(0);
		assertEquals(Skill.MINING, plan.getSkill());
		assertEquals("diary task 3", plan.getSource());
	}

	@Test
	void skillPlansFlagCoveredWhenTheRouteReachesTargetAndUncoveredOtherwise()
	{
		KnowledgeBase kb = new KbBuilder()
			.quest(0, "Test Quest").skill(Skill.HERBLORE, 10).skill(Skill.WOODCUTTING, 5)
			.method(Skill.HERBLORE, "H", 1, 100)
			.material(1, 1)
			.build();
		Snapshot snapshot = new SnapshotBuilder().bankItem(1, "H material", 100_000).build();
		AccountData data = focusOn("quest:0");

		Advice advice = engine.run(snapshot, kb, data, now);

		FocusDetail focus = advice.getFocus();
		assertNotNull(focus);
		SkillPlan herblore = planFor(focus, Skill.HERBLORE);
		SkillPlan woodcutting = planFor(focus, Skill.WOODCUTTING);

		assertTrue(herblore.isCovered());
		assertNull(herblore.getShortfall(), "a covered plan carries no shortfall");
		assertFalse(woodcutting.isCovered());
		assertNotNull(woodcutting.getShortfall(), "an uncovered plan must have its shortfall resolved");
	}

	@Test
	void nextSkillPlanIsNullWhenTheNextStepIsAQuest()
	{
		KnowledgeBase kb = new KbBuilder()
			.quest(CLOCK_TOWER_ID, "Clock Tower").prereq("Biohazard").skill(Skill.MINING, 20)
			.quest(BIOHAZARD_ID, "Biohazard")
			.build();
		Snapshot snapshot = new SnapshotBuilder().quest(Quest.BIOHAZARD, QuestState.NOT_STARTED).build();
		AccountData data = focusOn("quest:" + CLOCK_TOWER_ID);

		Advice advice = engine.run(snapshot, kb, data, now);

		FocusDetail focus = advice.getFocus();
		assertNotNull(focus);
		assertEquals(NextStepType.QUEST, focus.getNext().getType());
		assertNull(focus.getNextSkillPlan());
		assertFalse(focus.getSkillPlans().isEmpty(), "the quest's own skill gap should still produce a SkillPlan even though it wasn't picked next");
	}

	private static SkillPlan planFor(FocusDetail focus, Skill skill)
	{
		return focus.getSkillPlans().stream().filter(p -> p.getSkill() == skill).findFirst()
			.orElseThrow(() -> new AssertionError("no SkillPlan for " + skill));
	}

	private static AccountData focusOn(String goalId)
	{
		return new AccountData(new HashMap<>(), null, new HashMap<>(), new HashSet<>(), new ArrayList<>(), goalId, new HashSet<>());
	}
}
