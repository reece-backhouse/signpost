package dev.reece.nta.engine.model;

import lombok.Value;

/**
 * One {@link GoalStatus} placed by {@link dev.reece.nta.engine.Ranker}, carrying its computed
 * {@code score} (ruling 14) and whether it's user-pinned. {@code later} (spec ruling 27) is true
 * when the goal's stage is more than one stage above the account's estimated stage; such goals have
 * already had their {@code score} multiplied down and are never picked by
 * {@link dev.reece.nta.engine.SuggestSelector#pick3}. Never true for a pinned entry - a pin is an
 * explicit user override.
 */
@Value
public class RankedGoal
{
	GoalStatus status;
	double score;
	boolean pinned;
	boolean later;
}
