package com.signpost.snapshot;

import java.util.List;
import lombok.Value;

/** Collection completion and remaining combat tasks, independent of current item ownership. */
@Value
public class BossProgress
{
	/** Null means the collection state could not be read. */
	Boolean greenLogged;
	boolean combatAchievementsKnown;
	List<CombatAchievementTask> remainingTasks;

	public BossProgress(Boolean greenLogged, boolean combatAchievementsKnown,
		List<CombatAchievementTask> remainingTasks)
	{
		this.greenLogged = greenLogged;
		this.combatAchievementsKnown = combatAchievementsKnown;
		this.remainingTasks = List.copyOf(remainingTasks);
	}
}
