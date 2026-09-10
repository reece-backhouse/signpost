package com.signpost.snapshot;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountUnlockReaderTest
{
	@Test
	void pouchVariantsUseTheRuneEnumAndIgnoreEmptySlotsIncludingStaleContentsWhenBanked()
	{
		Map<Integer, Integer> varbits = Map.of(
			VarbitID.RUNE_POUCH_TYPE_1, 17, VarbitID.RUNE_POUCH_QUANTITY_1, 9,
			VarbitID.RUNE_POUCH_TYPE_2, 0, VarbitID.RUNE_POUCH_QUANTITY_2, 40,
			VarbitID.RUNE_POUCH_TYPE_3, 42, VarbitID.RUNE_POUCH_QUANTITY_3, 0,
			VarbitID.RUNE_POUCH_TYPE_4, 91, VarbitID.RUNE_POUCH_QUANTITY_4, 4);
		EnumComposition runes = stub(EnumComposition.class, (method, args) ->
		{
			if (method.equals("getIntValue")) return Map.of(17, 561, 91, 9075).getOrDefault(args[0], -1);
			throw new AssertionError("Unexpected enum read: " + method);
		});
		Client client = stub(Client.class, (method, args) ->
		{
			switch (method)
			{
				case "isClientThread": return true;
				case "getVarbitValue": return varbits.getOrDefault(args[0], 0);
				case "getEnum": return runes;
				default: throw new AssertionError("Unexpected client read: " + method);
			}
		});
		for (int pouch : new int[]{ItemID.BH_RUNE_POUCH, ItemID.BH_RUNE_POUCH_TROUVER,
			ItemID.DIVINE_RUNE_POUCH, ItemID.DIVINE_RUNE_POUCH_TROUVER})
		{
			assertEquals(Map.of(561, 9, 9075, 4), AccountUnlockReader.runePouch(client, Map.of(pouch, 1)));
		}
		assertEquals(Map.of(), AccountUnlockReader.runePouch(client, Map.of()));
		assertEquals(Map.of(), AccountUnlockReader.runePouch(client, Map.of(ItemID.BH_RUNE_POUCH, 0)));
	}

	@Test
	void bossTasksResolveThroughTheSublistAndUseTheWildernessStreakForKrystilia()
	{
		Map<Integer, Integer> varbits = Map.of(
			VarbitID.SLAYER_MASTER, 7, VarbitID.SLAYER_TARGET_BOSSID, 50,
			VarbitID.SLAYER_POINTS, 1240, VarbitID.SLAYER_TASKS_COMPLETED, 15,
			VarbitID.SLAYER_WILDERNESS_TASKS_COMPLETED, 271,
			SlayerReward.BIGGER_AND_BADDER.getVarbitId(), 1);
		Client client = stub(Client.class, (method, args) ->
		{
			switch (method)
			{
				case "isClientThread": return true;
				case "getVarpValue": return Map.of(VarPlayerID.SLAYER_TARGET, 98,
					VarPlayerID.SLAYER_COUNT, 87).getOrDefault(args[0], 0);
				case "getVarbitValue": return varbits.getOrDefault(args[0], 0);
				case "getDBRowsByValue":
					return args[0].equals(DBTableID.SlayerTaskSublist.ID)
						&& args[1].equals(DBTableID.SlayerTaskSublist.COL_TASK_SUBTABLE_ID)
						&& args[3].equals(50) ? List.of(801) : List.of();
				case "getDBTableField":
					if (args[0].equals(801) && args[1].equals(DBTableID.SlayerTaskSublist.COL_TASK))
						return new Object[]{902};
					if (args[0].equals(902) && args[1].equals(DBTableID.SlayerTask.COL_NAME_UPPERCASE))
						return new Object[]{"Vorkath"};
					return new Object[0];
				default: throw new AssertionError("Unexpected client read: " + method);
			}
		});
		SlayerState state = AccountUnlockReader.slayer(client);
		assertEquals("Vorkath", state.getTaskName());
		assertEquals(87, state.getRemaining());
		assertEquals("Krystilia", state.getMaster());
		assertEquals(271, state.getStreak());
		assertEquals(1240, state.getPoints());
		assertEquals(Set.of(SlayerReward.BIGGER_AND_BADDER), state.getUnlocks());
		assertTrue(state.isActive());
	}

	@ParameterizedTest
	@CsvSource({"10, Mortimer, 31", "7, Krystilia, 271", "5, Duradel, 15"})
	void taskStateUsesTheAssignedMastersNameAndStreak(int master, String name, int streak)
	{
		Map<Integer, Integer> varbits = Map.of(VarbitID.SLAYER_MASTER, master,
			VarbitID.SLAYER_TASKS_COMPLETED, 15, VarbitID.SLAYER_WILDERNESS_TASKS_COMPLETED, 271);
		Map<Integer, Integer> varps = Map.of(VarPlayerID.SLAYER_TARGET, 1,
			VarPlayerID.SLAYER_COUNT, 87, VarPlayerID.SLAYER_MORTIMER_TASKS_COMPLETED, 31);
		Client client = stub(Client.class, (method, args) ->
		{
			switch (method)
			{
				case "isClientThread": return true;
				case "getVarpValue": return varps.getOrDefault(args[0], 0);
				case "getVarbitValue": return varbits.getOrDefault(args[0], 0);
				case "getDBRowsByValue": return List.of(902);
				case "getDBTableField": return new Object[]{"Banshees"};
				default: throw new AssertionError("Unexpected client read: " + method);
			}
		});
		SlayerState state = AccountUnlockReader.slayer(client);
		assertTrue(state.isActive());
		assertEquals(name, state.getMaster());
		assertEquals(streak, state.getStreak());
		assertTrue(AccountUnlockReader.isTaskChange(VarPlayerID.SLAYER_MORTIMER_TASKS_COMPLETED, -1));
		assertFalse(AccountUnlockReader.isUnlockChange(-1));
	}

	@Test
	void completedTasksDoNotKeepTheStaleNameOrQueryTheTaskTable()
	{
		Client client = stub(Client.class, (method, args) ->
		{
			switch (method)
			{
				case "isClientThread": return true;
				case "getVarpValue": return args[0].equals(VarPlayerID.SLAYER_TARGET) ? 98 : 0;
				case "getVarbitValue": return 0;
				default: throw new AssertionError("Inactive task must not query: " + method);
			}
		});
		assertFalse(AccountUnlockReader.slayer(client).isActive());
	}

	@Test
	void readsOutsideTheClientThreadFailBeforeAccessingAccountState()
	{
		Client client = stub(Client.class, (method, args) ->
		{
			if (method.equals("isClientThread")) return false;
			throw new AssertionError("Account state read off the client thread");
		});
		assertThrows(IllegalStateException.class, () -> AccountUnlockReader.slayer(client));
		assertThrows(IllegalStateException.class, () -> AccountUnlockReader.runePouch(client, Map.of()));
	}

	private interface Calls
	{
		Object invoke(String method, Object[] args);
	}

	private static <T> T stub(Class<T> type, Calls calls)
	{
		return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
			(proxy, method, args) -> calls.invoke(method.getName(), args)));
	}
}
