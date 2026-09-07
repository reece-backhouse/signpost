package dev.reece.nta.snapshot;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Builder;
import lombok.Value;
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
	Map<Integer, Boolean> combatAchievementTiers;
	boolean bankKnown;
	Instant bankAsOf;

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
		Map<Integer, Boolean> combatAchievementTiers,
		boolean bankKnown,
		Instant bankAsOf)
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
		this.combatAchievementTiers = copyOf(combatAchievementTiers);
		this.bankKnown = bankKnown;
		this.bankAsOf = bankAsOf;
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
}
