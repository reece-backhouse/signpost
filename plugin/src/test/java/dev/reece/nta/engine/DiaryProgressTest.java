package dev.reece.nta.engine;

import dev.reece.nta.kb.KnowledgeBase;
import dev.reece.nta.snapshot.AccountType;
import dev.reece.nta.snapshot.DiaryTier;
import dev.reece.nta.snapshot.Snapshot;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiaryProgressTest
{
	@Test
	void countsCompletedTasksFromTaskCompletionBits()
	{
		KnowledgeBase kb = new KbBuilder().diaryTier(DiaryTier.VARROCK_EASY, 1176, 4).build();
		// bits 0 and 2 set -> tasks 1 and 3 complete, 2 and 4 not.
		Snapshot snapshot = Snapshot.builder()
			.accountHash(1L)
			.accountType(AccountType.NORMAL)
			.diaryVarps(Map.of(1176, 0b0101))
			.build();

		Map<DiaryTier, DiaryTierProgress> progress = DiaryProgress.compute(snapshot, kb);
		DiaryTierProgress varrockEasy = progress.get(DiaryTier.VARROCK_EASY);

		assertEquals(2, varrockEasy.getCompleted());
		assertEquals(4, varrockEasy.getTotal());
		assertEquals(List.of(1, 3), varrockEasy.getCompletedOrdinals());
	}

	@Test
	void mismatchIsTrueWhenGameCountDiffersFromComputedCount()
	{
		KnowledgeBase kb = new KbBuilder().diaryTier(DiaryTier.VARROCK_EASY, 1176, 4).build();
		Snapshot snapshot = Snapshot.builder()
			.accountHash(1L)
			.accountType(AccountType.NORMAL)
			.diaryVarps(Map.of(1176, 0b0101))
			.diaryCountVarbits(Map.of(DiaryProgress.COUNT_VARBITS.get(DiaryTier.VARROCK_EASY), 3))
			.build();

		DiaryTierProgress varrockEasy = DiaryProgress.compute(snapshot, kb).get(DiaryTier.VARROCK_EASY);

		assertEquals(2, varrockEasy.getCompleted());
		assertEquals(3, varrockEasy.getGameCount());
		assertTrue(varrockEasy.isMismatch());
	}

	@Test
	void mismatchIsFalseWhenGameCountMatches()
	{
		KnowledgeBase kb = new KbBuilder().diaryTier(DiaryTier.VARROCK_EASY, 1176, 4).build();
		Snapshot snapshot = Snapshot.builder()
			.accountHash(1L)
			.accountType(AccountType.NORMAL)
			.diaryVarps(Map.of(1176, 0b0101))
			.diaryCountVarbits(Map.of(DiaryProgress.COUNT_VARBITS.get(DiaryTier.VARROCK_EASY), 2))
			.build();

		DiaryTierProgress varrockEasy = DiaryProgress.compute(snapshot, kb).get(DiaryTier.VARROCK_EASY);

		assertFalse(varrockEasy.isMismatch());
	}

	@Test
	void gameCountAndMismatchAreAbsentWhenSnapshotHasNoCountVarbitData()
	{
		KnowledgeBase kb = new KbBuilder().diaryTier(DiaryTier.VARROCK_EASY, 1176, 4).build();
		Snapshot snapshot = Snapshot.builder()
			.accountHash(1L)
			.accountType(AccountType.NORMAL)
			.diaryVarps(Map.of(1176, 0b0101))
			.build();

		DiaryTierProgress varrockEasy = DiaryProgress.compute(snapshot, kb).get(DiaryTier.VARROCK_EASY);

		assertNull(varrockEasy.getGameCount());
		assertFalse(varrockEasy.isMismatch());
	}
}
