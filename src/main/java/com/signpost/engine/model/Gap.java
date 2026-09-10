package com.signpost.engine.model;

/**
 * One unmet requirement blocking a {@link Goal}. Carries no state of its own; every concrete
 * meaning lives on an immutable subclass ({@link SkillLevelGap}, {@link QuestPrereqGap},
 * {@link ItemGap}, {@link DiaryTaskGap}, {@link QuestPointsGap}, {@link KudosGap},
 * {@link CombatLevelGap}). Not sealed (Java 11 source level).
 */
public abstract class Gap
{
}
