package com.signpost.snapshot;

import java.util.Objects;
import lombok.Value;

/** Cache metadata for one combat achievement; its tier (1–6) is also its point value. */
@Value
public class CombatAchievementTask
{
	int id;
	String name;
	String description;
	int tier;

	public CombatAchievementTask(int id, String name, String description, int tier)
	{
		if (id < 0 || tier < 1 || tier > 6)
		{
			throw new IllegalArgumentException("Invalid combat achievement ID or tier");
		}
		this.id = id;
		this.name = Objects.requireNonNull(name);
		this.description = Objects.requireNonNull(description);
		this.tier = tier;
	}
}
