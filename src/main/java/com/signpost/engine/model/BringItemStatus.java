package com.signpost.engine.model;

import com.signpost.kb.BringItem;
import lombok.Value;

/** The same ownership presentation for gathering tools and route ingredients. */
@Value
public class BringItemStatus
{
	BringItem item;
	long quantity;
	long owned;

	public boolean isOwned()
	{
		return owned >= quantity;
	}

	public String text()
	{
		return (isOwned() ? "[x] " : "[ ] ") + item.getName() + " x" + quantity;
	}
}
