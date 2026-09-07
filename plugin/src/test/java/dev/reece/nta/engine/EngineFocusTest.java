package dev.reece.nta.engine;

import dev.reece.nta.engine.model.Advice;
import dev.reece.nta.engine.model.FocusDetail;
import dev.reece.nta.engine.model.NextStepType;
import dev.reece.nta.kb.KnowledgeBase;
import dev.reece.nta.snapshot.Snapshot;
import dev.reece.nta.store.AccountData;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import net.runelite.api.Skill;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/** Task 39: {@link Engine#run} computing {@link Advice#getFocus()}, per the goal detail view brief. */
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

	private static AccountData focusOn(String goalId)
	{
		return new AccountData(new HashMap<>(), null, new HashMap<>(), new HashSet<>(), new ArrayList<>(), goalId, new HashSet<>());
	}
}
