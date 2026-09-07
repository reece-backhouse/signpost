package dev.reece.nta.engine.model;

/**
 * The kind of thing a {@link Goal} represents. Only {@link #QUEST} and {@link #DIARY} are
 * produced by the S3 {@link dev.reece.nta.engine.GapEngine}; the rest are reserved for later
 * slices (S4/S5).
 */
public enum GoalCategory
{
	QUEST,
	DIARY,
	MILESTONE,
	SLAYER_TARGET,
	BOSS
}
