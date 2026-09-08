package dev.reece.nta.kb;

import lombok.Value;

/**
 * A quantity of a named item, used for {@link MethodEntry} materials and outputs. {@code id} is
 * resolved at {@link KnowledgeBase} load time by exact name match against {@code materials.json};
 * {@code null} means the name didn't resolve (see {@link MethodEntry#isUsable()}). {@code quantity}
 * is a {@code double}, not an {@code int}: some bundled methods (e.g. Mahogany Homes, leather
 * armour) record an average per-action consumption rate below 1 or with a fractional part.
 */
@Value
public class ItemQuantity
{
	String name;
	Integer id;
	double quantity;
}
