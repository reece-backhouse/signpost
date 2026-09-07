package dev.reece.nta.engine;

import dev.reece.nta.engine.model.CombatLevelGap;
import dev.reece.nta.engine.model.Gap;
import dev.reece.nta.engine.model.Goal;
import dev.reece.nta.engine.model.GoalCategory;
import dev.reece.nta.engine.model.GoalStatus;
import dev.reece.nta.engine.model.PrefsView;
import dev.reece.nta.store.AccountData;
import dev.reece.nta.store.Snooze;
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
 * Task 29: {@link PrefsResolver} resolves persisted {@link AccountData} prefs against current
 * {@link GoalStatus}es per ticket D6/D7/D8 and spec ruling 19's fingerprint rule.
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

	/** Final-review ledger 188 / M9: a snooze taken before any Advice existed has no fingerprint; it must still hide the goal until its time is up. */
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

	/** Final-review M4: a hand-edited file with no "until" must not NPE the whole run; the snooze is simply expired. */
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
