package com.signpost.engine;

import com.signpost.kb.DiaryEntry;
import com.signpost.kb.DiaryTask;
import com.signpost.kb.KnowledgeBase;
import com.signpost.snapshot.DiaryTier;
import com.signpost.snapshot.Snapshot;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.gameval.VarbitID;

/**
 * Computes per-tier achievement diary progress from a {@link Snapshot} and {@link KnowledgeBase},
 * and cross-checks it against the game's own per-tier completed-task counter varbit as a
 * self-check that the bundled task->bit mapping is correct. Pure: no {@link net.runelite.api.Client}.
 */
public final class DiaryProgress
{
	/**
	 * The game's own "tasks completed" counter varbit for each diary tier, used only to self-check
	 * {@link #compute}'s bit-derived count. Referencing {@link VarbitID} constants by name keeps
	 * this compile-checked against the client; every one of the 48 tiers has one.
	 */
	public static final Map<DiaryTier, Integer> COUNT_VARBITS = buildCountVarbits();

	private DiaryProgress()
	{
	}

	public static Map<DiaryTier, DiaryTierProgress> compute(Snapshot snapshot, KnowledgeBase kb)
	{
		Map<DiaryTier, DiaryTierProgress> result = new EnumMap<>(DiaryTier.class);
		for (DiaryTier tier : DiaryTier.values())
		{
			DiaryEntry entry = kb.diary(tier);
			if (entry == null)
			{
				continue;
			}

			List<Integer> completedOrdinals = new ArrayList<>();
			for (DiaryTask task : entry.getTasks())
			{
				if (task.getCompletion().isComplete(snapshot))
				{
					completedOrdinals.add(task.getOrdinal());
				}
			}
			int completed = completedOrdinals.size();
			int total = entry.getTasks().size();

			Integer countVarbit = COUNT_VARBITS.get(tier);
			Integer gameCount = countVarbit == null ? null : snapshot.getDiaryCountVarbits().get(countVarbit);
			boolean mismatch = gameCount != null && gameCount != completed;

			result.put(tier, new DiaryTierProgress(completed, total, completedOrdinals, gameCount, mismatch));
		}
		return result;
	}

