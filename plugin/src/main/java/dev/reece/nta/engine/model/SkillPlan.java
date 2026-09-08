package dev.reece.nta.engine.model;

import lombok.Value;
import net.runelite.api.Skill;

/**
 * One skill gap of the focused goal (task 56, spec ruling 30): a training {@link Route} from the
 * current bank to {@code toLevel}, plus its {@link Shortfall} when the route doesn't fully cover
 * it. Computed once per distinct skill by {@link dev.reece.nta.engine.Engine#run} for every
 * skill gap of the focused goal - top-level, inside a {@link DiaryTaskGap}, and recommended -
 * never recomputed by the panel. {@code shortfall} is {@code null} when {@code covered} is true.
 * {@code source} names where the gap came from: {@code "quest"} (a top-level hard requirement),
 * {@code "diary task N"}, or {@code "recommended"}.
 */
@Value
public class SkillPlan
{
	Skill skill;
	int fromLevel;
	int toLevel;
	long fromXp;
	long toXp;
	boolean recommended;
	Route route;
	Shortfall shortfall;
	boolean covered;
	String source;
}
