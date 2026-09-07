package dev.reece.nta.engine.model;

import lombok.Value;

/**
 * One {@link GoalStatus} placed by {@link dev.reece.nta.engine.Ranker}, carrying its computed
 * {@code score} (ruling 14) and whether it's user-pinned.
 */
@Value
public class RankedGoal
{
	GoalStatus status;
	double score;
	boolean pinned;
}
