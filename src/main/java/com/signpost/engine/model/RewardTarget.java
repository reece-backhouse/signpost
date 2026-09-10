package com.signpost.engine.model;

import com.signpost.kb.RewardValue;
import lombok.Value;

/** A concrete gain, its value to this account, and whether absence is actually known. */
@Value
public class RewardTarget
{
	String name;
	String benefit;
	RewardValue value;
	boolean ownershipUnknown;
}
