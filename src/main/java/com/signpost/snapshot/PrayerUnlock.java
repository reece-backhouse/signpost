package com.signpost.snapshot;

import lombok.Getter;

@Getter
public enum PrayerUnlock
{
	RIGOUR("Rigour", "milestone:rigour"),
	AUGURY("Augury", "milestone:augury"),
	PRESERVE("Preserve", "milestone:preserve");

	private final String displayName;
	private final String milestoneId;

	PrayerUnlock(String displayName, String milestoneId)
	{
		this.displayName = displayName;
		this.milestoneId = milestoneId;
	}

	public boolean isUnlocked(Snapshot snapshot)
	{
		switch (this)
		{
			case RIGOUR: return snapshot.isRigour();
			case AUGURY: return snapshot.isAugury();
			case PRESERVE: return snapshot.isPreserve();
			default: throw new IllegalStateException(name());
		}
	}

	public static PrayerUnlock forMilestone(String id)
	{
		for (PrayerUnlock prayer : values())
		{
			if (prayer.milestoneId.equals(id)) return prayer;
		}
		return null;
	}
}
