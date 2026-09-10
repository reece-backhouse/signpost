package com.signpost.snapshot;

import com.signpost.engine.DiaryProgress;
import com.signpost.kb.KnowledgeBase;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.game.ItemManager;

/**
 * Thin, obviously-correct read of client state into a {@link Snapshot}. No logic beyond map
 * building; must run on the client thread since it touches {@link Client} and {@link ItemManager}.
 */
public final class SnapshotCollector
{
	private static final int[] COMBAT_ACHIEVEMENT_TIER_VARBITS = {
		VarbitID.CA_TIER_STATUS_EASY,
		VarbitID.CA_TIER_STATUS_MEDIUM,
		VarbitID.CA_TIER_STATUS_HARD,
		VarbitID.CA_TIER_STATUS_ELITE,
		VarbitID.CA_TIER_STATUS_MASTER,
		VarbitID.CA_TIER_STATUS_GRANDMASTER,
	};

	private SnapshotCollector()
	{
	}

	/** {@code countGroupStorage} is the "Count group storage" toggle: off, the cached storage is left out of the snapshot (kept in memory and on disk). */
	public static Snapshot collect(Client client, ItemManager itemManager, CachedBank bank, CachedBank groupStorage, boolean countGroupStorage,
		KnowledgeBase kb, BossProgressReader bossProgressReader)
	{
		if (!client.isClientThread())
		{
			throw new IllegalStateException("SnapshotCollector.collect must run on the client thread");
		}
		if (!countGroupStorage)
		{
			groupStorage = CachedBank.unknown();
		}

		Map<Skill, SkillState> skills = new EnumMap<>(Skill.class);
		for (Skill skill : Skill.values())
		{
			skills.put(skill, new SkillState(client.getRealSkillLevel(skill), client.getSkillExperience(skill)));
		}

		Map<Quest, QuestState> quests = new EnumMap<>(Quest.class);
		for (Quest quest : Quest.values())
		{
			quests.put(quest, quest.getState(client));
		}

		Map<Integer, Integer> inventory = readContainer(client, InventoryID.INV);
		Map<Integer, Integer> equipment = readContainer(client, InventoryID.WORN);
		Map<Integer, Integer> runePouch = AccountUnlockReader.runePouch(client, inventory);

		Map<Integer, String> itemNames = new HashMap<>();
		collectItemNames(itemManager, itemNames, bank.getItems().keySet());
		collectItemNames(itemManager, itemNames, groupStorage.getItems().keySet());
		collectItemNames(itemManager, itemNames, inventory.keySet());
		collectItemNames(itemManager, itemNames, equipment.keySet());
		collectItemNames(itemManager, itemNames, runePouch.keySet());

		Map<DiaryTier, Boolean> diaryTiers = new EnumMap<>(DiaryTier.class);
		for (DiaryTier tier : DiaryTier.values())
		{
			diaryTiers.put(tier, client.getVarbitValue(tier.getVarbitId()) != 0);
		}

		Map<Integer, Integer> diaryVarps = new HashMap<>();
		for (int varp : kb.diaryVarps())
		{
			diaryVarps.put(varp, client.getVarpValue(varp));
		}

		Map<Integer, Integer> karamjaVarbits = new HashMap<>();
		for (int varbit : kb.diaryVarbits())
		{
			karamjaVarbits.put(varbit, client.getVarbitValue(varbit));
		}

		Map<Integer, Integer> diaryCountVarbits = new HashMap<>();
		for (int varbit : DiaryProgress.COUNT_VARBITS.values())
		{
			diaryCountVarbits.put(varbit, client.getVarbitValue(varbit));
		}

		Map<Integer, Boolean> combatAchievementTiers = new HashMap<>();
		for (int varbitId : COMBAT_ACHIEVEMENT_TIER_VARBITS)
		{
			combatAchievementTiers.put(varbitId, isCombatAchievementTierComplete(client.getVarbitValue(varbitId)));
		}

		return Snapshot.builder()
			.accountHash(client.getAccountHash())
			.accountType(AccountType.fromVarbit(client.getVarbitValue(VarbitID.IRONMAN)))
			.skills(skills)
			.quests(quests)
			.bank(bank.getItems())
			.inventory(inventory)
			.equipment(equipment)
			.runePouch(runePouch)
			.slayer(AccountUnlockReader.slayer(client))
			.rigour(client.getVarbitValue(VarbitID.PRAYER_RIGOUR_UNLOCKED) != 0)
			.augury(client.getVarbitValue(VarbitID.PRAYER_AUGURY_UNLOCKED) != 0)
			.preserve(client.getVarbitValue(VarbitID.PRAYER_PRESERVE_UNLOCKED) != 0)
			.spellbook(Spellbook.fromVarbit(client.getVarbitValue(VarbitID.SPELLBOOK)))
			.itemNames(itemNames)
			.diaryTiers(diaryTiers)
			.diaryVarps(diaryVarps)
			.karamjaVarbits(karamjaVarbits)
			.diaryCountVarbits(diaryCountVarbits)
			.combatAchievementTiers(combatAchievementTiers)
			.bossProgress(bossProgressReader.read(client))
			.bankKnown(bank.isKnown())
			.bankAsOf(bank.getAsOf())
			.groupStorage(groupStorage.getItems())
			.groupStorageKnown(groupStorage.isKnown())
			.groupStorageAsOf(groupStorage.getAsOf())
			.groupStorageEnabled(countGroupStorage)
			.questPoints(client.getVarpValue(VarPlayerID.QP))
			.kudos(client.getVarbitValue(VarbitID.VM_KUDOS))
			.build();
	}

	private static Map<Integer, Integer> readContainer(Client client, int inventoryId)
	{
		ItemContainer container = client.getItemContainer(inventoryId);
		if (container == null)
		{
			return Map.of();
		}

		Map<Integer, Integer> items = new HashMap<>();
		for (Item item : container.getItems())
		{
			if (isRealItem(item))
			{
				items.merge(item.getId(), item.getQuantity(), Integer::sum);
			}
		}
		return items;
	}

	/**
	 * RuneLite fills empty container slots with a placeholder {@code Item(-1, 0)}; this excludes
	 * those (and any other non-positive id or quantity) so they never reach a Snapshot map or an
	 * {@code ItemManager.getItemComposition} lookup.
	 */
	public static boolean isRealItem(Item item)
	{
		return item.getId() > 0 && item.getQuantity() > 0;
	}

	/**
	 * A combat achievement tier counts as complete only once its rewards are claimed. Evidence:
	 * RuneLite's {@code Varbits.COMBAT_ACHIEVEMENT_TIER_*} javadoc documents these varbits as
	 * "2 = completed", and Gielinor Compass reads them as {@code >= 2}; value 1 is the
	 * tasks-done-but-unclaimed state the old {@code != 0} check wrongly counted as done.
	 */
	static boolean isCombatAchievementTierComplete(int status)
	{
		return status >= 2;
	}

	private static void collectItemNames(ItemManager itemManager, Map<Integer, String> itemNames, Iterable<Integer> ids)
	{
		for (int id : ids)
		{
			itemNames.put(id, itemManager.getItemComposition(id).getName());
		}
	}
}
