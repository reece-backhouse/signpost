package dev.reece.nta.engine;

import dev.reece.nta.engine.model.GoalStatus;
import dev.reece.nta.engine.model.PrefsView;
import dev.reece.nta.store.AccountData;
import dev.reece.nta.store.Snooze;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves a player's persisted {@link AccountData} preferences (snoozes/ignores/pins/focus)
 * against the current {@link GoalStatus}es into a {@link PrefsView}. A snooze is active iff
 * {@code now} is before its {@code until} <em>and</em> its stored {@code gapFingerprint} still
 * matches {@link GapFingerprint#of} for that goal (ticket D6: a changed gap ends the snooze
 * early); a goal that no longer appears among {@code statuses} can never match, so its snooze is
 * treated as expired. Pure: no {@link net.runelite.api.Client}, no I/O.
 */
public final class PrefsResolver
{
	public PrefsView resolve(AccountData data, List<GoalStatus> statuses, Instant now)
	{
		Map<String, GoalStatus> byId = new LinkedHashMap<>();
		for (GoalStatus status : statuses)
		{
			byId.put(status.getGoal().getId(), status);
		}

		Set<String> snoozedActive = new LinkedHashSet<>();
		Set<String> snoozedExpired = new LinkedHashSet<>();
		for (Map.Entry<String, Snooze> entry : data.getSnoozes().entrySet())
		{
			String goalId = entry.getKey();
			Snooze snooze = entry.getValue();
			GoalStatus status = byId.get(goalId);
			boolean fingerprintMatches = status != null && snooze.getGapFingerprint().equals(GapFingerprint.of(status));
			boolean timeActive = now.isBefore(snooze.getUntil());
			if (timeActive && fingerprintMatches)
			{
				snoozedActive.add(goalId);
			}
			else
			{
				snoozedExpired.add(goalId);
			}
		}

		Set<String> hidden = new LinkedHashSet<>(data.getIgnores());
		hidden.addAll(snoozedActive);

		return new PrefsView(hidden, List.copyOf(data.getPins()), snoozedActive, snoozedExpired, data.getFocusGoalId());
	}
}
