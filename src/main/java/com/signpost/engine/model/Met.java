package com.signpost.engine.model;

import lombok.Value;

/**
 * One requirement a {@link Goal} already satisfies, recorded by {@link com.signpost.engine.GapEngine}
 * alongside the {@link Gap}s so a "Why?" explanation can name what is met as well as what is
 * missing. {@code label} is display text with the have/need numbers where they
 * apply ("Herblore 70 (have 74)", "Mourning's End Part II", "Rune crossbow"). {@code count}/
 * {@code required} describe one fulfilled role for {@link Kind#RECOMMENDED_GEAR} and tasks done /
 * total for {@link Kind#DIARY} on a diary tier goal; 0/0 otherwise. Gear labels name the role and
 * the actual owned item that satisfies it, including tracked replacements.
 */
@Value
public class Met
{
	public enum Kind
	{
		SKILL, QUEST, ITEM, DIARY, COMBAT, QUEST_POINTS, KUDOS, RECOMMENDED_SKILL, RECOMMENDED_GEAR,
		PREREQUISITE, SLAYER_POINTS, UNLOCK, RECOMMENDED_PRAYER
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
