package com.signpost.engine.model;

/**
 * The kind of thing a {@link Goal} represents, as produced by {@link com.signpost.engine.GapEngine}.
 * {@link #MILESTONE} covers gear/unlock/prayer/spellbook milestones; {@link #SLAYER_TARGET} and
 * {@link #BOSS} are their own categories. {@link #SKILL_TARGET} is a synthesised "&lt;level&gt;
 * &lt;Skill&gt;" goal ({@link com.signpost.engine.SkillTargetSynthesiser}).
 */
public enum GoalCategory
{
	QUEST,
	DIARY,
	MILESTONE,
	GEAR_UPGRADE,
	SLAYER_TARGET,
	BOSS,
	SKILL_TARGET
}
