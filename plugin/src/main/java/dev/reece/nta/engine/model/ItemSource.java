package dev.reece.nta.engine.model;

import lombok.Value;

/**
 * Where a missing item could be obtained. Placeholder for S5, which fills {@link ItemGap#getSources()}
 * in from {@code materials.json}; S3 always produces an empty source list, but the type is public
 * now so {@link ItemGap} and {@link dev.reece.nta.engine.GapEngine#sourcesFor} are testable ahead
 * of that.
 */
@Value
public class ItemSource
{
	String type;
	String where;
	String detail;
}
