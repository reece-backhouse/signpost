package dev.reece.nta.engine.model;

import lombok.EqualsAndHashCode;
import lombok.Value;

/** The player's combat level is below what the goal requires. */
@Value
@EqualsAndHashCode(callSuper = false)
public class CombatLevelGap extends Gap
{
	int have;
	int need;
}
