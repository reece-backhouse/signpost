package dev.reece.nta.engine.model;

import dev.reece.nta.kb.MethodEntry;
import java.util.List;
import lombok.Value;

/**
 * What's missing to close a {@link Route}'s {@code uncoveredXp} (ticket C7): the best method at the
 * level the route stopped at, and the material shortfall for it. {@code method} is {@code null} and
 * {@code items} empty when the route already covered the full xp delta.
 */
@Value
public class Shortfall
{
	MethodEntry method;
	List<ShortfallItem> items;
}
