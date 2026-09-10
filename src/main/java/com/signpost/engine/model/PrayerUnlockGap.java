package com.signpost.engine.model;

import com.signpost.snapshot.PrayerUnlock;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = false)
public class PrayerUnlockGap extends Gap
{
	PrayerUnlock prayer;
	boolean recommended;
}
