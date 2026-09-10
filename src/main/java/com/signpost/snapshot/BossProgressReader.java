package com.signpost.snapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.GameState;
import net.runelite.api.StructComposition;
import net.runelite.api.VarbitComposition;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;

/** Client-thread reader. Only account-independent cache metadata survives between reads. */
@Slf4j
public final class BossProgressReader
{
	private static final int BOSS_NAMES_ENUM = 3971;
	private static final int FIRST_TIER_ENUM = 3981;
	private static final int TASK_ID = 1306;
	private static final int TASK_NAME = 1308;
	private static final int TASK_DESCRIPTION = 1309;
	private static final int TASK_BOSS = 1312;
	// These words are not contiguous varps. New tasks beyond the supported words stay unknown.
	private static final int[] TASK_WORDS = {
		VarPlayerID.CA_TASK_COMPLETED_0, VarPlayerID.CA_TASK_COMPLETED_1,
		VarPlayerID.CA_TASK_COMPLETED_2, VarPlayerID.CA_TASK_COMPLETED_3,
		VarPlayerID.CA_TASK_COMPLETED_4, VarPlayerID.CA_TASK_COMPLETED_5,
		VarPlayerID.CA_TASK_COMPLETED_6, VarPlayerID.CA_TASK_COMPLETED_7,
		VarPlayerID.CA_TASK_COMPLETED_8, VarPlayerID.CA_TASK_COMPLETED_9,
		VarPlayerID.CA_TASK_COMPLETED_10, VarPlayerID.CA_TASK_COMPLETED_11,
		VarPlayerID.CA_TASK_COMPLETED_12, VarPlayerID.CA_TASK_COMPLETED_13,
		VarPlayerID.CA_TASK_COMPLETED_14, VarPlayerID.CA_TASK_COMPLETED_15,
		VarPlayerID.CA_TASK_COMPLETED_16, VarPlayerID.CA_TASK_COMPLETED_17,
		VarPlayerID.CA_TASK_COMPLETED_18, VarPlayerID.CA_TASK_COMPLETED_19,
		VarPlayerID.CA_TASK_COMPLETED_20
	};
	private static final Map<String, List<Integer>> COLLECTIONS = collections();
	private static final Set<Integer> COLLECTION_VARBITS = collectionVarbits();
	private static final BossProgress UNKNOWN = new BossProgress(null, false, List.of());

	private boolean metadataLoaded;
	private Map<String, List<CombatAchievementTask>> tasks = Map.of();
	private Map<Integer, Integer> collectionParents = Map.of();

	public Map<String, BossProgress> read(Client client)
	{
		checkThread(client);
		Map<String, BossProgress> result = new LinkedHashMap<>();
		int[] varps = client.getVarps();
		if (client.getGameState() != GameState.LOGGED_IN || varps == null)
		{
			COLLECTIONS.keySet().forEach(id -> result.put(id, UNKNOWN));
			return Collections.unmodifiableMap(result);
		}
		loadMetadata(client);
		for (Map.Entry<String, List<Integer>> entry : COLLECTIONS.entrySet())
		{
			List<CombatAchievementTask> bossTasks = tasks.get(entry.getKey());
			boolean known = bossTasks != null;
			List<CombatAchievementTask> remaining = new ArrayList<>();
			if (known)
			{
				for (CombatAchievementTask task : bossTasks)
				{
					int word = TASK_WORDS[task.getId() / 32];
					if (word >= varps.length)
					{
						known = false;
						remaining.clear();
						break;
					}
					if ((varps[word] & (1 << (task.getId() % 32))) == 0)
					{
						remaining.add(task);
					}
				}
			}
			result.put(entry.getKey(), new BossProgress(greenLogged(client, varps, entry.getValue()), known, remaining));
		}
		return Collections.unmodifiableMap(result);
	}

	/** VarbitChanged carries either a varbit or its parent varp, depending on the update source. */
	public boolean isProgressChange(Client client, int varp, int varbit)
	{
		checkThread(client);
		if (COLLECTION_VARBITS.contains(varbit)) return true;
		if (varp < 0 && varbit >= 0 && client.getGameState() == GameState.LOGGED_IN)
		{
			VarbitComposition definition = client.getVarbit(varbit);
			if (definition != null) varp = definition.getIndex();
		}
		for (int word : TASK_WORDS)
		{
			if (word == varp) return true;
		}
		if (client.getGameState() == GameState.LOGGED_IN && client.getVarps() != null)
		{
			loadMetadata(client);
		}
		return collectionParents.containsValue(varp);
	}

	private Boolean greenLogged(Client client, int[] varps, List<Integer> varbits)
	{
		boolean complete = true;
		for (int varbit : varbits)
		{
			Integer parent = collectionParents.get(varbit);
			if (parent == null || parent >= varps.length) return null;
			int value = client.getVarbitValue(varbit);
			if (value != 0 && value != 1) return null;
			complete &= value == 1;
		}
		return complete;
	}

