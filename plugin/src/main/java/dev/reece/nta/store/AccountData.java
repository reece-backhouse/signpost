package dev.reece.nta.store;

import dev.reece.nta.snapshot.CachedBank;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The per-account data persisted by {@link AccountStore}: the last-known bank plus user prefs
 * (snoozes, ignores, pins, focus goal) that survive between client sessions.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountData
{
	private Map<Integer, Integer> bank;
	private Instant bankAsOf;
	private Map<String, Snooze> snoozes;
	private Set<String> ignores;
	private List<String> pins;
	private String focusGoalId;

	public static AccountData empty()
	{
		return new AccountData(new HashMap<>(), null, new HashMap<>(), new HashSet<>(), new ArrayList<>(), null);
	}

	public CachedBank toCachedBank()
	{
		boolean known = bank != null && bankAsOf != null;
		return known ? new CachedBank(bank, bankAsOf, true) : CachedBank.unknown();
	}

	/**
	 * A snapshot independent of this instance: later mutating (or replacing) any of this
	 * instance's fields does not affect the returned copy. Used to hand a stable value to a
	 * background thread while the live instance keeps being updated on the client thread.
	 */
	public AccountData copy()
	{
		return new AccountData(
			new HashMap<>(bank),
			bankAsOf,
			new HashMap<>(snoozes),
			new HashSet<>(ignores),
			new ArrayList<>(pins),
			focusGoalId);
	}
}
