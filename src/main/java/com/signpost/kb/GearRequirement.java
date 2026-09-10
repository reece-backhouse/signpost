package com.signpost.kb;

import java.util.List;
import java.util.Set;
import lombok.Value;

/** A required equipment role; any alternative (or a curated replacement) satisfies it. */
@Value
public class GearRequirement
{
	String role;
	List<OwnedItem> alternatives;

	public GearRequirement(String role, List<OwnedItem> alternatives)
	{
		if (!validRole(role) || alternatives == null || alternatives.isEmpty() || alternatives.stream().anyMatch(java.util.Objects::isNull))
		{
			throw new IllegalArgumentException("Invalid gear requirement: " + role);
		}
		this.role = role;
		this.alternatives = List.copyOf(alternatives);
	}

	static boolean validRole(String role)
	{
		if ("tool".equals(role) || "melee-stab".equals(role) || "melee-slash".equals(role)) return true;
		if (role == null) return false;
		String[] parts = role.split("-", -1);
		return parts.length == 2 && Set.of("melee", "ranged", "magic").contains(parts[0])
			&& Set.of("head", "body", "legs", "weapon", "offhand", "cape", "neck", "hands", "feet", "ring").contains(parts[1]);
	}
}