	private void loadMetadata(Client client)
	{
		if (metadataLoaded) return;
		metadataLoaded = true;
		Map<Integer, Integer> parents = new HashMap<>();
		for (int varbit : COLLECTION_VARBITS)
		{
			try
			{
				VarbitComposition definition = client.getVarbit(varbit);
				if (definition == null || definition.getIndex() < 0
					|| definition.getLeastSignificantBit() < 0 || definition.getMostSignificantBit() > 31
					|| definition.getLeastSignificantBit() != definition.getMostSignificantBit())
				{
					throw new IllegalStateException("Missing or invalid boolean varbit " + varbit);
				}
				parents.put(varbit, definition.getIndex());
			}
			catch (RuntimeException exception)
			{
				log.warn("Cannot read boss collection metadata for varbit {}", varbit, exception);
			}
		}
		collectionParents = Map.copyOf(parents);
		try
		{
			tasks = loadTasks(client);
		}
		catch (RuntimeException exception)
		{
			// Never publish a partial enumeration as a complete list of remaining tasks.
			log.warn("Cannot read complete combat achievement metadata; boss tasks remain unknown", exception);
		}
	}

	private static Map<String, List<CombatAchievementTask>> loadTasks(Client client)
	{
		EnumComposition bossEnum = client.getEnum(BOSS_NAMES_ENUM);
		if (bossEnum == null || bossEnum.getKeys() == null || bossEnum.getStringVals() == null
			|| bossEnum.size() == 0 || bossEnum.getKeys().length != bossEnum.size()
			|| bossEnum.getStringVals().length != bossEnum.size())
		{
			throw new IllegalStateException("Missing or incomplete boss-name enum " + BOSS_NAMES_ENUM);
		}
		Map<Integer, String> bossNames = new HashMap<>();
		for (int i = 0; i < bossEnum.size(); i++)
		{
			String name = bossEnum.getStringVals()[i];
			if (!validText(name) || bossNames.put(bossEnum.getKeys()[i], name) != null)
			{
				throw new IllegalStateException("Invalid boss-name enum entry " + i);
			}
		}
		Map<String, List<CombatAchievementTask>> result = new LinkedHashMap<>();
		Set<Integer> taskIds = new HashSet<>();
		Set<String> mappedNames = new HashSet<>();
		for (int tier = 1; tier <= 6; tier++)
		{
			int enumId = FIRST_TIER_ENUM + tier - 1;
			EnumComposition tierEnum = client.getEnum(enumId);
			if (tierEnum == null || tierEnum.getIntVals() == null || tierEnum.size() == 0
				|| tierEnum.getIntVals().length != tierEnum.size())
			{
				throw new IllegalStateException("Missing or incomplete combat tier enum " + enumId);
			}
			for (int structId : tierEnum.getIntVals())
			{
				StructComposition struct = client.getStructComposition(structId);
				if (struct == null) throw new IllegalStateException("Missing combat task struct " + structId);
				int id = struct.getIntValue(TASK_ID);
				String name = struct.getStringValue(TASK_NAME);
				String description = struct.getStringValue(TASK_DESCRIPTION);
				String bossName = bossNames.get(struct.getIntValue(TASK_BOSS));
				if (id < 0 || id / 32 >= TASK_WORDS.length || !taskIds.add(id)
					|| !validText(name) || !validText(description) || bossName == null)
				{
					throw new IllegalStateException("Invalid combat task metadata or unsupported completion word: struct " + structId);
				}
				String boss = modeledBoss(bossName);
				if (boss != null)
				{
					mappedNames.add(normalize(bossName));
					result.computeIfAbsent(boss, ignored -> new ArrayList<>())
						.add(new CombatAchievementTask(id, name, description, tier));
				}
			}
		}
		// The aggregate must not silently become "complete" if a component boss disappeared.
		if (!mappedNames.containsAll(Set.of("general graardor", "commander zilyana", "kree'arra", "k'ril tsutsaroth")))
		{
			result.remove("boss:god-wars-dungeon");
		}
		if (!mappedNames.contains("dagannoth kings")
			&& !mappedNames.containsAll(Set.of("dagannoth rex", "dagannoth prime", "dagannoth supreme")))
		{
			result.remove("boss:dagannoth-kings");
		}
		for (String boss : COLLECTIONS.keySet())
		{
			if (!result.containsKey(boss)) log.warn("No complete combat achievement metadata mapped for {}", boss);
		}
		result.replaceAll((boss, list) -> List.copyOf(list));
		return Collections.unmodifiableMap(result);
	}

	private static boolean validText(String text)
	{
		return text != null && !text.isBlank() && !text.equalsIgnoreCase("null");
	}

	private static String normalize(String name)
	{
		return name.toLowerCase(Locale.ROOT).replace('\u2019', '\'').trim();
	}

