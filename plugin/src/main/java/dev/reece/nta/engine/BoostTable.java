package dev.reece.nta.engine;

import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Skill;

/**
 * Curated maximum realistic boost per skill (ruling 12: the engine never reads live boosted
 * levels from the client; this table is what {@code SkillLevelGap.boostableFrom} is computed
 * against). Values are display-only estimates of commonly available boosts and may drift from
 * current game balance - update this table deliberately, not the engine, when they do.
 */
public final class BoostTable
{
	private static final Map<Skill, Integer> DEFAULTS = buildDefaults();

	private final Map<Skill, Integer> maxBoosts;

	public BoostTable()
	{
		this(DEFAULTS);
	}

	public BoostTable(Map<Skill, Integer> maxBoosts)
	{
		this.maxBoosts = Map.copyOf(maxBoosts);
	}

	/** The largest realistic temporary boost for {@code skill}, or 0 if none is curated. */
	public int maxBoost(Skill skill)
	{
		return maxBoosts.getOrDefault(skill, 0);
	}

	private static Map<Skill, Integer> buildDefaults()
	{
		Map<Skill, Integer> m = new HashMap<>();
		m.put(Skill.AGILITY, 5);
		m.put(Skill.CONSTRUCTION, 8); // crystal saw (3) + spicy stew (5)
		m.put(Skill.COOKING, 5);
		m.put(Skill.CRAFTING, 4);
		m.put(Skill.FARMING, 5);
		m.put(Skill.FIREMAKING, 5);
		m.put(Skill.FISHING, 5);
		m.put(Skill.FLETCHING, 5);
		m.put(Skill.HERBLORE, 6);
		m.put(Skill.HUNTER, 5);
		m.put(Skill.MAGIC, 10); // imbued heart
		m.put(Skill.MINING, 3);
		m.put(Skill.RUNECRAFT, 5);
		m.put(Skill.SLAYER, 5);
		m.put(Skill.SMITHING, 5);
		m.put(Skill.THIEVING, 5);
		m.put(Skill.WOODCUTTING, 3);
		m.put(Skill.PRAYER, 0);
		return Map.copyOf(m);
	}
}
