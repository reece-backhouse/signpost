package dev.reece.nta.kb;

import lombok.Value;

/** One item id whose presence (in any quantity) marks a gear milestone as already owned. */
@Value
public class OwnedItem
{
	String name;
	int id;
}
