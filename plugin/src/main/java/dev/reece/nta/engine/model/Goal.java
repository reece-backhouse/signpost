package dev.reece.nta.engine.model;

import lombok.Value;

/**
 * One thing the player could work towards next: a quest, a diary tier, or (later slices) a
 * milestone, slayer target, or boss. {@code id} is stable and unique, e.g. {@code "quest:123"} or
 * {@code "diary:VARROCK_MEDIUM"}.
 */
@Value
public class Goal
{
	String id;
	GoalCategory category;
	String name;
	String wikiUrl;
	int priority;
}
