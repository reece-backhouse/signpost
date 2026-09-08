package dev.reece.nta.engine.model;

import java.util.List;
import java.util.Set;
import lombok.Value;

/**
 * The resolved view of a player's persisted preferences (snoozes / ignores / pins / focus) against
 * the current {@link GoalStatus}es, as computed by {@link dev.reece.nta.engine.PrefsResolver}.
 * {@code hidden} (passed to {@link dev.reece.nta.engine.Ranker#rank}) is ignores unioned with
 * {@code snoozedActive}; {@code snoozedExpired} exists for display/bookkeeping only.
 * {@code ownedManually} (ticket 55) is passed through from {@link dev.reece.nta.store.AccountData}
 * unchanged, for the panel's "Owned (manual)" section - the goals themselves are never in
 * {@code hidden} since {@link dev.reece.nta.engine.GapEngine} doesn't emit them at all.
 */
@Value
public class PrefsView
{
	Set<String> hidden;
	List<String> pins;
	Set<String> snoozedActive;
	Set<String> snoozedExpired;
	String focusGoalId;
	Set<String> ownedManually;
}
