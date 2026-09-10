package com.signpost.snapshot;

import java.util.Arrays;
import net.runelite.api.gameval.VarbitID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DiaryTierTest
{
	@Test
	void karamjaTiersUseTheAtjunSpecialCaseVarbits()
	{
		// Karamja predates the DIARY_*_COMPLETE naming: gameval keeps its easy/medium/hard done
		// flags under the ATJUN_ prefix and only the elite tier under KARAMJA_DIARY_ELITE_COMPLETE.
		assertEquals(VarbitID.ATJUN_EASY_DONE, DiaryTier.KARAMJA_EASY.getVarbitId());
		assertEquals(VarbitID.ATJUN_MED_DONE, DiaryTier.KARAMJA_MEDIUM.getVarbitId());
		assertEquals(VarbitID.ATJUN_HARD_DONE, DiaryTier.KARAMJA_HARD.getVarbitId());
		assertEquals(VarbitID.KARAMJA_DIARY_ELITE_COMPLETE, DiaryTier.KARAMJA_ELITE.getVarbitId());
	}

	@Test
	void everyRegionAndTierHasItsOwnVarbit()
	{
		assertEquals(48, DiaryTier.values().length);
		assertEquals(48, Arrays.stream(DiaryTier.values()).mapToInt(DiaryTier::getVarbitId).distinct().count());
	}
}
