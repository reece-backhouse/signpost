package dev.reece.nta.engine.model;

import dev.reece.nta.kb.OwnedItem;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * A milestone's recommended gear ({@code gearOwnedAny}, spec ruling 27) isn't held: fewer than the
 * profile's effective minimum distinct items (task 46 - {@link dev.reece.nta.kb.RecommendedProfile#effectiveGearOwnedMin()})
 * are owned. {@code owned} is the number of {@code acceptable} items confirmed held (bank &cup;
 * inventory &cup; equipment, any variant id); {@code required} is the effective minimum.
 * {@code bankUnknown} mirrors {@link ItemGap}'s {@code have == null} semantics: true when the bank
 * hasn't been seen this session and the unseen items could still push {@code owned} up to
 * {@code required}, so this isn't a confirmed shortfall.
 */
@Value
@EqualsAndHashCode(callSuper = false)
public class GearGap extends Gap
{
	List<OwnedItem> acceptable;
	int owned;
	int required;
	boolean bankUnknown;
}
