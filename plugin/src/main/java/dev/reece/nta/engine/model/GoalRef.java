package dev.reece.nta.engine.model;

import lombok.Value;

/**
 * A goal that needs a {@link GoalCategory#SKILL_TARGET} goal (spec ruling 28): the parent's id and
 * name, and the level of the target's skill the parent itself requires (which may be above the
 * target's level - the target is the lowest level any parent needs).
 */
@Value
public class GoalRef
{
	String id;
	String name;
	int level;
}
