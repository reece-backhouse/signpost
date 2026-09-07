package dev.reece.nta.store;

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

	@Test
	void savedDataRoundTripsThroughLoad()
	{
		AccountData data = new AccountData(
			Map.of(995, 1000, 1511, 28),
			Instant.parse("2026-09-01T12:00:00Z"),
			Map.of("giant_mole", new Snooze(Instant.parse("2026-09-08T00:00:00Z"), "abc123")),
			Set.of("callisto"),
			List.of("barrows gloves", "fire cape"),
			"quest-points");

		store.save(1234L, data);

		assertEquals(data, store.load(1234L));
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
