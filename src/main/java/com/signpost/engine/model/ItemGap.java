package com.signpost.engine.model;

import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * The player doesn't have enough of {@code name}. {@code have} is {@code null} when the bank
 * hasn't been seen this session ({@code Snapshot.bankKnown == false}), not when the count is
 * zero. {@code mustObtain} is true only when
 * the account is an iron type and every known source was filtered out by
 * {@link com.signpost.engine.GapEngine#sourcesFor}. {@code wikiUrl} is the item's wiki page
 * when the knowledge base knows the material, else {@code null}. {@code itemId} is
 * the resolved OSRS item id - a milestone requirement's own id, or a quest/diary item's matched
 * {@link com.signpost.kb.MaterialEntry#getId()} - else {@code null} when nothing resolves it, so
 * the UI can show an icon and link the wiki page precisely.
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
	String wikiUrl;
	Integer itemId;

	public ItemGap(String name, Integer have, int need, List<ItemSource> sources, boolean mustObtain)
	{
		this(name, have, need, sources, mustObtain, null, null);
	}

	public ItemGap(String name, Integer have, int need, List<ItemSource> sources, boolean mustObtain, String wikiUrl)
	{
		this(name, have, need, sources, mustObtain, wikiUrl, null);
	}

	public ItemGap(String name, Integer have, int need, List<ItemSource> sources, boolean mustObtain, String wikiUrl, Integer itemId)
	{
		this.name = name;
		this.have = have;
		this.need = need;
		this.sources = sources;
		this.mustObtain = mustObtain;
		this.wikiUrl = wikiUrl;
		this.itemId = itemId;
	}
}
