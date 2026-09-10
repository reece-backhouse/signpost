package com.signpost.engine.model;

import com.signpost.kb.RewardValue;
import com.signpost.snapshot.CombatAchievementTask;
import java.util.List;
import lombok.Value;

/** Personal progression value, distinct from entry readiness and collection completion. */
@Value
public class GoalObjective
{
	List<RewardTarget> rewards;
	List<CombatAchievementTask> tasks;
	boolean greenLogged;
	boolean achievementsKnown;
	boolean bankUnknown;
	RewardValue value;
	boolean achievementPriority;

	public GoalObjective(List<RewardTarget> rewards, List<CombatAchievementTask> tasks, boolean greenLogged,
		boolean achievementsKnown, boolean bankUnknown)
	{
		this.rewards = List.copyOf(rewards);
		this.tasks = List.copyOf(tasks);
		this.greenLogged = greenLogged;
		this.achievementsKnown = achievementsKnown;
		this.bankUnknown = bankUnknown;
		RewardValue best = RewardValue.COLLECTION;
		for (RewardTarget reward : rewards)
		{
			if (!reward.isOwnershipUnknown() && reward.getValue().ordinal() < best.ordinal()) best = reward.getValue();
		}
		this.achievementPriority = !tasks.isEmpty() && best.ordinal() > RewardValue.USEFUL.ordinal();
		this.value = achievementPriority ? RewardValue.USEFUL : best;
	}

	public boolean hasReason()
	{
		return !rewards.isEmpty() || !tasks.isEmpty();
	}

	public boolean hasConfirmedReason()
	{
		return !tasks.isEmpty() || value != RewardValue.COLLECTION;
	}
}
