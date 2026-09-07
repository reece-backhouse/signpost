package dev.reece.nta.engine;

import dev.reece.nta.kb.KnowledgeBase;
import dev.reece.nta.kb.MilestoneCategory;
import dev.reece.nta.kb.MilestoneEntry;
import dev.reece.nta.kb.OwnedItem;
import dev.reece.nta.snapshot.Snapshot;
import net.runelite.api.QuestState;

/**
 * Derives the account's progression stage (1 early game .. 4 endgame) from combat level, total
 * level, finished quest count, and owned gear milestones (spec ruling 27). Every threshold lives
 * here, in one place:
 *
 * <ul>
 *   <li>Stage 4: owns &ge; 2 distinct stage-4 gear milestones, or (combat &ge; 120 and owns &ge; 2
 *       stage-3 gear milestones).
 *   <li>Stage 3 (else): owns &ge; 2 stage-3 gear milestones, or (combat &ge; 110 and total level
 *       &ge; 1900), or &ge; 190 quests finished.
 *   <li>Stage 2 (else): combat &ge; 85, or total level &ge; 1400, or &ge; 120 quests finished, or
 *       owns &ge; 2 stage-2 gear milestones.
 *   <li>Stage 1 (else).
 * </ul>
 *
 * "Owns" a gear milestone means any of its {@code ownedIf} item ids is held in bank, inventory, or
 * equipment (bank checked only when {@link Snapshot#isBankKnown()}). Pure: no
 * {@link net.runelite.api.Client}, no I/O.
 */
public final class StageEstimator
{
	private StageEstimator()
	{
	}

	public static int estimate(Snapshot snapshot, KnowledgeBase kb)
	{
		int combat = snapshot.combatLevel();
		int totalLevel = totalLevel(snapshot);
		int questsFinished = questsFinished(snapshot);

		if (ownedGearMilestoneCount(kb, snapshot, 4) >= 2 || (combat >= 120 && ownedGearMilestoneCount(kb, snapshot, 3) >= 2))
		{
			return 4;
		}
		if (ownedGearMilestoneCount(kb, snapshot, 3) >= 2 || (combat >= 110 && totalLevel >= 1900) || questsFinished >= 190)
		{
			return 3;
		}
		if (combat >= 85 || totalLevel >= 1400 || questsFinished >= 120 || ownedGearMilestoneCount(kb, snapshot, 2) >= 2)
		{
			return 2;
		}
		return 1;
	}

	private static int totalLevel(Snapshot snapshot)
	{
		return snapshot.getSkills().values().stream().mapToInt(s -> s.getLevel()).sum();
	}

	private static int questsFinished(Snapshot snapshot)
	{
		return (int) snapshot.getQuests().values().stream().filter(state -> state == QuestState.FINISHED).count();
	}

	private static int ownedGearMilestoneCount(KnowledgeBase kb, Snapshot snapshot, int stage)
	{
		int count = 0;
		for (MilestoneEntry entry : kb.getMilestones())
		{
			if (entry.getCategory() == MilestoneCategory.GEAR && entry.getStage() == stage && anyOwned(entry.getOwnedIf(), snapshot))
			{
				count++;
			}
		}
		return count;
	}

	private static boolean anyOwned(Iterable<OwnedItem> ownedIf, Snapshot snapshot)
	{
		for (OwnedItem owned : ownedIf)
		{
			if (GapEngine.anyIdHeld(owned.getIds(), snapshot))
			{
				return true;
			}
		}
		return false;
	}
}
