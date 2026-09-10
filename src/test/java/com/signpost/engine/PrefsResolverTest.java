package com.signpost.engine;

import com.signpost.engine.model.CombatLevelGap;
import com.signpost.engine.model.Gap;
import com.signpost.engine.model.Goal;
import com.signpost.engine.model.GoalCategory;
import com.signpost.engine.model.GoalStatus;
import com.signpost.engine.model.PrefsView;
import com.signpost.store.AccountData;
import com.signpost.store.Snooze;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PrefsResolver} resolves persisted {@link AccountData} prefs against current
 * {@link GoalStatus}es, including time- and fingerprint-based snooze expiry.
 */
class PrefsResolverTest
{
	private final PrefsResolver resolver = new PrefsResolver();
	private final Instant now = Instant.parse("2026-09-07T12:00:00Z");

	@Test
	void snoozeExpiresByTimeEvenWithAMatchingFingerprint()
	{
		GoalStatus status = status("g1", List.of(new CombatLevelGap(50, 60, false)));
		String fingerprint = GapFingerprint.of(status);

		AccountData data = accountData();
		data.getSnoozes().put("g1", new Snooze(now.minusSeconds(1), fingerprint));

		PrefsView view = resolver.resolve(data, List.of(status), now);

		assertTrue(view.getSnoozedExpired().contains("g1"));
		assertFalse(view.getSnoozedActive().contains("g1"));
		assertFalse(view.getHidden().contains("g1"), "an expired snooze must not hide the goal");
	}

	@Test
	void snoozeExpiresEarlyWhenTheGapFingerprintChangesEvenBeforeItsTimeIsUp()
	{
		GoalStatus original = status("g1", List.of(new CombatLevelGap(50, 60, false)));
		String staleFingerprint = GapFingerprint.of(original);

		AccountData data = accountData();
		data.getSnoozes().put("g1", new Snooze(now.plusSeconds(60), staleFingerprint));

		// The requirement changed (need 60 -> 70): a different fingerprint, same goal id.
		GoalStatus changed = status("g1", List.of(new CombatLevelGap(50, 70, false)));

		PrefsView view = resolver.resolve(data, List.of(changed), now);

		assertTrue(view.getSnoozedExpired().contains("g1"));
		assertFalse(view.getSnoozedActive().contains("g1"));
		assertFalse(view.getHidden().contains("g1"));
	}

	@Test
	void snoozeStaysActiveWhenNotExpiredByTimeOrFingerprint()
	{
		GoalStatus status = status("g1", List.of(new CombatLevelGap(50, 60, false)));
		String fingerprint = GapFingerprint.of(status);

		AccountData data = accountData();
		data.getSnoozes().put("g1", new Snooze(now.plusSeconds(60), fingerprint));

		PrefsView view = resolver.resolve(data, List.of(status), now);

		assertTrue(view.getSnoozedActive().contains("g1"));
		assertFalse(view.getSnoozedExpired().contains("g1"));
		assertTrue(view.getHidden().contains("g1"));
	}

	@Test
	void ignoredGoalsAreAlwaysHiddenRegardlessOfSnoozeState()
	{
		GoalStatus status = status("g1", List.of());
		AccountData data = accountData();
		data.getIgnores().add("g1");

		PrefsView view = resolver.resolve(data, List.of(status), now);

		assertTrue(view.getHidden().contains("g1"));
	}

	@Test
	void pinsArePassedThroughUnchanged()
	{
		AccountData data = accountData();
		data.getPins().add("g2");
		data.getPins().add("g1");

		PrefsView view = resolver.resolve(data, List.of(status("g1", List.of()), status("g2", List.of())), now);

		assertEquals(List.of("g2", "g1"), view.getPins());
	}

	@Test
	void focusGoalIdIsPassedThroughUnchanged()
	{
		AccountData data = accountData();
		data.setFocusGoalId("g1");

		PrefsView view = resolver.resolve(data, List.of(status("g1", List.of())), now);

		assertEquals("g1", view.getFocusGoalId());
	}

	@Test
	void aSnoozedGoalNoLongerPresentInStatusesIsTreatedAsExpired()
	{
		AccountData data = accountData();
		data.getSnoozes().put("gone", new Snooze(now.plusSeconds(60), "anything"));

		PrefsView view = resolver.resolve(data, List.of(), now);

		assertTrue(view.getSnoozedExpired().contains("gone"));
		assertFalse(view.getHidden().contains("gone"));
	}

	/** A snooze taken before any Advice existed has no fingerprint; it must still hide the goal until its time is up. */
	@Test
	void snoozeWithNoFingerprintIsActiveByTimeAlone()
	{
		AccountData data = accountData();
		data.getSnoozes().put("g", new Snooze(now.plusSeconds(60), null));
		GoalStatus g = status("g", List.of(new CombatLevelGap(3, 50, false)));

		PrefsView view = resolver.resolve(data, List.of(g), now);

		assertTrue(view.getSnoozedActive().contains("g"));
		assertTrue(view.getHidden().contains("g"));
		assertTrue(resolver.resolve(data, List.of(g), now.plusSeconds(61)).getSnoozedExpired().contains("g"));
	}

	/** A hand-edited file with no "until" must not NPE the whole run; the snooze is simply expired. */
	@Test
	void snoozeWithNoUntilIsExpiredNotAnError()
	{
		AccountData data = accountData();
		data.getSnoozes().put("g", new Snooze(null, null));

		PrefsView view = resolver.resolve(data, List.of(status("g", List.of())), now);

		assertTrue(view.getSnoozedExpired().contains("g"));
		assertFalse(view.getHidden().contains("g"));
	}

	private static AccountData accountData()
	{
		return new AccountData(new HashMap<>(), null, new HashMap<>(), new HashSet<>(), new ArrayList<>(), null, new HashSet<>());
	}

	private static GoalStatus status(String id, List<Gap> gaps)
	{
		Goal goal = new Goal(id, GoalCategory.QUEST, id, "https://x", 5, 1);
		return new GoalStatus(goal, gaps, gaps.isEmpty(), false, List.of());
	}
}
