package dev.reece.nta.kb;

import dev.reece.nta.snapshot.AccountType;
import dev.reece.nta.snapshot.Snapshot;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskCompletionTest
{
	@Test
	void varpFormChecksTheGivenBit()
	{
		// 0b0110: bits 1 and 2 set, bit 3 not.
		Snapshot snapshot = snapshotWithDiaryVarp(1176, 0b0110);

		assertTrue(varp(1176, 1).isComplete(snapshot));
		assertTrue(varp(1176, 2).isComplete(snapshot));
		assertFalse(varp(1176, 3).isComplete(snapshot));
	}

	@Test
	void varpFormTreatsMissingVarpAsZero()
	{
		Snapshot snapshot = Snapshot.builder().accountHash(1L).accountType(AccountType.NORMAL).build();

		assertFalse(varp(1176, 0).isComplete(snapshot));
	}

	@Test
	void varbitFormCompleteOnceValueReachesDoneMin()
	{
		Snapshot below = snapshotWithKaramjaVarbit(3566, 4);
		Snapshot atThreshold = snapshotWithKaramjaVarbit(3566, 5);
		Snapshot above = snapshotWithKaramjaVarbit(3566, 6);

		TaskCompletion completion = varbit(3566, 5);

		assertFalse(completion.isComplete(below));
		assertTrue(completion.isComplete(atThreshold));
		assertTrue(completion.isComplete(above));
	}

	private static TaskCompletion varp(int varp, int bit)
	{
		return new TaskCompletion(varp, bit, null, null);
	}

	private static TaskCompletion varbit(int varbit, int doneMin)
	{
		return new TaskCompletion(null, null, varbit, doneMin);
	}

	private static Snapshot snapshotWithDiaryVarp(int varp, int value)
	{
		return Snapshot.builder()
			.accountHash(1L)
			.accountType(AccountType.NORMAL)
			.diaryVarps(Map.of(varp, value))
			.build();
	}

	private static Snapshot snapshotWithKaramjaVarbit(int varbit, int value)
	{
		return Snapshot.builder()
			.accountHash(1L)
			.accountType(AccountType.NORMAL)
			.karamjaVarbits(Map.of(varbit, value))
			.build();
	}
}
