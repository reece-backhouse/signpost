package dev.reece.nta.engine.model;

import java.util.List;
import lombok.Value;

/**
 * A {@link Goal} together with everything currently blocking it, as computed by
 * {@link dev.reece.nta.engine.GapEngine}, and (spec ruling 29) everything it already satisfies.
 */
@Value
public class GoalStatus
{
	Goal goal;
	List<Gap> gaps;
	/** True when {@code gaps} is empty, i.e. the goal could be done right now. */
	boolean ready;
	/** True when any {@link ItemGap} (including inside a {@link DiaryTaskGap}) has an unknown {@code have} because the bank hasn't been seen. */
	boolean bankUnknown;
	/** Display-only text, never evaluated. Currently only a quest goal's {@code prereqNotes}; empty for every other goal category. */
	List<String> notes;
	/** Requirements already satisfied (spec ruling 29), in knowledge-base order; empty for a {@link GoalCategory#SKILL_TARGET}. */
	List<Met> met;
	/** The goals needing this one - non-empty only for a {@link GoalCategory#SKILL_TARGET} (spec ruling 28). */
	List<GoalRef> parents;
	/**
	 * A {@link GoalCategory#SKILL_TARGET}'s training route from the current bank to the target
	 * level, computed once by {@link dev.reece.nta.engine.SkillTargetSynthesiser}; {@code null} for
	 * every other goal.
	 */
	Route bankRoute;
	/**
	 * A {@link GoalCategory#SKILL_TARGET}'s best exact-level parent's {@link dev.reece.nta.engine.Ranker#score}:
	 * an uncovered target scores exactly this, so it never outranks the goal it serves (fix round 1).
	 * 0 for every other goal.
	 */
	double parentScore;

	/** As the full constructor with no met requirements, no parents, and no bank route. */
	public GoalStatus(Goal goal, List<Gap> gaps, boolean ready, boolean bankUnknown, List<String> notes)
	{
		this(goal, gaps, ready, bankUnknown, notes, List.of(), List.of(), null, 0);
	}

	public GoalStatus(Goal goal, List<Gap> gaps, boolean ready, boolean bankUnknown, List<String> notes, List<Met> met,
		List<GoalRef> parents, Route bankRoute, double parentScore)
	{
		this.goal = goal;
		this.gaps = gaps;
		this.ready = ready;
		this.bankUnknown = bankUnknown;
		this.notes = notes;
		this.met = met;
		this.parents = parents;
		this.bankRoute = bankRoute;
		this.parentScore = parentScore;
	}

	/** True for a {@link GoalCategory#SKILL_TARGET} whose bank route falls short - never picked, ranked just below its parent. */
	public boolean isUncoveredTarget()
	{
		return bankRoute != null && bankRoute.getUncoveredXp() > 0;
	}

	/** True when this is a skill target whose {@link #bankRoute} reaches the target level from the bank alone (spec ruling 28). */
	public boolean isBankCovered()
	{
		return bankRoute != null && bankRoute.getUncoveredXp() == 0;
	}
}
