package com.signpost.engine.model;

import java.util.List;
import lombok.Value;

/**
 * One {@link GoalStatus} placed by {@link com.signpost.engine.Ranker}, carrying its computed
 * {@code score} and whether it's user-pinned. {@code later} is true
 * when the goal's stage is more than one stage above the account's estimated stage; such goals have
 * already had their {@code score} multiplied down and are never picked by
 * {@link com.signpost.engine.SuggestSelector#pick3}. Never true for a pinned entry - a pin is an
 * explicit user override. {@code unblocks} names every other ranked goal whose
 * gap tree needs this quest, in rank order - empty for anything but a quest with dependents.
 */
@Value
public class RankedGoal
{
	GoalStatus status;
	double score;
	boolean pinned;
	boolean later;
	List<String> unblocks;

	public RankedGoal(GoalStatus status, double score, boolean pinned, boolean later)
	{
		this(status, score, pinned, later, List.of());
	}

	public RankedGoal(GoalStatus status, double score, boolean pinned, boolean later, List<String> unblocks)
	{
		this.status = status;
		this.score = score;
		this.pinned = pinned;
		this.later = later;
		this.unblocks = List.copyOf(unblocks);
	}

	/**
	 * The bucket {@link com.signpost.engine.Ranker} placed this goal into, derived from
	 * the fields above rather than stored separately - {@code pinned}/{@code later} directly, else
	 * "ready" iff the underlying {@link GoalStatus} is ready with a known bank (mirrors
	 * {@link com.signpost.engine.Ranker}'s own {@code isReadyNow}), else "rest".
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

	/** The four buckets {@link com.signpost.engine.Ranker} sorts a {@link RankedGoal} into. */
	public enum Tier
	{
		PINNED, READY, REST, LATER
	}
}
