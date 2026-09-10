package com.signpost.kb;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Value;

/** Budget and midgame data merge into one ordered track per style and equipment slot. */
@Value
public class GearLadder
{
	String style;
	String slot;
	List<Rung> rungs;

	@Value
	public static class Rung
	{
		String name;
		int id;
		List<Integer> variants;
		String provider;
		boolean geOnly;
	}

	static List<GearLadder> parse(Data data, List<QuestEntry> quests, List<MilestoneEntry> milestones)
	{
		if (data == null) return List.of(); // In-memory KB fixtures need not contain ladders.
		require(data.version == 1 && data.ladders != null, "version/ladders");
		Set<String> providers = new HashSet<>();
		quests.forEach(q -> providers.add("quest:" + q.getId()));
		milestones.forEach(m -> providers.add(m.getId()));
		Set<String> combinations = new HashSet<>();
		Map<String, List<Rung>> tracks = new LinkedHashMap<>();
		List<LadderDto> ordered = new ArrayList<>(data.ladders);
		for (LadderDto ladder : ordered)
		{
			require(ladder != null, "ladders entry");
			String context = ladder.style + "/" + ladder.tier;
			require(Set.of("melee", "ranged", "magic").contains(ladder.style == null ? "" : ladder.style), context + " style");
			require("budget".equals(ladder.tier) || "midgame".equals(ladder.tier), context + " tier");
			require(combinations.add(context), context + " duplicate combination");
			require(ladder.slots != null && !ladder.slots.isEmpty(), context + " slots");
		}
		require(combinations.size() == 6, "expected all six style/tier combinations");
		ordered.sort(Comparator.comparingInt(l -> "budget".equals(l.tier) ? 0 : 1));
		for (LadderDto ladder : ordered)
		{
			for (Map.Entry<String, List<RungDto>> slot : ladder.slots.entrySet())
			{
				String key = ladder.style + ":" + slot.getKey();
				require(Set.of("head", "body", "legs", "weapon", "offhand", "cape", "neck", "hands", "feet", "ring").contains(slot.getKey()), key + " slot");
				require(slot.getValue() != null && !slot.getValue().isEmpty(), key + " rungs");
				List<Rung> track = tracks.computeIfAbsent(key, ignored -> new ArrayList<>());
				int previous = -1;
				for (RungDto row : slot.getValue())
				{
					require(row != null, key + " rung");
					String context = key + "/" + row.name;
					require(row.name != null && !row.name.isBlank(), context + " name");
					require(row.id != null && row.id > 0, context + " id");
					require(row.variants != null && row.variants.contains(row.id), context + " variants must include id");
					require(row.variants.stream().allMatch(id -> id != null && id > 0)
						&& new HashSet<>(row.variants).size() == row.variants.size(), context + " variants");
					require(providers.contains(row.provider), context + " unknown provider " + row.provider);
					require(row.geOnly != null, context + " geOnly");
					Rung rung = new Rung(row.name, row.id, List.copyOf(row.variants), row.provider, row.geOnly);
					int index = -1;
					for (int i = 0; i < track.size(); i++)
					{
						Rung existing = track.get(i);
						if (existing.getId() == rung.getId())
						{
							require(existing.equals(rung), context + " conflicting duplicate rung");
							index = i;
						}
						else
						{
							require(existing.getVariants().stream().noneMatch(rung.getVariants()::contains), context + " overlapping variants");
						}
					}
					if (index < 0)
					{
						index = track.size();
						track.add(rung);
					}
					require(index > previous, context + " reversed or repeated rung order");
					previous = index;
				}
			}
		}
		List<GearLadder> result = new ArrayList<>();
		tracks.forEach((key, rungs) -> result.add(new GearLadder(key.substring(0, key.indexOf(':')),
			key.substring(key.indexOf(':') + 1), List.copyOf(rungs))));
		return List.copyOf(result);
	}

	private static void require(boolean valid, String field)
	{
		if (!valid) throw new IllegalStateException("Malformed gear-ladders.json: " + field);
	}

	static final class Data
	{
		int version;
		List<LadderDto> ladders;
		List<EquipmentDto> equipment;
	}

	static final class EquipmentDto
	{
		String name;
		Integer id;
		List<Integer> variants;
		String style;
		String slot;
		String benefit;
		List<Integer> replaces;
	}

	static final class LadderDto
	{
		String style;
		String tier;
		Map<String, List<RungDto>> slots;
	}

	static final class RungDto
	{
		String name;
		Integer id;
		List<Integer> variants;
		String provider;
		Boolean geOnly;
		List<Integer> replaces;
	}
}
