package dev.reece.nta.snapshot;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Builder;
import lombok.Value;
import net.runelite.api.Experience;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;

/**
 * Immutable point-in-time read of the account's state, as produced by {@link SnapshotCollector}.
 * All map fields are defensively copied and unmodifiable.
 */
@Value
public class Snapshot
{
	long accountHash;
	AccountType accountType;
	Map<Skill, SkillState> skills;
	Map<Quest, QuestState> quests;
	Map<Integer, Integer> bank;
	Map<Integer, Integer> inventory;
	Map<Integer, Integer> equipment;
	Map<Integer, String> itemNames;
	Map<DiaryTier, Boolean> diaryTiers;
	Map<Integer, Integer> diaryVarps;
	Map<Integer, Integer> karamjaVarbits;
	Map<Integer, Integer> diaryCountVarbits;
	Map<Integer, Boolean> combatAchievementTiers;
	boolean bankKnown;
	Instant bankAsOf;
	int questPoints;
	int kudos;

	@Builder(toBuilder = true)
	private Snapshot(
		long accountHash,
		AccountType accountType,
		Map<Skill, SkillState> skills,
		Map<Quest, QuestState> quests,
		Map<Integer, Integer> bank,
		Map<Integer, Integer> inventory,
		Map<Integer, Integer> equipment,
		Map<Integer, String> itemNames,
		Map<DiaryTier, Boolean> diaryTiers,
		Map<Integer, Integer> diaryVarps,
		Map<Integer, Integer> karamjaVarbits,
		Map<Integer, Integer> diaryCountVarbits,
		Map<Integer, Boolean> combatAchievementTiers,
		boolean bankKnown,
		Instant bankAsOf,
		int questPoints,
		int kudos)
	{
		this.accountHash = accountHash;
		this.accountType = accountType;
		this.skills = copyOf(skills);
		this.quests = copyOf(quests);
		this.bank = copyOf(bank);
		this.inventory = copyOf(inventory);
		this.equipment = copyOf(equipment);
		this.itemNames = copyOf(itemNames);
		this.diaryTiers = copyOf(diaryTiers);
		this.diaryVarps = copyOf(diaryVarps);
		this.karamjaVarbits = copyOf(karamjaVarbits);
		this.diaryCountVarbits = copyOf(diaryCountVarbits);
		this.combatAchievementTiers = copyOf(combatAchievementTiers);
		this.bankKnown = bankKnown;
		this.bankAsOf = bankAsOf;
		this.questPoints = questPoints;
		this.kudos = kudos;
	}

	private static <K, V> Map<K, V> copyOf(Map<K, V> map)
	{
		return map == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(map));
	}

	/**
	 * Returns a copy of this snapshot with the bank fields replaced from {@code cachedBank}.
	 */
	public Snapshot withBank(CachedBank cachedBank)
	{
		return toBuilder()
			.bank(cachedBank.getItems())
			.bankKnown(cachedBank.isKnown())
			.bankAsOf(cachedBank.getAsOf())
			.build();
	}

	/**
	 * Combat level derived from {@link #skills}, via {@link Experience#getCombatLevel}. A skill
	 * missing from the map (e.g. an empty test snapshot) is treated as level 1.
	 */
	public int combatLevel()
	{
		return Experience.getCombatLevel(
			levelOf(Skill.ATTACK), levelOf(Skill.STRENGTH), levelOf(Skill.DEFENCE), levelOf(Skill.HITPOINTS),
			levelOf(Skill.MAGIC), levelOf(Skill.RANGED), levelOf(Skill.PRAYER));
	}

	private int levelOf(Skill skill)
	{
		SkillState state = skills.get(skill);
		return state == null ? 1 : state.getLevel();
	}
}
