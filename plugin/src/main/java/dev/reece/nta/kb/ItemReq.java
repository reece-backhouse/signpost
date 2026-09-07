package dev.reece.nta.kb;

import lombok.Value;

/**
 * An item requirement for a quest (name plus how many are needed).
 */
@Value
public class ItemReq
{
	String name;
	int quantity;
}
