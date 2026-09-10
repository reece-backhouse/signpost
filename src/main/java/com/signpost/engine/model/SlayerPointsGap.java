package com.signpost.engine.model;

import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = false)
public class SlayerPointsGap extends Gap
{
	int have;
	int need;
}
