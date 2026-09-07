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
}
