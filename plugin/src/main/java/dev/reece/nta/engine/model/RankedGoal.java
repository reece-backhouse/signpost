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

	/**
	 * Task 50: the bucket {@link dev.reece.nta.engine.Ranker} placed this goal into, derived from
	 * the fields above rather than stored separately - {@code pinned}/{@code later} directly, else
	 * "ready" iff the underlying {@link GoalStatus} is ready with a known bank (mirrors
	 * {@link dev.reece.nta.engine.Ranker}'s own {@code isReadyNow}), else "rest".
	 */
	public Tier getTier()
	{
		if (pinned)
		{
			return Tier.PINNED;
		}
		if (later)
		{
			return Tier.LATER;
		}
		return status.isReady() && !status.isBankUnknown() ? Tier.READY : Tier.REST;
	}

	/** The four buckets {@link dev.reece.nta.engine.Ranker} sorts a {@link RankedGoal} into. */
	public enum Tier
	{
		PINNED, READY, REST, LATER
	}
}
