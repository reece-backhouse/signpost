package com.signpost.snapshot;

import java.util.Set;
import lombok.Value;

/** Client-resolved task identity and persistent reward state, independent of the client. */
@Value
public class SlayerState
{
	String taskName;
	int remaining;
	int masterId;
	String master;
	int streak;
	int points;
	Set<SlayerReward> unlocks;

	public SlayerState(String taskName, int remaining, int masterId, String master, int streak, int points, Set<SlayerReward> unlocks)
	{
		this.taskName = taskName;
		this.remaining = remaining;
		this.masterId = masterId;
		this.master = master;
		this.streak = streak;
		this.points = points;
		this.unlocks = Set.copyOf(unlocks);
	}

	public boolean isActive()
	{
		return remaining > 0 && taskName != null && !taskName.isEmpty();
	}

	public static SlayerState empty()
	{
		return new SlayerState(null, 0, 0, "Unknown master", 0, 0, Set.of());
	}
}
