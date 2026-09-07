package dev.reece.nta.engine.model;

import dev.reece.nta.kb.OwnedItem;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * A milestone's recommended gear ({@code gearOwnedAny}, spec ruling 27) isn't held: none of
 * {@code acceptable}'s item ids is in bank/inventory/equipment in any quantity. {@code bankUnknown}
 * mirrors {@link ItemGap}'s {@code have == null} semantics: true when the bank hasn't been seen
 * this session, so this isn't a confirmed shortfall.
 */
@Value
@EqualsAndHashCode(callSuper = false)
public class GearGap extends Gap
{
	List<OwnedItem> acceptable;
	boolean bankUnknown;
}
