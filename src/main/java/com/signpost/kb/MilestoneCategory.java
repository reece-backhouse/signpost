package com.signpost.kb;

/**
 * The kind of curated milestone, as the {@code category} field of a {@code milestones.json}
 * entry. See {@link com.signpost.engine.GapEngine} for how each category is evaluated for
 * completion and mapped onto {@link com.signpost.engine.model.GoalCategory}.
 */
public enum MilestoneCategory
{
	GEAR,
	UNLOCK,
	PRAYER,
	SPELLBOOK,
	SLAYER_TARGET,
	BOSS,
	/** A built player-owned-house room or piece of furniture - never auto-detected, finished only by "Own it". */
	POH
}
