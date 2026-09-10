package com.signpost.snapshot;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.GameState;
import net.runelite.api.StructComposition;
import net.runelite.api.VarbitComposition;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BossProgressReaderTest
{
	@Test
	void completionUsesTheSignBitAndNoncontiguousWordsIncludingTheLatestWord()
	{
		Fixture fixture = new Fixture();
		BossProgressReader reader = new BossProgressReader();
		fixture.varps[VarPlayerID.CA_TASK_COMPLETED_0] = Integer.MIN_VALUE;
		fixture.varps[VarPlayerID.CA_TASK_COMPLETED_13] = 1;
		BossProgress moons = reader.read(fixture.client).get("boss:moons-of-peril");
		assertTrue(moons.isCombatAchievementsKnown());
		assertEquals(List.of(new CombatAchievementTask(640, "Task 640", "Description 640", 3)), moons.getRemainingTasks());

		fixture.varps[VarPlayerID.CA_TASK_COMPLETED_20] = 1;
		assertTrue(reader.read(fixture.client).get("boss:moons-of-peril").getRemainingTasks().isEmpty());
		fixture.varps[VarPlayerID.CA_TASK_COMPLETED_0] = 0;
		assertEquals(List.of(new CombatAchievementTask(31, "Task 31", "Description 31", 1)),
			reader.read(fixture.client).get("boss:moons-of-peril").getRemainingTasks());
	}

	@Test
	void allModeledBossesHaveTasksAndModesStayWithTheCorrectBoss()
	{
		Fixture fixture = new Fixture();
		Map<String, BossProgress> progress = new BossProgressReader().read(fixture.client);
		for (String boss : List.of("barrows", "moons-of-peril", "zulrah", "vorkath", "god-wars-dungeon",
			"dagannoth-kings", "gauntlet", "corrupted-gauntlet", "phantom-muspah", "nex", "duke-sucellus",
			"the-whisperer", "vardorvis", "the-leviathan", "zalcano", "chambers-of-xeric", "theatre-of-blood", "tombs-of-amascut"))
		{
			assertTrue(progress.get("boss:" + boss).isCombatAchievementsKnown(), boss);
		}
		assertEquals(List.of(fixture.taskFor("The Gauntlet")), progress.get("boss:gauntlet").getRemainingTasks());
		assertEquals(List.of(fixture.taskFor("The Corrupted Gauntlet")), progress.get("boss:corrupted-gauntlet").getRemainingTasks());
		assertTrue(progress.get("boss:chambers-of-xeric").getRemainingTasks().contains(fixture.taskFor("Chambers of Xeric: Challenge Mode")));
		assertTrue(progress.get("boss:theatre-of-blood").getRemainingTasks().contains(fixture.taskFor("Theatre of Blood: Hard Mode")));
		assertTrue(progress.get("boss:tombs-of-amascut").getRemainingTasks().contains(fixture.taskFor("Tombs of Amascut: Expert Mode")));
		assertTrue(progress.get("boss:god-wars-dungeon").getRemainingTasks().containsAll(List.of(
			fixture.taskFor("General Graardor"), fixture.taskFor("Commander Zilyana"),
			fixture.taskFor("Kree'arra"), fixture.taskFor("K'ril Tsutsaroth"))));
	}

	@Test
	void incompleteMetadataNeverClaimsThereAreNoRemainingAchievements()
	{
		for (int failure = 0; failure < 4; failure++)
		{
			Fixture fixture = new Fixture();
			if (failure == 0) fixture.enums.remove(3986);
			if (failure == 1) fixture.structs.remove(1000);
			if (failure == 2) fixture.structs.put(1000, taskStruct(672, 1)); // Missing word 21.
			if (failure == 3) fixture.enums.put(3981, intEnum(new int[]{1000}, 2));
			fixture.greens.put(VarbitID.COLLECTION_BOSSES_PERILOUS_MOONS_COMPLETED, 1);
			BossProgress moons = new BossProgressReader().read(fixture.client).get("boss:moons-of-peril");
			assertEquals(Boolean.TRUE, moons.getGreenLogged());
			assertFalse(moons.isCombatAchievementsKnown(), "Failure " + failure);
			assertEquals(List.of(), moons.getRemainingTasks());
		}
	}

	@Test
	void unavailableAccountWordIsUnknownRatherThanUnfinished()
	{
		Fixture fixture = new Fixture();
		fixture.varps = new int[5000];
		Map<String, BossProgress> progress = new BossProgressReader().read(fixture.client);
		assertFalse(progress.get("boss:moons-of-peril").isCombatAchievementsKnown());
		assertEquals(List.of(), progress.get("boss:moons-of-peril").getRemainingTasks());
		assertTrue(progress.get("boss:barrows").isCombatAchievementsKnown());
	}

	@Test
	void collectionCompletionRequiresEveryGodWarsLogAndGauntletModesShareOneLog()
	{
		Fixture fixture = new Fixture();
		BossProgressReader reader = new BossProgressReader();
		fixture.greens.put(VarbitID.COLLECTION_BOSSES_BANDOS_COMPLETED, 1);
		fixture.greens.put(VarbitID.COLLECTION_BOSSES_SARADOMIN_COMPLETED, 1);
		fixture.greens.put(VarbitID.COLLECTION_BOSSES_ARMADYL_COMPLETED, 1);
		assertEquals(Boolean.FALSE, reader.read(fixture.client).get("boss:god-wars-dungeon").getGreenLogged());
		fixture.greens.put(VarbitID.COLLECTION_BOSSES_ZAMORAK_COMPLETED, 1);
		fixture.greens.put(VarbitID.COLLECTION_BOSSES_GAUNTLET_COMPLETED, 1);
		Map<String, BossProgress> progress = reader.read(fixture.client);
		assertEquals(Boolean.TRUE, progress.get("boss:god-wars-dungeon").getGreenLogged());
		assertEquals(Boolean.TRUE, progress.get("boss:gauntlet").getGreenLogged());
		assertEquals(Boolean.TRUE, progress.get("boss:corrupted-gauntlet").getGreenLogged());
		fixture.greens.put(VarbitID.COLLECTION_BOSSES_GAUNTLET_COMPLETED, 2);
		assertNull(reader.read(fixture.client).get("boss:gauntlet").getGreenLogged());
	}

	@Test
	void logoutAndAnotherAccountCannotReuseCompletionFlags()
	{
		Fixture fixture = new Fixture();
		BossProgressReader reader = new BossProgressReader();
		fixture.greens.put(VarbitID.COLLECTION_BOSSES_PERILOUS_MOONS_COMPLETED, 1);
		fixture.varps[VarPlayerID.CA_TASK_COMPLETED_0] = Integer.MIN_VALUE;
		fixture.varps[VarPlayerID.CA_TASK_COMPLETED_13] = 1;
		fixture.varps[VarPlayerID.CA_TASK_COMPLETED_20] = 1;
		Map<String, BossProgress> first = reader.read(fixture.client);
		fixture.state = GameState.LOGIN_SCREEN;
		BossProgress loggedOut = reader.read(fixture.client).get("boss:moons-of-peril");
		assertNull(loggedOut.getGreenLogged());
		assertFalse(loggedOut.isCombatAchievementsKnown());
		fixture.varps = new int[6000];
		fixture.greens.clear();
		fixture.state = GameState.LOGGED_IN;
		// Metadata is cache data, not account data: it need not be fetched again after login.
		fixture.cacheAvailable = false;
		BossProgress second = reader.read(fixture.client).get("boss:moons-of-peril");
		assertEquals(Boolean.FALSE, second.getGreenLogged());
		assertTrue(second.isCombatAchievementsKnown());
		assertEquals(List.of(31, 416, 640), second.getRemainingTasks().stream()
			.map(CombatAchievementTask::getId).collect(java.util.stream.Collectors.toList()));
		assertEquals(Boolean.TRUE, first.get("boss:moons-of-peril").getGreenLogged());
		assertTrue(first.get("boss:moons-of-peril").getRemainingTasks().isEmpty());
	}

	@Test
	void missingCollectionMetadataIsUnknownAndBothEventShapesRequestRefresh()
	{
		Fixture fixture = new Fixture();
		fixture.missingCollection = VarbitID.COLLECTION_BOSSES_PERILOUS_MOONS_COMPLETED;
		BossProgressReader reader = new BossProgressReader();
		assertNull(reader.read(fixture.client).get("boss:moons-of-peril").getGreenLogged());
		assertTrue(reader.isProgressChange(fixture.client, -1, VarbitID.COLLECTION_BOSSES_PERILOUS_MOONS_COMPLETED));
		assertTrue(reader.isProgressChange(fixture.client, Fixture.COLLECTION_PARENT, -1));
		assertTrue(reader.isProgressChange(fixture.client, VarPlayerID.CA_TASK_COMPLETED_20, -1));
		assertFalse(reader.isProgressChange(fixture.client, 999999, -1));
	}

	@Test
	void taskVarbitUpdatesRefreshEvenWhenTheEventDoesNotNameItsParentWord()
	{
		Fixture fixture = new Fixture();
		fixture.eventParents.put(90001, VarPlayerID.CA_TASK_COMPLETED_17);
		BossProgressReader reader = new BossProgressReader();
		reader.read(fixture.client);
		assertTrue(reader.isProgressChange(fixture.client, -1, 90001));
	}

	@Test
	void immutableProgressCannotBeChangedByTheCaller()
	{
		List<CombatAchievementTask> source = new ArrayList<>();
		source.add(new CombatAchievementTask(31, "Task", "Description", 1));
		BossProgress progress = new BossProgress(true, true, source);
		source.clear();
		assertEquals(List.of(new CombatAchievementTask(31, "Task", "Description", 1)), progress.getRemainingTasks());
		assertThrows(UnsupportedOperationException.class, () -> progress.getRemainingTasks().clear());
		Map<String, BossProgress> map = new HashMap<>();
		map.put("boss:moons-of-peril", progress);
		Snapshot snapshot = Snapshot.builder().bossProgress(map).build();
		map.clear();
		assertEquals(progress, snapshot.getBossProgress().get("boss:moons-of-peril"));
		assertThrows(UnsupportedOperationException.class, () -> snapshot.getBossProgress().clear());
	}

	private static final class Fixture
	{
		private static final int COLLECTION_PARENT = 100;
		private final Map<Integer, Integer> greens = new HashMap<>();
		private final Map<Integer, Integer> eventParents = new HashMap<>();
		private final Map<Integer, EnumComposition> enums = new HashMap<>();
		private final Map<Integer, StructComposition> structs = new HashMap<>();
		private final Map<String, CombatAchievementTask> namedTasks = new HashMap<>();
		private int[] varps = new int[6000];
		private GameState state = GameState.LOGGED_IN;
		private boolean cacheAvailable = true;
		private int missingCollection = -1;
		private final Client client = stub(Client.class, (method, args) ->
		{
			switch (method)
			{
				case "isClientThread": return true;
				case "getGameState": return state;
				case "getVarps": return varps;
				case "getVarbitValue": return greens.getOrDefault(args[0], 0);
				case "getEnum":
					if (!cacheAvailable) throw new AssertionError("Metadata should survive account changes");
					return enums.get(args[0]);
				case "getStructComposition":
					if (!cacheAvailable) throw new AssertionError("Metadata should survive account changes");
					return structs.get(args[0]);
				case "getVarbit":
					if (!cacheAvailable) throw new AssertionError("Metadata should survive account changes");
					if (args[0].equals(missingCollection)) return null;
					return stub(VarbitComposition.class, (field, ignored) -> field.equals("getIndex")
						? eventParents.getOrDefault(args[0], COLLECTION_PARENT) : 0);
				default: throw new AssertionError("Unexpected client read: " + method);
			}
		});

		private Fixture()
		{
			String[] names = {"Perilous Moons", "Moons of Peril", "Perilous Moons", "Barrows", "Zulrah", "Vorkath",
				"General Graardor", "Commander Zilyana", "Kree'arra", "K'ril Tsutsaroth", "Dagannoth Kings",
				"The Gauntlet", "The Corrupted Gauntlet", "Phantom Muspah", "Nex", "Duke Sucellus", "The Whisperer",
				"Vardorvis", "The Leviathan", "Zalcano", "Chambers of Xeric", "Chambers of Xeric: Challenge Mode",
				"Theatre of Blood", "Theatre of Blood: Hard Mode", "Tombs of Amascut", "Tombs of Amascut: Expert Mode"};
			int[] keys = new int[names.length];
			List<List<Integer>> tiers = new ArrayList<>();
			for (int i = 0; i < 6; i++) tiers.add(new ArrayList<>());
			for (int i = 0; i < names.length; i++)
			{
				keys[i] = i + 1;
				int taskId = i == 0 ? 31 : i == 1 ? 416 : i == 2 ? 640 : 100 + i;
				structs.put(1000 + i, taskStruct(taskId, keys[i]));
				tiers.get(i % 6).add(1000 + i);
				namedTasks.put(names[i], new CombatAchievementTask(taskId, "Task " + taskId, "Description " + taskId, i % 6 + 1));
			}
			enums.put(3971, stub(EnumComposition.class, (method, args) ->
			{
				switch (method)
				{
					case "size": return keys.length;
					case "getKeys": return keys;
					case "getStringVals": return names;
					default: throw new AssertionError("Unexpected boss enum read: " + method);
				}
			}));
			for (int i = 0; i < 6; i++)
			{
				int[] ids = tiers.get(i).stream().mapToInt(Integer::intValue).toArray();
				enums.put(3981 + i, intEnum(ids, ids.length));
			}
		}

		private CombatAchievementTask taskFor(String boss)
		{
			return namedTasks.get(boss);
		}
	}

	private static StructComposition taskStruct(int id, int bossId)
	{
		return stub(StructComposition.class, (method, args) ->
		{
			if (method.equals("getIntValue")) return args[0].equals(1306) ? id : bossId;
			if (method.equals("getStringValue")) return args[0].equals(1308) ? "Task " + id : "Description " + id;
			throw new AssertionError("Unexpected task struct read: " + method);
		});
	}

	private static EnumComposition intEnum(int[] ids, int size)
	{
		return stub(EnumComposition.class, (method, args) ->
		{
			if (method.equals("getIntVals")) return ids;
			if (method.equals("size")) return size;
			throw new AssertionError("Unexpected tier enum read: " + method);
		});
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
