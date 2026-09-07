package dev.reece.nta.store;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Pure functions that produce a new {@link AccountData} with one field changed, via
 * {@link AccountData#copy()} - the input is never mutated. Used by the plugin's panel actions
 * (S4 ruling 19) so the actual mutation is a plain, independently-testable data transform; the
 * executor/thread wiring around it (serialising every call, saving, re-running the engine) lives
 * in {@code NextTargetPlugin#mutateAccountData}.
 */
public final class AccountDataMutations
{
	private AccountDataMutations()
	{
	}

	public static AccountData snooze(AccountData data, String goalId, Instant until, String gapFingerprint)
	{
		AccountData copy = data.copy();
		copy.getSnoozes().put(goalId, new Snooze(until, gapFingerprint));
		return copy;
	}

	public static AccountData unsnooze(AccountData data, String goalId)
	{
		AccountData copy = data.copy();
		copy.getSnoozes().remove(goalId);
		return copy;
	}

	public static AccountData ignore(AccountData data, String goalId)
	{
		AccountData copy = data.copy();
		copy.getIgnores().add(goalId);
		return copy;
	}

	public static AccountData unignore(AccountData data, String goalId)
	{
		AccountData copy = data.copy();
		copy.getIgnores().remove(goalId);
		return copy;
	}

	/** Idempotent: pinning an already-pinned goal doesn't move it within the pin order. */
	public static AccountData pin(AccountData data, String goalId)
	{
		AccountData copy = data.copy();
		if (!copy.getPins().contains(goalId))
		{
			copy.getPins().add(goalId);
		}
		return copy;
	}

	public static AccountData unpin(AccountData data, String goalId)
	{
		AccountData copy = data.copy();
		copy.getPins().remove(goalId);
		return copy;
	}

	public static AccountData focus(AccountData data, String goalId)
	{
		AccountData copy = data.copy();
		copy.setFocusGoalId(goalId);
		return copy;
	}

	public static AccountData clearFocus(AccountData data)
	{
		AccountData copy = data.copy();
		copy.setFocusGoalId(null);
		return copy;
	}

	public static AccountData bank(AccountData data, Map<Integer, Integer> items, Instant asOf)
	{
		AccountData copy = data.copy();
		copy.setBank(new HashMap<>(items));
		copy.setBankAsOf(asOf);
		return copy;
	}
}
