package com.signpost.engine.model;

import java.util.List;
import lombok.Value;

/**
 * The full drill-down for the player's active focus goal: the goal's
 * current {@link GoalStatus}, plus the same {@link com.signpost.engine.NextStepPicker}-computed
 * "do this next" step, {@link Route}, and {@link Shortfall} objects {@link
 * com.signpost.engine.Engine#run} already computed - never recomputed by the panel. {@code route}
 * is {@code null} unless {@code next} is a skill step; {@code shortfall} is {@code null} unless
 * {@code route.getUncoveredXp() > 0}. {@code fromLevel}/{@code toLevel} are the skill step's
 * current/target level (both 0 for a non-skill step).
 *
 * <p>{@code skillPlans} is one {@link SkillPlan} per distinct skill gap of the focused
 * goal - top-level, inside a {@link DiaryTaskGap}, and recommended; a skill appearing more than
 * once keeps its highest target level - sorted by xp delta ascending. {@code nextSkillPlan} is
 * the entry matching {@code next} when {@code next} is a skill step, else {@code null}; when it
 * exists, its {@code route} is the same object as {@code route} above (never recomputed).
 */
@Value
public class FocusDetail
{
	GoalStatus status;
	NextStep next;
	Route route;
	Shortfall shortfall;
	int fromLevel;
	int toLevel;
	List<SkillPlan> skillPlans;
	SkillPlan nextSkillPlan;
}
