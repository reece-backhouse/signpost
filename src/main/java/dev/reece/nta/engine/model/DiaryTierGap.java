package dev.reece.nta.engine.model;

import dev.reece.nta.snapshot.DiaryTier;
import lombok.EqualsAndHashCode;
import lombok.Value;

/** An achievement diary tier a milestone requires isn't complete yet. */
@Value
@EqualsAndHashCode(callSuper = false)
public class DiaryTierGap extends Gap
{
	DiaryTier tier;
}
