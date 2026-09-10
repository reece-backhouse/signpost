package com.signpost.kb;

import java.util.List;
import lombok.Value;

/**
 * An item whose presence (in any quantity, held under any of {@code ids}) marks a gear milestone
 * as already owned. {@code id} is the primary/original id (kept for fingerprinting and display);
 * {@code ids} is every wiki-page-variant id that also counts as owning it (e.g. a trimmed "(t)"
 * item), always including {@code id}.
 */
@Value
public class OwnedItem
{
	String name;
	int id;
	List<Integer> ids;

	/** {@code ids} defaults to {@code [id]} - the common case for a name with no known variant ids. */
	public OwnedItem(String name, int id)
	{
		this(name, id, List.of(id));
	}

	public OwnedItem(String name, int id, List<Integer> ids)
	{
		this.name = name;
		this.id = id;
		this.ids = List.copyOf(ids);
	}
}
