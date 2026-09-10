package com.signpost.kb;

import java.util.List;
import lombok.Value;

/** A concrete boss progression reward; any held superior item removes the need for this reward. */
@Value
public class BossReward
{
	String name;
	List<Integer> ids;
	List<Integer> supersededBy;
	/** Consumed prayer scrolls remain obtained when this prayer is unlocked; otherwise null. */
	String prayer;
	RewardValue value;
	String benefit;
	List<List<Integer>> situationalWhenAllOwned;

	public BossReward(String name, List<Integer> ids, List<Integer> supersededBy, String prayer,
		RewardValue value, String benefit, List<List<Integer>> situationalWhenAllOwned)
	{
		this.name = name;
		this.ids = List.copyOf(ids);
		this.supersededBy = List.copyOf(supersededBy);
		this.prayer = prayer;
		this.value = java.util.Objects.requireNonNull(value, "reward value");
		this.benefit = java.util.Objects.requireNonNull(benefit, "reward benefit");
		this.situationalWhenAllOwned = situationalWhenAllOwned.stream().map(List::copyOf)
			.collect(java.util.stream.Collectors.toUnmodifiableList());
	}
}
