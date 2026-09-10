package com.signpost.engine.model;

import java.util.List;
import java.util.Set;
import lombok.Value;

/**
 * The resolved view of a player's persisted preferences (snoozes / ignores / pins / focus) against
 * the current {@link GoalStatus}es, as computed by {@link com.signpost.engine.PrefsResolver}.
 * {@code hidden} (passed to {@link com.signpost.engine.Ranker#rank}) is ignores unioned with
 * {@code snoozedActive}; {@code snoozedExpired} exists for display/bookkeeping only.
 * {@code ownedManually} is passed through from {@link com.signpost.store.AccountData}
 * unchanged, for the panel's "Owned (manual)" section - the goals themselves are never in
 * {@code hidden} since {@link com.signpost.engine.GapEngine} doesn't emit them at all.
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
