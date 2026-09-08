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
 * (snoozes, ignores, pins, focus goal) that survive between client sessions. {@code version} is
 * the file format: 1 (implicit, no field) predates group storage; 2 (RL-003) adds
 * {@code groupStorage}/{@code groupStorageAsOf}, the group ironman shared storage cached like the bank.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountData
{
	public static final int CURRENT_VERSION = 2;

	private Map<Integer, Integer> bank;
	private Instant bankAsOf;
	private Map<String, Snooze> snoozes;
	private Set<String> ignores;
	private List<String> pins;
	private String focusGoalId;
	/** Goal ids marked "owned" by hand (ticket 55): the account has it but it lives somewhere the client can't see (a house cape rack; group storage only while "Count group storage" is off). */
	private Set<String> ownedManually;
	private Map<Integer, Integer> groupStorage;
	private Instant groupStorageAsOf;
	private int version;

	/** Version-1 shape (no group storage): kept for the many fixtures that build prefs by hand. */
	public AccountData(Map<Integer, Integer> bank, Instant bankAsOf, Map<String, Snooze> snoozes, Set<String> ignores, List<String> pins,
		String focusGoalId, Set<String> ownedManually)
	{
		this(bank, bankAsOf, snoozes, ignores, pins, focusGoalId, ownedManually, new HashMap<>(), null, CURRENT_VERSION);
	}

	public static AccountData empty()
	{
		return new AccountData(new HashMap<>(), null, new HashMap<>(), new HashSet<>(), new ArrayList<>(), null, new HashSet<>());
	}

	public CachedBank toCachedBank()
	{
		return toCached(bank, bankAsOf);
	}

	public CachedBank toCachedGroupStorage()
	{
		return toCached(groupStorage, groupStorageAsOf);
	}

	private static CachedBank toCached(Map<Integer, Integer> items, Instant asOf)
	{
		boolean known = items != null && asOf != null;
		return known ? new CachedBank(items, asOf, true) : CachedBank.unknown();
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
			focusGoalId,
			new HashSet<>(ownedManually),
			new HashMap<>(groupStorage),
			groupStorageAsOf,
			version);
	}
}
