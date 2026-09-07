package dev.reece.nta.snapshot;

import dev.reece.nta.engine.DiaryProgress;
import dev.reece.nta.kb.KnowledgeBase;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.Varbits;
import net.runelite.client.game.ItemManager;

/**
 * Thin, obviously-correct read of client state into a {@link Snapshot}. No logic beyond map
 * building; must run on the client thread since it touches {@link Client} and {@link ItemManager}.
 */
public final class SnapshotCollector
{
	private static final int[] COMBAT_ACHIEVEMENT_TIER_VARBITS = {
		Varbits.COMBAT_ACHIEVEMENT_TIER_EASY,
		Varbits.COMBAT_ACHIEVEMENT_TIER_MEDIUM,
		Varbits.COMBAT_ACHIEVEMENT_TIER_HARD,
		Varbits.COMBAT_ACHIEVEMENT_TIER_ELITE,
		Varbits.COMBAT_ACHIEVEMENT_TIER_MASTER,
		Varbits.COMBAT_ACHIEVEMENT_TIER_GRANDMASTER,
	};

	private SnapshotCollector()
	{
	}

	public static Snapshot collect(Client client, ItemManager itemManager, CachedBank bank, KnowledgeBase kb)
	{
		if (!client.isClientThread())
		{
			throw new IllegalStateException("SnapshotCollector.collect must run on the client thread");
		}

		Map<Skill, SkillState> skills = new EnumMap<>(Skill.class);
		for (Skill skill : Skill.values())
		{
			if (skill == Skill.OVERALL)
			{
				continue;
			}
			skills.put(skill, new SkillState(client.getRealSkillLevel(skill), client.getSkillExperience(skill)));
		}

		Map<Quest, QuestState> quests = new EnumMap<>(Quest.class);
		for (Quest quest : Quest.values())
		{
			quests.put(quest, quest.getState(client));
		}

		Map<Integer, Integer> inventory = readContainer(client, InventoryID.INVENTORY);
		Map<Integer, Integer> equipment = readContainer(client, InventoryID.EQUIPMENT);

		Map<Integer, String> itemNames = new HashMap<>();
		collectItemNames(itemManager, itemNames, bank.getItems().keySet());
		collectItemNames(itemManager, itemNames, inventory.keySet());
		collectItemNames(itemManager, itemNames, equipment.keySet());

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
			combatAchievementTiers.put(varbitId, client.getVarbitValue(varbitId) != 0);
		}

		return Snapshot.builder()
			.accountHash(client.getAccountHash())
			.accountType(AccountType.fromVarbit(client.getVarbitValue(Varbits.ACCOUNT_TYPE)))
			.skills(skills)
			.quests(quests)
			.bank(bank.getItems())
			.inventory(inventory)
			.equipment(equipment)
			.itemNames(itemNames)
			.diaryTiers(diaryTiers)
			.diaryVarps(diaryVarps)
			.karamjaVarbits(karamjaVarbits)
			.diaryCountVarbits(diaryCountVarbits)
			.combatAchievementTiers(combatAchievementTiers)
			.bankKnown(bank.isKnown())
			.bankAsOf(bank.getAsOf())
			.build();
	}

	private static Map<Integer, Integer> readContainer(Client client, InventoryID inventoryId)
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

	private static void collectItemNames(ItemManager itemManager, Map<Integer, String> itemNames, Iterable<Integer> ids)
	{
		for (int id : ids)
		{
			itemNames.put(id, itemManager.getItemComposition(id).getName());
		}
	}
}
