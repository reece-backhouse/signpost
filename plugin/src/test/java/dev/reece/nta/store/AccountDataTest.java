package dev.reece.nta.store;

import dev.reece.nta.snapshot.CachedBank;
import java.time.Instant;
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
}
