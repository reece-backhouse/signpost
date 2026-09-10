package com.signpost.kb;

/** Builds OSRS wiki page urls from goal, method and material names. Pure. */
public final class WikiUrls
{
	public static final String WIKI_BASE = "https://oldschool.runescape.wiki/w/";

	private WikiUrls()
	{
	}

	/** {@code WIKI_BASE} plus {@code title} with spaces as underscores - the wiki's canonical page url form. */
	public static String forTitle(String title)
	{
		return WIKI_BASE + title.trim().replace(' ', '_');
	}
}
