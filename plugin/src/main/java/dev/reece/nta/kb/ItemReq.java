package dev.reece.nta.kb;

import java.util.List;
import lombok.Value;

/**
 * An item requirement: a name plus how many are needed. {@code id} and {@code sources} are only
 * populated for milestone item requirements (which carry a resolved item id and free-text source
 * descriptions); quest items keep {@code id == null} and {@code sources} empty, matched by name
 * as before (see {@link dev.reece.nta.engine.GapEngine}).
 */
@Value
public class ItemReq
{
	String name;
	Integer id;
	int quantity;
	List<String> sources;

	/** Quest item requirement: name-matched only, no id or sources. */
	public ItemReq(String name, int quantity)
	{
		this(name, null, quantity, List.of());
	}

	public ItemReq(String name, Integer id, int quantity, List<String> sources)
	{
		this.name = name;
		this.id = id;
		this.quantity = quantity;
		this.sources = List.copyOf(sources);
	}
}
