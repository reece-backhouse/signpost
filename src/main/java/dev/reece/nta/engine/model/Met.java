package dev.reece.nta.engine.model;

import lombok.Value;

/**
 * One requirement a {@link Goal} already satisfies, recorded by {@link dev.reece.nta.engine.GapEngine}
 * alongside the {@link Gap}s so a "Why?" explanation can name what is met as well as what is
 * missing (spec ruling 29). {@code label} is display text with the have/need numbers where they
 * apply ("Herblore 70 (have 74)", "Mourning's End Part II", "Rune crossbow"). {@code count}/
 * {@code required} are only meaningful for {@link Kind#RECOMMENDED_GEAR} (items owned / minimum
 * required - one entry per owned item, all carrying the same counts) and {@link Kind#DIARY} on a
 * diary tier goal (tasks done / total); 0/0 otherwise.
 */
@Value
public class Met
{
	public enum Kind
	{
		SKILL, QUEST, ITEM, DIARY, COMBAT, QUEST_POINTS, KUDOS, RECOMMENDED_SKILL, RECOMMENDED_GEAR, PREREQUISITE
	}

	Kind kind;
	String label;
	int count;
	int required;

	public Met(Kind kind, String label)
	{
		this(kind, label, 0, 0);
	}

	public Met(Kind kind, String label, int count, int required)
	{
		this.kind = kind;
		this.label = label;
		this.count = count;
		this.required = required;
	}
}
