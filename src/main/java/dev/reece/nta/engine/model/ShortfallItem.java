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
 * an always-empty {@code craftFrom} of their own. {@code wikiUrl} is the material's wiki page (ticket
 * F3), {@code null} when the knowledge base has no entry for it. {@code plans} (spec ruling 28) is
 * this item's own curated {@link dev.reece.nta.kb.GatheringPlan}s plus, for each {@code craftFrom}
 * ingredient, that ingredient's own plans - so a shortfall reached only through the craft chain
 * (e.g. Dragon scale dust ground from Blue dragon scales) still surfaces the upstream plan.
 */
@Value
public class ShortfallItem
{
	ItemQuantity item;
	int have;
	int need;
	List<ItemSource> sources;
	List<ShortfallItem> craftFrom;
	String wikiUrl;
	List<PlanOffer> plans;

	public ShortfallItem(ItemQuantity item, int have, int need, List<ItemSource> sources, List<ShortfallItem> craftFrom)
	{
		this(item, have, need, sources, craftFrom, null, List.of());
	}

	public ShortfallItem(ItemQuantity item, int have, int need, List<ItemSource> sources, List<ShortfallItem> craftFrom, String wikiUrl)
	{
		this(item, have, need, sources, craftFrom, wikiUrl, List.of());
	}

	public ShortfallItem(ItemQuantity item, int have, int need, List<ItemSource> sources, List<ShortfallItem> craftFrom, String wikiUrl,
		List<PlanOffer> plans)
	{
		this.item = item;
		this.have = have;
		this.need = need;
		this.sources = sources;
		this.craftFrom = craftFrom;
		this.wikiUrl = wikiUrl;
		this.plans = plans;
	}
}
