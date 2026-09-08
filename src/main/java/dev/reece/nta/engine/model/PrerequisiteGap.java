package dev.reece.nta.engine.model;

import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * RL-007: the milestone named by a goal's {@code prerequisite} isn't owned yet (a portal nexus
 * before its portal chamber). {@code goalId} is that milestone's id, {@code name} its display name.
 */
@Value
@EqualsAndHashCode(callSuper = false)
public class PrerequisiteGap extends Gap
{
	String goalId;
	String name;
}
