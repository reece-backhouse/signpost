package com.signpost.snapshot;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;

/** Small client-thread readers shared by full snapshots and task-only header refreshes. */
public final class AccountUnlockReader
{
	private static final int[] TYPES = {VarbitID.RUNE_POUCH_TYPE_1, VarbitID.RUNE_POUCH_TYPE_2,
		VarbitID.RUNE_POUCH_TYPE_3, VarbitID.RUNE_POUCH_TYPE_4};
	private static final int[] QUANTITIES = {VarbitID.RUNE_POUCH_QUANTITY_1, VarbitID.RUNE_POUCH_QUANTITY_2,
		VarbitID.RUNE_POUCH_QUANTITY_3, VarbitID.RUNE_POUCH_QUANTITY_4};
	private static final int[] POUCHES = {ItemID.BH_RUNE_POUCH, ItemID.BH_RUNE_POUCH_TROUVER,
		ItemID.DIVINE_RUNE_POUCH, ItemID.DIVINE_RUNE_POUCH_TROUVER};

	private AccountUnlockReader()
	{
	}

	public static boolean isRunePouch(int itemId)
	{
		for (int id : POUCHES)
		{
			if (id == itemId) return true;
		}
		return false;
	}

	public static Map<Integer, Integer> runePouch(Client client, Map<Integer, Integer> inventory)
	{
		checkThread(client);
		boolean present = false;
		for (int id : POUCHES)
		{
			present |= inventory.getOrDefault(id, 0) > 0;
		}
		if (!present) return Map.of(); // Varbits retain stale contents after banking the pouch.
		EnumComposition runes = client.getEnum(EnumID.RUNEPOUCH_RUNE);
		Map<Integer, Integer> result = new HashMap<>();
		for (int slot = 0; slot < TYPES.length; slot++)
		{
			int type = client.getVarbitValue(TYPES[slot]);
			int quantity = client.getVarbitValue(QUANTITIES[slot]);
			if (type <= 0 || quantity <= 0) continue;
			int itemId = runes.getIntValue(type);
			if (itemId > 0) result.merge(itemId, quantity, Integer::sum);
		}
		return Map.copyOf(result);
	}

	public static SlayerState slayer(Client client)
	{
		checkThread(client);
		int target = client.getVarpValue(VarPlayerID.SLAYER_TARGET);
		int remaining = client.getVarpValue(VarPlayerID.SLAYER_COUNT);
		int master = client.getVarbitValue(VarbitID.SLAYER_MASTER);
		String name = remaining > 0 && target > 0 ? taskName(client, target) : null;
		EnumSet<SlayerReward> rewards = EnumSet.noneOf(SlayerReward.class);
		for (SlayerReward reward : SlayerReward.values())
		{
			if (client.getVarbitValue(reward.getVarbitId()) != 0) rewards.add(reward);
		}
		return new SlayerState(name, remaining, master, masterName(master),
			master == 10 ? client.getVarpValue(VarPlayerID.SLAYER_MORTIMER_TASKS_COMPLETED)
				: client.getVarbitValue(master == 7 ? VarbitID.SLAYER_WILDERNESS_TASKS_COMPLETED : VarbitID.SLAYER_TASKS_COMPLETED),
			client.getVarbitValue(VarbitID.SLAYER_POINTS), rewards);
	}

	private static String taskName(Client client, int target)
	{
		// Match RuneLite SlayerPlugin: boss target 98 indexes the boss sublist, not SlayerTask.COL_ID.
		List<Integer> rows;
		int task;
		if (target == 98)
		{
			rows = client.getDBRowsByValue(DBTableID.SlayerTaskSublist.ID, DBTableID.SlayerTaskSublist.COL_TASK_SUBTABLE_ID,
				0, client.getVarbitValue(VarbitID.SLAYER_TARGET_BOSSID));
			if (rows == null || rows.isEmpty()) return "Unknown task";
			Object[] fields = client.getDBTableField(rows.get(0), DBTableID.SlayerTaskSublist.COL_TASK, 0);
			if (fields == null || fields.length == 0 || !(fields[0] instanceof Integer)) return "Unknown task";
			task = (Integer) fields[0];
		}
		else
		{
			rows = client.getDBRowsByValue(DBTableID.SlayerTask.ID, DBTableID.SlayerTask.COL_ID, 0, target);
			if (rows == null || rows.isEmpty()) return "Unknown task";
			task = rows.get(0);
		}
		Object[] names = client.getDBTableField(task, DBTableID.SlayerTask.COL_NAME_UPPERCASE, 0);
		return names != null && names.length > 0 && names[0] instanceof String ? (String) names[0] : "Unknown task";
	}

	private static String masterName(int master)
	{
		switch (master)
		{
			case 1: return "Turael";
			case 2: return "Mazchna";
			case 3: return "Vannaka";
			case 4: return "Chaeldar";
			case 5: return "Duradel";
			case 6: return "Nieve";
			case 7: return "Krystilia";
			case 8: return "Konar";
			case 10: return "Mortimer";
			default: return "Unknown master";
		}
	}

	public static boolean isTaskChange(int varp, int varbit)
	{
		return varp == VarPlayerID.SLAYER_COUNT || varp == VarPlayerID.SLAYER_TARGET
			|| varp == VarPlayerID.SLAYER_MORTIMER_TASKS_COMPLETED
			|| varbit == VarbitID.SLAYER_TARGET_BOSSID || varbit == VarbitID.SLAYER_MASTER;
	}

	public static boolean isUnlockChange(int varbit)
	{
		if (varbit == VarbitID.SLAYER_POINTS || varbit == VarbitID.SLAYER_TASKS_COMPLETED
			|| varbit == VarbitID.SLAYER_WILDERNESS_TASKS_COMPLETED || varbit == VarbitID.SPELLBOOK
			|| varbit == VarbitID.PRAYER_RIGOUR_UNLOCKED || varbit == VarbitID.PRAYER_AUGURY_UNLOCKED
			|| varbit == VarbitID.PRAYER_PRESERVE_UNLOCKED) return true;
		for (int id : TYPES) if (varbit == id) return true;
		for (int id : QUANTITIES) if (varbit == id) return true;
		for (SlayerReward reward : SlayerReward.values()) if (varbit == reward.getVarbitId()) return true;
		return false;
	}

	private static void checkThread(Client client)
	{
		if (!client.isClientThread()) throw new IllegalStateException("AccountUnlockReader must run on the client thread");
	}
}
