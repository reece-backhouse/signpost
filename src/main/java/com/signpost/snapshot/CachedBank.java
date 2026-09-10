package com.signpost.snapshot;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Value;

/**
 * The plugin's last-seen view of the player's bank, held in memory between snapshots and
 * refreshed from {@code ItemContainerChanged} or a loaded {@code AccountData}.
 */
@Value
public class CachedBank
{
	Map<Integer, Integer> items;
	Instant asOf;
	boolean known;

	public CachedBank(Map<Integer, Integer> items, Instant asOf, boolean known)
	{
		this.items = items == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(items));
		this.asOf = asOf;
		this.known = known;
	}

	public static CachedBank unknown()
	{
		return new CachedBank(Map.of(), null, false);
	}
}
