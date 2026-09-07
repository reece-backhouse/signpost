package dev.reece.nta.store;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fix round 1: {@link AccountDataMutations} is the pure part of every plugin panel action - a
 * plain data transform that returns a new {@link AccountData} and never mutates its input. The
 * executor wiring that serialises calls to it lives in {@code NextTargetPlugin#mutateAccountData}
 * and isn't unit-tested (Swing/executor wiring, per the plan).
 */
class AccountDataMutationsTest
{
	private final Instant until = Instant.parse("2026-09-14T00:00:00Z");

	@Test
	void snoozeAddsAnEntryWithoutMutatingTheInput()
	{
		AccountData original = AccountData.empty();

		AccountData result = AccountDataMutations.snooze(original, "g1", until, "fp1");

		assertTrue(original.getSnoozes().isEmpty(), "input must not be mutated");
		assertEquals(new Snooze(until, "fp1"), result.getSnoozes().get("g1"));
	}

	@Test
	void unsnoozeRemovesTheEntry()
	{
		AccountData original = AccountData.empty();
		original.getSnoozes().put("g1", new Snooze(until, "fp1"));

		AccountData result = AccountDataMutations.unsnooze(original, "g1");

		assertTrue(original.getSnoozes().containsKey("g1"), "input must not be mutated");
		assertFalse(result.getSnoozes().containsKey("g1"));
	}

	@Test
	void ignoreAddsAndUnignoreRemoves()
	{
		AccountData empty = AccountData.empty();

		AccountData ignored = AccountDataMutations.ignore(empty, "g1");
		assertTrue(ignored.getIgnores().contains("g1"));
		assertFalse(empty.getIgnores().contains("g1"), "input must not be mutated");

		AccountData restored = AccountDataMutations.unignore(ignored, "g1");
		assertFalse(restored.getIgnores().contains("g1"));
	}

	@Test
	void pinIsIdempotentAndUnpinRemoves()
	{
		AccountData empty = AccountData.empty();

		AccountData pinnedOnce = AccountDataMutations.pin(empty, "g1");
		AccountData pinnedTwice = AccountDataMutations.pin(pinnedOnce, "g1");

		assertEquals(1, pinnedTwice.getPins().size(), "pinning twice must not duplicate");
		assertEquals("g1", pinnedTwice.getPins().get(0));

		AccountData unpinned = AccountDataMutations.unpin(pinnedTwice, "g1");
		assertFalse(unpinned.getPins().contains("g1"));
	}

	@Test
	void focusSetsAndClearFocusNulls()
	{
		AccountData empty = AccountData.empty();

		AccountData focused = AccountDataMutations.focus(empty, "g1");
		assertEquals("g1", focused.getFocusGoalId());
		assertNull(empty.getFocusGoalId(), "input must not be mutated");

		AccountData cleared = AccountDataMutations.clearFocus(focused);
		assertNull(cleared.getFocusGoalId());
	}

	@Test
	void bankReplacesItemsAndAsOfWithoutMutatingTheInput()
	{
		AccountData original = AccountData.empty();

		AccountData result = AccountDataMutations.bank(original, Map.of(995, 100), until);

		assertTrue(original.getBank().isEmpty(), "input must not be mutated");
		assertEquals(Map.of(995, 100), result.getBank());
		assertEquals(until, result.getBankAsOf());
	}
}
