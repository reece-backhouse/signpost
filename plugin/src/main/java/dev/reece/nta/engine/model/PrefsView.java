package dev.reece.nta.engine.model;

import java.util.List;
import java.util.Set;
import lombok.Value;

/**
 * The resolved view of a player's persisted preferences (snoozes / ignores / pins / focus) against
 * the current {@link GoalStatus}es, as computed by {@link dev.reece.nta.engine.PrefsResolver}.
 * {@code hidden} (passed to {@link dev.reece.nta.engine.Ranker#rank}) is ignores unioned with
 * {@code snoozedActive}; {@code snoozedExpired} exists for display/bookkeeping only.
 */
@Value
public class PrefsView
{
	Set<String> hidden;
	List<String> pins;
	Set<String> snoozedActive;
	Set<String> snoozedExpired;
	String focusGoalId;
}
