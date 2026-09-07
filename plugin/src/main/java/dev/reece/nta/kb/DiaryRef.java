package dev.reece.nta.kb;

import dev.reece.nta.snapshot.DiaryTier;
import lombok.Value;

/** A diary tier a milestone requires, resolved from the JSON's {@code area}+{@code tier} pair. */
@Value
public class DiaryRef
{
	DiaryTier tier;
}
