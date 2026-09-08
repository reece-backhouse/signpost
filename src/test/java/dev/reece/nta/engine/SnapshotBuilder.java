package dev.reece.nta.engine;

import dev.reece.nta.snapshot.AccountType;
import dev.reece.nta.snapshot.DiaryTier;
import dev.reece.nta.snapshot.SkillState;
import dev.reece.nta.snapshot.Snapshot;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Experience;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;

/**
 * Fluent builder for a {@link Snapshot} fixture. Defaults: {@link AccountType#NORMAL}, every
 * skill level 1 / 0 xp, every quest {@link QuestState#NOT_STARTED}, bank known and empty, every
 * diary tier incomplete, 0 quest points, 0 kudos. Shared by every engine test so fixtures aren't
 * hand-rolled per test.
 */
final class SnapshotBuilder
{
	private long accountHash = 1L;
	private AccountType accountType = AccountType.NORMAL;
	private final Map<Skill, SkillState> skills = new EnumMap<>(Skill.class);
	private final Map<Quest, QuestState> quests = new EnumMap<>(Quest.class);
	private final Map<Integer, Integer> bank = new HashMap<>();
	private final Map<Integer, Integer> inventory = new HashMap<>();
	private final Map<Integer, Integer> equipment = new HashMap<>();
	private final Map<Integer, String> itemNames = new HashMap<>();
	private final Map<DiaryTier, Boolean> diaryTiers = new EnumMap<>(DiaryTier.class);
	private final Map<Integer, Integer> diaryVarps = new HashMap<>();
	private final Map<Integer, Integer> karamjaVarbits = new HashMap<>();
	private final Map<Integer, Integer> diaryCountVarbits = new HashMap<>();
	private final Map<Integer, Boolean> combatAchievementTiers = new HashMap<>();
	private final Map<Integer, Integer> groupStorage = new HashMap<>();
	private boolean bankKnown = true;
	private boolean groupStorageKnown;
	private int questPoints;
	private int kudos;

	SnapshotBuilder()
	{
		for (Skill skill : Skill.values())
		{
			skills.put(skill, new SkillState(1, 0));
		}
		for (Quest quest : Quest.values())
		{
			quests.put(quest, QuestState.NOT_STARTED);
		}
		for (DiaryTier tier : DiaryTier.values())
		{
			diaryTiers.put(tier, false);
		}
	}

	SnapshotBuilder accountType(AccountType type)
	{
		this.accountType = type;
		return this;
	}

	SnapshotBuilder iron()
	{
		return accountType(AccountType.IRONMAN);
	}

	SnapshotBuilder skill(Skill skill, int level)
	{
		skills.put(skill, new SkillState(level, Experience.getXpForLevel(level)));
		return this;
	}

	SnapshotBuilder xp(Skill skill, long xp)
	{
		skills.put(skill, new SkillState(Experience.getLevelForXp((int) xp), (int) xp));
		return this;
	}

	SnapshotBuilder quest(Quest quest, QuestState state)
	{
		quests.put(quest, state);
		return this;
	}

	SnapshotBuilder bankItem(int id, String name, int qty)
	{
		bank.merge(id, qty, Integer::sum);
		itemNames.put(id, name);
		return this;
	}

	SnapshotBuilder inventoryItem(int id, String name, int qty)
	{
		inventory.merge(id, qty, Integer::sum);
		itemNames.put(id, name);
		return this;
	}

	SnapshotBuilder equipmentItem(int id, String name, int qty)
	{
		equipment.merge(id, qty, Integer::sum);
		itemNames.put(id, name);
		return this;
	}

	/** RL-003: an item in the group ironman shared storage; marks the storage as seen. */
	SnapshotBuilder groupStorageItem(int id, String name, int qty)
	{
		groupStorage.merge(id, qty, Integer::sum);
		itemNames.put(id, name);
		groupStorageKnown = true;
		return this;
	}

	SnapshotBuilder groupStorageKnown()
	{
		groupStorageKnown = true;
		return this;
	}

	SnapshotBuilder bankUnknown()
	{
		bankKnown = false;
		return this;
	}

	SnapshotBuilder diaryTier(DiaryTier tier, boolean complete)
	{
		diaryTiers.put(tier, complete);
		return this;
	}

	SnapshotBuilder diaryVarp(int varp, int value)
	{
		diaryVarps.put(varp, value);
		return this;
	}

	SnapshotBuilder karamjaVarbit(int id, int value)
	{
		karamjaVarbits.put(id, value);
		return this;
	}

	SnapshotBuilder diaryCountVarbit(int id, int value)
	{
		diaryCountVarbits.put(id, value);
		return this;
	}

	SnapshotBuilder combatAchievementTier(int varbit, boolean done)
	{
		combatAchievementTiers.put(varbit, done);
		return this;
	}

	SnapshotBuilder questPoints(int n)
	{
		this.questPoints = n;
		return this;
	}

	SnapshotBuilder kudos(int n)
	{
		this.kudos = n;
		return this;
	}

	Snapshot build()
	{
		return Snapshot.builder()
			.accountHash(accountHash)
			.accountType(accountType)
			.skills(skills)
			.quests(quests)
			.bank(bank)
			.inventory(inventory)
			.equipment(equipment)
			.itemNames(itemNames)
			.diaryTiers(diaryTiers)
			.diaryVarps(diaryVarps)
			.karamjaVarbits(karamjaVarbits)
			.diaryCountVarbits(diaryCountVarbits)
			.combatAchievementTiers(combatAchievementTiers)
			.bankKnown(bankKnown)
			.groupStorage(groupStorage)
			.groupStorageKnown(groupStorageKnown)
			.questPoints(questPoints)
			.kudos(kudos)
			.build();
	}
}
