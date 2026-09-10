package com.signpost.kb;

import java.util.List;
import lombok.Value;

/**
 * An item requirement: a name plus how many are needed. {@code id}, {@code sources}, and
 * {@code ids} are only populated for milestone item requirements (which carry a resolved item id
 * and free-text source descriptions); quest items keep {@code id == null}, {@code sources} empty,
 * and {@code ids} empty, matched by name as before (see {@link com.signpost.engine.GapEngine}).
 * {@code ids} is every wiki-page-variant id that counts toward the requirement (e.g. Dragon
 * defender's several item ids), always including {@code id}; it defaults to {@code [id]} when not
 * given explicitly.
 */
@Value
public class ItemReq
{
	String name;
	Integer id;
	int quantity;
	List<String> sources;
	List<Integer> ids;

	/** Quest item requirement: name-matched only, no id, sources, or ids. */
	public ItemReq(String name, int quantity)
	{
		this(name, null, quantity, List.of(), List.of());
	}

	/** {@code ids} defaults to {@code [id]} (or empty when {@code id} is null) - the common case for a single-id requirement. */
	public ItemReq(String name, Integer id, int quantity, List<String> sources)
	{
		this(name, id, quantity, sources, id == null ? List.of() : List.of(id));
	}

	public ItemReq(String name, Integer id, int quantity, List<String> sources, List<Integer> ids)
	{
		this.name = name;
		this.id = id;
		this.quantity = quantity;
		this.sources = List.copyOf(sources);
		this.ids = List.copyOf(ids);
	}
}