	private static Map<DiaryTier, Integer> buildCountVarbits()
	{
		Map<DiaryTier, Integer> m = new EnumMap<>(DiaryTier.class);
		m.put(DiaryTier.ARDOUGNE_EASY, VarbitID.ARDOUGNE_EASY_COUNT);
		m.put(DiaryTier.ARDOUGNE_MEDIUM, VarbitID.ARDOUGNE_MED_COUNT);
		m.put(DiaryTier.ARDOUGNE_HARD, VarbitID.ARDOUGNE_HARD_COUNT);
		m.put(DiaryTier.ARDOUGNE_ELITE, VarbitID.ARDOUGNE_ELITE_COUNT);
		m.put(DiaryTier.DESERT_EASY, VarbitID.DESERT_EASY_COUNT);
		m.put(DiaryTier.DESERT_MEDIUM, VarbitID.DESERT_MED_COUNT);
		m.put(DiaryTier.DESERT_HARD, VarbitID.DESERT_HARD_COUNT);
		m.put(DiaryTier.DESERT_ELITE, VarbitID.DESERT_ELITE_COUNT);
		m.put(DiaryTier.FALADOR_EASY, VarbitID.FALADOR_EASY_COUNT);
		m.put(DiaryTier.FALADOR_MEDIUM, VarbitID.FALADOR_MED_COUNT);
		m.put(DiaryTier.FALADOR_HARD, VarbitID.FALADOR_HARD_COUNT);
		m.put(DiaryTier.FALADOR_ELITE, VarbitID.FALADOR_ELITE_COUNT);
		m.put(DiaryTier.FREMENNIK_EASY, VarbitID.FREMENNIK_EASY_COUNT);
		m.put(DiaryTier.FREMENNIK_MEDIUM, VarbitID.FREMENNIK_MED_COUNT);
		m.put(DiaryTier.FREMENNIK_HARD, VarbitID.FREMENNIK_HARD_COUNT);
		m.put(DiaryTier.FREMENNIK_ELITE, VarbitID.FREMENNIK_ELITE_COUNT);
		m.put(DiaryTier.KANDARIN_EASY, VarbitID.KANDARIN_EASY_COUNT);
		m.put(DiaryTier.KANDARIN_MEDIUM, VarbitID.KANDARIN_MED_COUNT);
		m.put(DiaryTier.KANDARIN_HARD, VarbitID.KANDARIN_HARD_COUNT);
		m.put(DiaryTier.KANDARIN_ELITE, VarbitID.KANDARIN_ELITE_COUNT);
		m.put(DiaryTier.KARAMJA_EASY, VarbitID.KARAMJA_EASY_COUNT);
		m.put(DiaryTier.KARAMJA_MEDIUM, VarbitID.KARAMJA_MED_COUNT);
		m.put(DiaryTier.KARAMJA_HARD, VarbitID.KARAMJA_HARD_COUNT);
		m.put(DiaryTier.KARAMJA_ELITE, VarbitID.KARAMJA_ELITE_COUNT);
		m.put(DiaryTier.KOUREND_EASY, VarbitID.KOUREND_EASY_COUNT);
		m.put(DiaryTier.KOUREND_MEDIUM, VarbitID.KOUREND_MED_COUNT);
		m.put(DiaryTier.KOUREND_HARD, VarbitID.KOUREND_HARD_COUNT);
		m.put(DiaryTier.KOUREND_ELITE, VarbitID.KOUREND_ELITE_COUNT);
		m.put(DiaryTier.LUMBRIDGE_EASY, VarbitID.LUMBRIDGE_EASY_COUNT);
		m.put(DiaryTier.LUMBRIDGE_MEDIUM, VarbitID.LUMBRIDGE_MED_COUNT);
		m.put(DiaryTier.LUMBRIDGE_HARD, VarbitID.LUMBRIDGE_HARD_COUNT);
		m.put(DiaryTier.LUMBRIDGE_ELITE, VarbitID.LUMBRIDGE_ELITE_COUNT);
		m.put(DiaryTier.MORYTANIA_EASY, VarbitID.MORYTANIA_EASY_COUNT);
		m.put(DiaryTier.MORYTANIA_MEDIUM, VarbitID.MORYTANIA_MED_COUNT);
		m.put(DiaryTier.MORYTANIA_HARD, VarbitID.MORYTANIA_HARD_COUNT);
		m.put(DiaryTier.MORYTANIA_ELITE, VarbitID.MORYTANIA_ELITE_COUNT);
		m.put(DiaryTier.VARROCK_EASY, VarbitID.VARROCK_EASY_COUNT);
		m.put(DiaryTier.VARROCK_MEDIUM, VarbitID.VARROCK_MED_COUNT);
		m.put(DiaryTier.VARROCK_HARD, VarbitID.VARROCK_HARD_COUNT);
		m.put(DiaryTier.VARROCK_ELITE, VarbitID.VARROCK_ELITE_COUNT);
		m.put(DiaryTier.WESTERN_EASY, VarbitID.WESTERN_EASY_COUNT);
		m.put(DiaryTier.WESTERN_MEDIUM, VarbitID.WESTERN_MED_COUNT);
		m.put(DiaryTier.WESTERN_HARD, VarbitID.WESTERN_HARD_COUNT);
		m.put(DiaryTier.WESTERN_ELITE, VarbitID.WESTERN_ELITE_COUNT);
		m.put(DiaryTier.WILDERNESS_EASY, VarbitID.WILDERNESS_EASY_COUNT);
		m.put(DiaryTier.WILDERNESS_MEDIUM, VarbitID.WILDERNESS_MED_COUNT);
		m.put(DiaryTier.WILDERNESS_HARD, VarbitID.WILDERNESS_HARD_COUNT);
		m.put(DiaryTier.WILDERNESS_ELITE, VarbitID.WILDERNESS_ELITE_COUNT);
		return Map.copyOf(m);
	}
}
