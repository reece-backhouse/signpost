package com.signpost.kb;

import lombok.Value;

/** An identified tool or supply and the quantity needed for one gathering loop. */
@Value
public class BringItem
{
	String name;
	int id;
	int quantity;

	public BringItem(String name, int id)
	{
		this(name, id, 1);
	}

	public BringItem(String name, int id, int quantity)
	{
		this.name = name;
		this.id = id;
		this.quantity = quantity;
	}
}
