package dev.reece.nta.store;

import dev.reece.nta.snapshot.CachedBank;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AccountDataTest
{
	@Test
	void toCachedBankIsKnownOnlyWhenBankAndAsOfArePresent()
	{
		AccountData empty = AccountData.empty();
		assertFalse(empty.toCachedBank().isKnown());

		AccountData withBank = new AccountData(
			Map.of(995, 100), Instant.parse("2026-09-01T00:00:00Z"), Map.of(), Set.of(), List.of(), null);
		CachedBank cachedBank = withBank.toCachedBank();

		assertEquals(Map.of(995, 100), cachedBank.getItems());
		assertEquals(Instant.parse("2026-09-01T00:00:00Z"), cachedBank.getAsOf());
	}

	@Test
	void copyIsNotAffectedByMutatingTheOriginal()
	{
		AccountData original = new AccountData(
			new HashMap<>(Map.of(995, 100)),
			Instant.parse("2026-09-01T00:00:00Z"),
			new HashMap<>(),
			new HashSet<>(),
			new ArrayList<>(),
			null);

		AccountData copy = original.copy();

		original.getBank().put(996, 5);
		original.setBank(new HashMap<>(Map.of(1, 1)));
		original.setBankAsOf(Instant.parse("2026-09-02T00:00:00Z"));

		assertEquals(Map.of(995, 100), copy.getBank());
		assertEquals(Instant.parse("2026-09-01T00:00:00Z"), copy.getBankAsOf());
	}
}
