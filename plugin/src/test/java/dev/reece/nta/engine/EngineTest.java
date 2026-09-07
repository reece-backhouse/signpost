package dev.reece.nta.engine;

import java.util.stream.Collectors;
import java.util.List;
import dev.reece.nta.engine.model.GoalStatus;
import dev.reece.nta.engine.model.Advice;
import dev.reece.nta.kb.KnowledgeBase;
import dev.reece.nta.snapshot.DiaryTier;
import dev.reece.nta.snapshot.Snapshot;
import net.runelite.api.Skill;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * {@link Engine#run} is just {@link GapEngine#evaluate} + {@link DiaryProgress#compute} glued
 * together with a timestamp; this checks the glue, not the underlying computations (those are
 * covered by {@link GapEngineTest} and {@link DiaryProgressTest}).
 */
class EngineTest
{
	private static final int CLOCK_TOWER_ID = 14; // Quest.CLOCK_TOWER

	@Test
	void runReturnsStatusesAndDiaryProgressConsistentWithCallingBothPiecesDirectly()
	{
		KnowledgeBase kb = new KbBuilder()
			.quest(CLOCK_TOWER_ID, "Clock Tower").skill(Skill.MINING, 20)
			.diaryTier(DiaryTier.VARROCK_EASY, 1176, 4)
			.build();
		Snapshot snapshot = new SnapshotBuilder().skill(Skill.MINING, 5).diaryVarp(1176, 0b0101).build();

		Engine engine = new Engine(new BoostTable());
		Advice advice = engine.run(snapshot, kb);

		assertEquals(snapshot, advice.getSnapshot());
		assertNotNull(advice.getComputedAt());

		GapEngine gapEngine = new GapEngine(new BoostTable());
		// Task 51: statuses are the gap engine's, followed by the synthesised skill targets (spec ruling 28).
		List<GoalStatus> base = gapEngine.evaluate(snapshot, kb);
		assertEquals(base, advice.getStatuses().subList(0, base.size()));
		assertEquals(List.of("skill:MINING:20"), advice.getStatuses().subList(base.size(), advice.getStatuses().size()).stream()
			.map(s -> s.getGoal().getId()).collect(Collectors.toList()));
		assertEquals(DiaryProgress.compute(snapshot, kb), advice.getDiaryProgress());
	}
}
