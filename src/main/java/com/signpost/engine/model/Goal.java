package com.signpost.engine.model;

import com.signpost.kb.GearLadder;
import lombok.Value;

/**
 * One thing the player could work towards next: a quest, a diary tier, or a
 * milestone, slayer target, or boss. {@code id} is stable and unique, e.g. {@code "quest:123"} or
 * {@code "diary:VARROCK_MEDIUM"}. {@code stage} (1 early game .. 4 endgame) is the
 * milestone's own curated stage, or a derived stage for quests (by effective priority) and diaries
 * (by tier) - see {@link com.signpost.engine.GapEngine} for the mapping.
 */
@Value
public class Goal
{
	String id;
	GoalCategory category;
	String name;
	String wikiUrl;
	int priority;
	int stage;
	Upgrade upgrade;

	public Goal(String id, GoalCategory category, String name, String wikiUrl, int priority, int stage)
	{
		this(id, category, name, wikiUrl, priority, stage, null);
	}

	public Goal(String id, GoalCategory category, String name, String wikiUrl, int priority, int stage, Upgrade upgrade)
	{
		this.id = id;
		this.category = category;
		this.name = name;
		this.wikiUrl = wikiUrl;
		this.priority = priority;
		this.stage = stage;
		this.upgrade = upgrade;
	}

	@Value
	public static class Upgrade
	{
		String style;
		String slot;
		GearLadder.Rung target;
		String replaces;
	}
}
