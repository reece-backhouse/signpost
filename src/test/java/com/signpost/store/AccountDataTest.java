package com.signpost.store;

import com.signpost.snapshot.CachedBank;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountDataTest
{
	@Test
	void toCachedBankIsKnownOnlyWhenBankAndAsOfArePresent()
	{
		AccountData empty = AccountData.empty();
		assertFalse(empty.toCachedBank().isKnown());

		AccountData withBank = new AccountData(
			Map.of(995, 100), Instant.parse("2026-09-01T00:00:00Z"), Map.of(), Set.of(), List.of(), null, Set.of());
		CachedBank cachedBank = withBank.toCachedBank();

		assertEquals(Map.of(995, 100), cachedBank.getItems());
		assertEquals(Instant.parse("2026-09-01T00:00:00Z"), cachedBank.getAsOf());
	}

	/** Group storage mirrors the bank - known only when both the map and its asOf are present. */
	@Test
	void toCachedGroupStorageIsKnownOnlyWhenMapAndAsOfArePresent()
	{
		assertFalse(AccountData.empty().toCachedGroupStorage().isKnown());

		AccountData data = AccountDataMutations.groupStorage(AccountData.empty(), Map.of(4151, 1), Instant.parse("2026-09-08T10:00:00Z"));
		CachedBank cached = data.toCachedGroupStorage();

		assertTrue(cached.isKnown());
		assertEquals(Map.of(4151, 1), cached.getItems());
		assertEquals(Instant.parse("2026-09-08T10:00:00Z"), cached.getAsOf());
		assertEquals(2, data.getVersion());
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
			null,
			new HashSet<>(Set.of("milestone:barrows-gloves")));

		AccountData copy = original.copy();

		original.getBank().put(996, 5);
		original.setBank(new HashMap<>(Map.of(1, 1)));
		original.setBankAsOf(Instant.parse("2026-09-02T00:00:00Z"));
		original.getOwnedManually().add("milestone:fire-cape");
		original.getGroupStorage().put(4151, 1);

		assertEquals(Map.of(995, 100), copy.getBank());
		assertEquals(Instant.parse("2026-09-01T00:00:00Z"), copy.getBankAsOf());
		assertEquals(Set.of("milestone:barrows-gloves"), copy.getOwnedManually(), "copy must not see a later mutation of the original's ownedManually set");
		assertEquals(Map.of(), copy.getGroupStorage(), "copy must not see a later mutation of the original's group storage");
	}
}
