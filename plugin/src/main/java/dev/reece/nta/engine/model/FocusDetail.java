package dev.reece.nta.engine.model;

import lombok.Value;

/**
 * The full drill-down for the player's active focus goal (ticket E, spec ruling 26): the goal's
 * current {@link GoalStatus}, plus the same {@link dev.reece.nta.engine.NextStepPicker}-computed
 * "do this next" step, {@link Route}, and {@link Shortfall} objects {@link
 * dev.reece.nta.engine.Engine#run} already computed - never recomputed by the panel. {@code route}
 * is {@code null} unless {@code next} is a skill step; {@code shortfall} is {@code null} unless
 * {@code route.getUncoveredXp() > 0}. {@code fromLevel}/{@code toLevel} are the skill step's
 * current/target level (both 0 for a non-skill step).
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
}
