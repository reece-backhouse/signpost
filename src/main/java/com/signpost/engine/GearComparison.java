package com.signpost.engine;

import com.signpost.kb.GearCatalog;
import com.signpost.kb.GearLadder;
import com.signpost.kb.KnowledgeBase;
import com.signpost.kb.MilestoneEntry;
import com.signpost.kb.OwnedItem;
import com.signpost.snapshot.Snapshot;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Per-evaluation evidence: equivalent ownership is deliberately separate from replacement coverage. */
final class GearComparison
{
	private static final List<String> COMBAT_STYLES = List.of("melee", "ranged", "magic");
	private final Snapshot snapshot;
	private final GearCatalog catalog;
	private final Set<String> manual;
	private final Set<Integer> ownedIds;
	private final Map<Integer, Integer> held = new LinkedHashMap<>();
	private final Map<Integer, String> manualNames = new LinkedHashMap<>();
	private final Set<Integer> absent = new HashSet<>();

	GearComparison(Snapshot snapshot, KnowledgeBase kb, Set<String> manual)
	{
		this.snapshot = snapshot;
		this.catalog = kb.getGearCatalog();
		this.manual = manual;
		addHeld(snapshot.getEquipment());
		addHeld(snapshot.getInventory());
		addHeld(snapshot.getBank());
		addHeld(snapshot.getRunePouch());
		if (snapshot.isGroupStorageEnabled()) addHeld(snapshot.getGroupStorage());
		this.ownedIds = java.util.Collections.unmodifiableSet(held.keySet());
		for (int id : snapshot.getConfirmedAbsentItems()) absent.add(catalog.canonicalId(id));
		for (GearLadder ladder : kb.getGearLadders())
		{
			for (GearLadder.Rung rung : ladder.getRungs())
			{
				if (manual.contains(GearUpgradeSynthesiser.id(ladder, rung)))
				{
					manualNames.putIfAbsent(catalog.canonicalId(rung.getId()), rung.getName());
				}
			}
		}
		for (MilestoneEntry entry : kb.getMilestones())
		{
			// A manually completed broad drop milestone does not identify which piece was acquired.
			if (!manual.contains(entry.getId()) || entry.getOwnedIfMin() != entry.getOwnedIf().size()) continue;
			for (OwnedItem item : entry.getOwnedIf())
			{
				int canonical = catalog.canonicalId(item.getId());
				if (item.getIds().stream().allMatch(id -> catalog.canonicalId(id) == canonical))
				{
					manualNames.putIfAbsent(canonical, item.getName());
				}
			}
		}
	}

	private void addHeld(Map<Integer, Integer> items)
	{
		items.forEach((id, quantity) -> {
			if (quantity > 0) held.putIfAbsent(catalog.canonicalId(id), id);
		});
	}

	Set<Integer> ownedIds()
	{
		return ownedIds;
	}

	boolean owns(List<Integer> ids)
	{
		for (int id : ids)
		{
			int canonical = catalog.canonicalId(id);
			if (held.containsKey(canonical) || manualNames.containsKey(canonical)) return true;
		}
		return false;
	}

	boolean covers(List<Integer> ids)
	{
		return covers(ids, null);
	}

	boolean covers(List<Integer> ids, String style)
	{
		for (int target : ids)
		{
			if (hybridQuery(target, style))
			{
				boolean complete = true;
				for (String combatStyle : COMBAT_STYLES)
				{
					if (coveringId(target, combatStyle) == null)
					{
						complete = false;
						break;
					}
				}
				if (complete) return true;
			}
			else if (coveringId(target, style) != null) return true;
		}
		return false;
	}

	String coveringName(List<Integer> ids)
	{
		return coveringName(ids, null);
	}

	String coveringName(List<Integer> ids, String style)
	{
		for (int target : ids)
		{
			if (hybridQuery(target, style))
			{
				Integer melee = coveringId(target, "melee");
				Integer ranged = coveringId(target, "ranged");
				Integer magic = coveringId(target, "magic");
				if (melee == null || ranged == null || magic == null) continue;
				String name = heldName(melee);
				if (!ranged.equals(melee)) name += ", " + heldName(ranged);
				if (!magic.equals(melee) && !magic.equals(ranged)) name += ", " + heldName(magic);
				return name;
			}
			Integer owned = coveringId(target, style);
			if (owned != null) return heldName(owned);
		}
		return null;
	}

	private boolean hybridQuery(int target, String style)
	{
		return "hybrid".equals(style) || style == null && "hybrid".equals(catalog.style(target));
	}

	private String heldName(int id)
	{
		Integer actual = held.get(id);
		if (actual != null)
		{
			String name = snapshot.getItemNames().get(actual);
			if (name != null) return name;
			name = catalog.name(actual);
			return name == null ? "Item " + actual : name;
		}
		return manualNames.get(id);
	}

	private Integer coveringId(int target, String style)
	{
		int canonical = catalog.canonicalId(target);
		if (held.containsKey(canonical) || manualNames.containsKey(canonical)) return canonical;
		for (int owned : held.keySet())
		{
			if (coversRole(owned, target, style)) return owned;
		}
		for (int owned : manualNames.keySet())
		{
			if (coversRole(owned, target, style)) return owned;
		}
		return null;
	}

	private boolean coversRole(int owned, int target, String style)
	{
		if (!catalog.covers(owned, target)) return false;
		String requiredStyle = style == null ? catalog.style(target) : style;
		String ownedStyle = catalog.style(owned);
		return requiredStyle == null || ownedStyle == null || "hybrid".equals(ownedStyle)
			|| requiredStyle.equals(ownedStyle);
	}

	boolean ownershipKnown(List<Integer> ids)
	{
		if (owns(ids) || snapshot.isBankKnown()) return true;
		return !ids.isEmpty() && ids.stream().allMatch(id -> absent.contains(catalog.canonicalId(id)));
	}

	boolean milestoneCovered(MilestoneEntry entry)
	{
		if (manual.contains(entry.getId())) return true;
		int covered = 0;
		for (OwnedItem item : entry.getOwnedIf())
		{
			if (covers(item.getIds())) covered++;
		}
		return !entry.getOwnedIf().isEmpty() && covered >= entry.getOwnedIfMin();
	}
}
