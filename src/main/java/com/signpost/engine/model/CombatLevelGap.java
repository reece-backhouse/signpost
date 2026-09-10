package com.signpost.engine.model;

import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * The player's combat level is below what the goal requires. {@code recommended} is true when
 * this comes from a milestone's {@code recommended} profile rather than its hard
 * requirements.
 */
@Value
@EqualsAndHashCode(callSuper = false)
public class CombatLevelGap extends Gap
{
	int have;
	int need;
	boolean recommended;
}
