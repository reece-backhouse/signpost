package dev.reece.nta.engine;

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
		assertEquals(gapEngine.evaluate(snapshot, kb), advice.getStatuses());
		assertEquals(DiaryProgress.compute(snapshot, kb), advice.getDiaryProgress());
	}
}