	private static String modeledBoss(String name)
	{
		String normalized = normalize(name);
		if (raidName(normalized, "chambers of xeric")) return "boss:chambers-of-xeric";
		if (raidName(normalized, "theatre of blood")) return "boss:theatre-of-blood";
		if (raidName(normalized, "tombs of amascut")) return "boss:tombs-of-amascut";
		switch (normalized)
		{
			case "barrows": return "boss:barrows";
			case "perilous moons":
			case "moons of peril": return "boss:moons-of-peril";
			case "zulrah": return "boss:zulrah";
			case "vorkath": return "boss:vorkath";
			case "general graardor":
			case "commander zilyana":
			case "kree'arra":
			case "k'ril tsutsaroth": return "boss:god-wars-dungeon";
			case "dagannoth kings":
			case "dagannoth rex":
			case "dagannoth prime":
			case "dagannoth supreme": return "boss:dagannoth-kings";
			case "the gauntlet":
			case "gauntlet": return "boss:gauntlet";
			case "the corrupted gauntlet":
			case "corrupted gauntlet": return "boss:corrupted-gauntlet";
			case "phantom muspah": return "boss:phantom-muspah";
			case "nex": return "boss:nex";
			case "duke sucellus": return "boss:duke-sucellus";
			case "the whisperer":
			case "whisperer": return "boss:the-whisperer";
			case "vardorvis": return "boss:vardorvis";
			case "the leviathan":
			case "leviathan": return "boss:the-leviathan";
			case "zalcano": return "boss:zalcano";
			default: return null;
		}
	}

	private static boolean raidName(String name, String base)
	{
		return name.equals(base) || name.startsWith(base + ":") || name.startsWith(base + " (");
	}

	private static Map<String, List<Integer>> collections()
	{
		Map<String, List<Integer>> result = new LinkedHashMap<>();
		result.put("boss:barrows", List.of(VarbitID.COLLECTION_BOSSES_BARROWS_COMPLETED));
		result.put("boss:moons-of-peril", List.of(VarbitID.COLLECTION_BOSSES_PERILOUS_MOONS_COMPLETED));
		result.put("boss:zulrah", List.of(VarbitID.COLLECTION_BOSSES_ZULRAH_COMPLETED));
		result.put("boss:vorkath", List.of(VarbitID.COLLECTION_BOSSES_VORKATH_COMPLETED));
		result.put("boss:god-wars-dungeon", List.of(VarbitID.COLLECTION_BOSSES_BANDOS_COMPLETED,
			VarbitID.COLLECTION_BOSSES_SARADOMIN_COMPLETED, VarbitID.COLLECTION_BOSSES_ARMADYL_COMPLETED,
			VarbitID.COLLECTION_BOSSES_ZAMORAK_COMPLETED));
		result.put("boss:dagannoth-kings", List.of(VarbitID.COLLECTION_BOSSES_DAGANNOTH_COMPLETED));
		result.put("boss:gauntlet", List.of(VarbitID.COLLECTION_BOSSES_GAUNTLET_COMPLETED));
		result.put("boss:corrupted-gauntlet", List.of(VarbitID.COLLECTION_BOSSES_GAUNTLET_COMPLETED));
		result.put("boss:phantom-muspah", List.of(VarbitID.COLLECTION_BOSSES_MUSPAH_COMPLETED));
		result.put("boss:nex", List.of(VarbitID.COLLECTION_BOSSES_NEX_COMPLETED));
		result.put("boss:duke-sucellus", List.of(VarbitID.COLLECTION_BOSSES_DUKE_COMPLETED));
		result.put("boss:the-whisperer", List.of(VarbitID.COLLECTION_BOSSES_WHISPERER_COMPLETED));
		result.put("boss:vardorvis", List.of(VarbitID.COLLECTION_BOSSES_VARDORVIS_COMPLETED));
		result.put("boss:the-leviathan", List.of(VarbitID.COLLECTION_BOSSES_LEVIATHAN_COMPLETED));
		result.put("boss:zalcano", List.of(VarbitID.COLLECTION_BOSSES_ZALCANO_COMPLETED));
		result.put("boss:chambers-of-xeric", List.of(VarbitID.COLLECTION_RAIDS_COX_COMPLETED));
		result.put("boss:theatre-of-blood", List.of(VarbitID.COLLECTION_RAIDS_TOB_COMPLETED));
		result.put("boss:tombs-of-amascut", List.of(VarbitID.COLLECTION_RAIDS_TOA_COMPLETED));
		return Collections.unmodifiableMap(result);
	}

	private static Set<Integer> collectionVarbits()
	{
		Set<Integer> result = new HashSet<>();
		COLLECTIONS.values().forEach(result::addAll);
		return Set.copyOf(result);
	}

	private static void checkThread(Client client)
	{
		if (!client.isClientThread()) throw new IllegalStateException("BossProgressReader must run on the client thread");
	}
}
