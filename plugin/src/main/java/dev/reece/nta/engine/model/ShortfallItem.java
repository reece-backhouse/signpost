package dev.reece.nta.engine.model;

import dev.reece.nta.kb.ItemQuantity;
import java.util.List;
import lombok.Value;

/**
 * One material {@link Shortfall#getMethod()} needs, with where to get it (ticket C7). {@code item}
 * is the method's own {@link ItemQuantity} (its per-action recipe amount); {@code need}/{@code have}
 * are totals over the whole shortfall. {@code craftFrom} is populated (one level only - never
 * recursively) when {@code item} is short and one of {@code sources} is a {@code craft} source: the
 * ingredients of the intermediate method that produces it, each with its own have/need/sources but
 * an always-empty {@code craftFrom} of their own.
 */
@Value
public class ShortfallItem
{
	ItemQuantity item;
	int have;
	int need;
	List<ItemSource> sources;
	List<ShortfallItem> craftFrom;
}
