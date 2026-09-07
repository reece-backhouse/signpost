package dev.reece.nta.engine.model;

import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * The player doesn't have enough of {@code name}. {@code have} is {@code null} when the bank
 * hasn't been seen this session ({@code Snapshot.bankKnown == false}), not when the count is
 * zero. {@code sources} is always empty in S3 (filled in S5); {@code mustObtain} is true only when
 * the account is an iron type and every known source was filtered out by
 * {@link dev.reece.nta.engine.GapEngine#sourcesFor}.
 */
@Value
@EqualsAndHashCode(callSuper = false)
public class ItemGap extends Gap
{
	String name;
	Integer have;
	int need;
	List<ItemSource> sources;
	boolean mustObtain;
}
