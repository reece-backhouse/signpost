package com.signpost.engine.model;

import lombok.EqualsAndHashCode;
import lombok.Value;

/** The player has fewer quest points than the goal requires. */
@Value
@EqualsAndHashCode(callSuper = false)
public class QuestPointsGap extends Gap
{
	int have;
	int need;
}
