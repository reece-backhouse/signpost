package dev.reece.nta.kb;

/**
 * The kind of curated milestone, as the {@code category} field of a {@code milestones.json}
 * entry. See {@link dev.reece.nta.engine.GapEngine} for how each category is evaluated for
 * completion and mapped onto {@link dev.reece.nta.engine.model.GoalCategory}.
 */
public enum MilestoneCategory
{
	GEAR,
	UNLOCK,
	PRAYER,
	SPELLBOOK,
	SLAYER_TARGET,
	BOSS
}
