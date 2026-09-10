package com.signpost.kb;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Immutable actual-item aliases and curated offensive progression, built once with the KB. */
public final class GearCatalog
{
	private final Map<Integer, Integer> canonical = new HashMap<>();
	private final Map<Integer, Set<Integer>> variants = new HashMap<>();
	private final Map<Integer, String> names = new HashMap<>();
	private final Map<Integer, String> benefits = new HashMap<>();
	private final Map<Integer, String> slots = new HashMap<>();
	private final Map<Integer, String> styles = new HashMap<>();
	private final Map<Integer, Set<Integer>> coverage = new HashMap<>();

	GearCatalog(List<GearLadder> ladders, GearLadder.Data data, List<MilestoneEntry> milestones)
	{
		Map<Integer, Set<Integer>> edges = new LinkedHashMap<>();
		for (GearLadder ladder : ladders)
		{
			Integer previous = null;
			for (GearLadder.Rung rung : ladder.getRungs())
			{
				register(rung.getId(), rung.getName(), rung.getVariants(), ladder.getStyle(), ladder.getSlot(),
					"Improves the " + ladder.getStyle() + " " + ladder.getSlot() + " progression slot.");
				// Weapon tracks are acquisition suggestions, not a universal ordering of combat roles.
				if (previous != null && !"weapon".equals(ladder.getSlot()))
				{
					edges.computeIfAbsent(rung.getId(), ignored -> new HashSet<>()).add(previous);
				}
				previous = rung.getId();
			}
		}
		if (data != null)
		{
			require(data.equipment != null, "equipment is required");
			Set<Integer> definitions = new HashSet<>();
			for (GearLadder.LadderDto ladder : data.ladders)
			{
				for (List<GearLadder.RungDto> rows : ladder.slots.values())
				{
					for (GearLadder.RungDto row : rows)
					{
						if (row.replaces != null)
						{
							require(new HashSet<>(row.replaces).size() == row.replaces.size(), row.name + " duplicate replacements");
							edges.computeIfAbsent(row.id, ignored -> new HashSet<>()).addAll(row.replaces);
						}
					}
				}
			}
			for (GearLadder.EquipmentDto row : data.equipment)
			{
				require(row != null && row.id != null && definitions.add(row.id), "null or duplicate equipment definition");
				register(row.id, row.name, row.variants, row.style, row.slot, row.benefit);
				require(row.replaces != null && new HashSet<>(row.replaces).size() == row.replaces.size(), row.name + " replaces");
				edges.computeIfAbsent(row.id, ignored -> new HashSet<>()).addAll(row.replaces);
			}
		}
		for (Map.Entry<Integer, Set<Integer>> entry : edges.entrySet())
		{
			for (Integer target : entry.getValue())
			{
				require(target != null && canonical.containsKey(target), names.get(entry.getKey()) + " unknown replacement " + target);
				require(canonicalId(target) != canonicalId(entry.getKey()), "self replacement " + target);
				require(slots.get(canonicalId(entry.getKey())).equals(slots.get(canonicalId(target))), "cross-slot replacement " + target);
			}
		}
		Map<Integer, Set<Integer>> normalized = new HashMap<>();
		edges.forEach((id, targets) -> targets.forEach(target -> normalized.computeIfAbsent(canonicalId(id), ignored -> new HashSet<>()).add(canonicalId(target))));
		for (MilestoneEntry milestone : milestones)
		{
			for (BossReward reward : milestone.getBossRewards())
			{
				for (int replacement : reward.getSupersededBy())
				{
					int superior = canonicalId(replacement);
					for (int variant : reward.getIds())
					{
						int target = canonicalId(variant);
						if (superior != target) normalized.computeIfAbsent(superior, ignored -> new HashSet<>()).add(target);
					}
				}
			}
		}
		for (Integer id : normalized.keySet()) close(id, normalized, new HashSet<>());
	}

	private void register(int id, String name, List<Integer> ids, String style, String slot, String benefit)
	{
		require(id > 0 && name != null && !name.isBlank(), "equipment id/name");
		require(style != null && Set.of("melee", "ranged", "magic", "hybrid", "utility").contains(style), name + " style");
		require(slot != null && Set.of("head", "body", "legs", "weapon", "offhand", "cape", "neck", "hands", "feet", "ring", "tool").contains(slot), name + " slot");
		require(benefit != null && !benefit.isBlank(), name + " benefit");
		require(ids != null && ids.contains(id) && new HashSet<>(ids).size() == ids.size(), name + " variants");
		for (Integer variant : ids)
		{
			require(variant != null && variant > 0, name + " invalid variant");
			Integer existing = canonical.putIfAbsent(variant, id);
			require(existing == null || existing == id, name + " conflicting alias " + variant);
		}
		if (names.containsKey(id))
		{
			require(names.get(id).equals(name) && slots.get(id).equals(slot) && variants.get(id).equals(new HashSet<>(ids)), name + " conflicting metadata");
			if (!styles.get(id).equals(style)) styles.put(id, "hybrid");
			return;
		}
		names.put(id, name);
		variants.put(id, Set.copyOf(ids));
		styles.put(id, style);
		slots.put(id, slot);
		benefits.put(id, benefit);
	}

	private Set<Integer> close(int id, Map<Integer, Set<Integer>> edges, Set<Integer> visiting)
	{
		if (coverage.containsKey(id)) return coverage.get(id);
		require(visiting.add(id), "replacement cycle at " + names.get(id));
		Set<Integer> covered = new HashSet<>();
		covered.add(id);
		for (Integer target : edges.getOrDefault(id, Set.of())) covered.addAll(close(target, edges, visiting));
		visiting.remove(id);
		Set<Integer> result = Set.copyOf(covered);
		coverage.put(id, result);
		return result;
	}

	public int canonicalId(int id) { return canonical.getOrDefault(id, id); }
	public boolean covers(int ownedId, int targetId)
	{
		int owned = canonicalId(ownedId), target = canonicalId(targetId);
		return owned == target || coverage.getOrDefault(owned, Set.of()).contains(target);
	}
	public String name(int id) { return names.get(canonicalId(id)); }
	public String benefit(int id) { return benefits.get(canonicalId(id)); }
	public String style(int id) { return styles.get(canonicalId(id)); }

	private static void require(boolean valid, String field)
	{
		if (!valid) throw new IllegalStateException("Malformed gear-ladders.json: " + field);
	}
}
