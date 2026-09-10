package com.signpost.store;

import com.google.gson.Gson;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AccountStoreTest
{
	@TempDir
	Path dir;

	private AccountStore store;

	@BeforeEach
	void setUp()
	{
		store = new AccountStore(dir, new Gson());
	}

	/** Gson leaves omitted collections null; load must hand back something copy() and every mutation can use. */
	@Test
	void fileOmittingCollectionFieldsLoadsWithEmptyCollections() throws Exception
	{
		Files.createDirectories(dir);
		Files.writeString(dir.resolve("42.json"), "{\"focusGoalId\":\"quest:1\"}");

		AccountData data = store.load(42L);

		assertEquals("quest:1", data.getFocusGoalId());
		assertEquals(Map.of(), data.getBank());
		assertEquals(Map.of(), data.getSnoozes());
		assertEquals(Set.of(), data.getIgnores());
		assertEquals(List.of(), data.getPins());
		assertEquals(Set.of(), data.getOwnedManually());
		assertEquals(data, data.copy());
	}

	@Test
	void savedDataRoundTripsThroughLoad()
	{
		AccountData data = new AccountData(
			Map.of(995, 1000, 1511, 28),
			Instant.parse("2026-09-01T12:00:00Z"),
			Map.of("giant_mole", new Snooze(Instant.parse("2026-09-08T00:00:00Z"), "abc123")),
			Set.of("callisto"),
			List.of("barrows gloves", "fire cape"),
			"quest-points",
			Set.of("milestone:barrows-gloves", "milestone:fire-cape"));

		store.save(1234L, data);

		assertEquals(data, store.load(1234L));
	}

	/** A version-1 file (no version field, no group storage) upgrades on load to version 2 with empty, unseen group storage. */
	@Test
	void versionOneFileUpgradesToVersionTwoWithEmptyGroupStorage() throws Exception
	{
		Files.createDirectories(dir);
		Files.writeString(dir.resolve("7.json"), "{\"bank\":{\"995\":10},\"bankAsOf\":\"2026-09-01T12:00:00Z\",\"focusGoalId\":\"quest:1\"}");

		AccountData data = store.load(7L);

		assertEquals(2, data.getVersion());
		assertEquals(Map.of(), data.getGroupStorage());
		assertEquals(null, data.getGroupStorageAsOf());
		assertEquals(false, data.toCachedGroupStorage().isKnown());
		assertEquals(Map.of(995, 10), data.getBank());
		assertEquals("quest:1", data.getFocusGoalId());
	}

	/** Group storage round-trips with its asOf; an account that never saw it writes no groupStorageAsOf. */
	@Test
	void groupStorageRoundTripsAndUnseenStorageWritesNoAsOf() throws Exception
	{
		AccountData seen = AccountDataMutations.groupStorage(AccountData.empty(), Map.of(4151, 2), Instant.parse("2026-09-08T10:00:00Z"));
		store.save(8L, seen);
		assertEquals(seen, store.load(8L));
		assertEquals(Map.of(4151, 2), store.load(8L).getGroupStorage());

		store.save(9L, AccountData.empty());
		String json = Files.readString(dir.resolve("9.json"));
		assertEquals(false, json.contains("groupStorageAsOf"), json);
		assertEquals(true, json.contains("\"version\":2"), json);
	}

	@Test
	void loadOfMissingFileReturnsEmpty()
	{
		assertEquals(AccountData.empty(), store.load(999L));
	}

	@Test
	void loadOfCorruptFileReturnsEmptyAndLogsWarning() throws Exception
	{
		Files.writeString(dir.resolve("555.json"), "{ not valid json ]");

		assertEquals(AccountData.empty(), store.load(555L));
	}
}
