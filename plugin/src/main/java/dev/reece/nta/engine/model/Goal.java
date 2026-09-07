package dev.reece.nta.engine.model;

import lombok.Value;

/**
 * One thing the player could work towards next: a quest, a diary tier, or (later slices) a
 * milestone, slayer target, or boss. {@code id} is stable and unique, e.g. {@code "quest:123"} or
 * {@code "diary:VARROCK_MEDIUM"}. {@code stage} (1 early game .. 4 endgame, spec ruling 27) is the
 * milestone's own curated stage, or a derived stage for quests (by effective priority) and diaries
 * (by tier) - see {@link dev.reece.nta.engine.GapEngine} for the mapping.
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
}
