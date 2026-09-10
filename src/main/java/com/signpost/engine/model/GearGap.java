package com.signpost.engine.model;

import com.signpost.kb.OwnedItem;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Value;

/** One required loadout role is unfilled; acceptable items are alternatives, not a piece count. */
@Value
@EqualsAndHashCode(callSuper = false)
public class GearGap extends Gap
{
	String role;
	List<OwnedItem> acceptable;
	boolean bankUnknown;
}
