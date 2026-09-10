package com.signpost.engine.model;

import lombok.EqualsAndHashCode;
import lombok.Value;
import net.runelite.api.Skill;

/**
 * The player's level in {@code skill} is below what's required. {@code xpDelta} is the XP still
 * needed to reach {@code need} from the player's current XP (via {@code Experience.getXpForLevel}).
 * {@code boostableFrom} is the lowest real level from which a boost could reach {@code need}
 * (per {@link com.signpost.engine.BoostTable#maxBoost}), or {@code null} when the requirement
 * isn't boostable or the player's level is below even the boosted floor. {@code recommended} is
 * true when this comes from a milestone's {@code recommended} profile rather than
 * its hard requirements - never boostable, since a recommended level is advisory, not a gate.
 */
@Value
@EqualsAndHashCode(callSuper = false)
public class SkillLevelGap extends Gap
{
	Skill skill;
	int have;
	int need;
	long xpDelta;
	boolean boostable;
	Integer boostableFrom;
	boolean recommended;
}
