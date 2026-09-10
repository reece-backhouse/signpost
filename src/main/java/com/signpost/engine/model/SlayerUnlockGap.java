package com.signpost.engine.model;

import com.signpost.snapshot.SlayerReward;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = false)
public class SlayerUnlockGap extends Gap
{
	SlayerReward reward;
	String milestoneId;
}
