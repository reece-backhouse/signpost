package dev.reece.nta.engine.model;

/**
 * The kind of thing a {@link Goal} represents, as produced by {@link dev.reece.nta.engine.GapEngine}.
 * {@link #MILESTONE} covers gear/unlock/prayer/spellbook milestones; {@link #SLAYER_TARGET} and
 * {@link #BOSS} are their own categories. {@link #SKILL_TARGET} is a synthesised "&lt;level&gt;
 * &lt;Skill&gt;" goal (spec ruling 28, {@link dev.reece.nta.engine.SkillTargetSynthesiser}).
 */
public enum GoalCategory
{
	QUEST,
	DIARY,
	MILESTONE,
	SLAYER_TARGET,
	BOSS,
	SKILL_TARGET
}
