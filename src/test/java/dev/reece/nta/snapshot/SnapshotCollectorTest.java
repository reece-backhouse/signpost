package dev.reece.nta.snapshot;

import net.runelite.api.Item;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnapshotCollectorTest
{
	@Test
	void isRealItemSkipsEmptySlotPlaceholders()
	{
		assertFalse(SnapshotCollector.isRealItem(new Item(-1, 0)));
		assertFalse(SnapshotCollector.isRealItem(new Item(-1, 1)));
		assertFalse(SnapshotCollector.isRealItem(new Item(995, 0)));
	}

	@Test
	void isRealItemKeepsNormalItems()
	{
		assertTrue(SnapshotCollector.isRealItem(new Item(995, 1000)));
	}

	@Test
	void combatAchievementTierIsCompleteOnlyOnceClaimed()
	{
		// CA_TIER_STATUS_* is 0 = not done, 1 = tasks done but rewards unclaimed, 2 = claimed.
		assertFalse(SnapshotCollector.isCombatAchievementTierComplete(0));
		assertFalse(SnapshotCollector.isCombatAchievementTierComplete(1));
		assertTrue(SnapshotCollector.isCombatAchievementTierComplete(2));
	}
}
