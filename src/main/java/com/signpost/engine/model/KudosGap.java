package com.signpost.engine.model;

import lombok.EqualsAndHashCode;
import lombok.Value;

/** The player has less museum kudos than the goal requires. */
@Value
@EqualsAndHashCode(callSuper = false)
public class KudosGap extends Gap
{
	int have;
	int need;
}
