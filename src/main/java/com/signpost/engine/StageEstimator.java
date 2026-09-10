package com.signpost.engine;

import com.signpost.kb.KnowledgeBase;
import com.signpost.kb.MilestoneCategory;
import com.signpost.kb.MilestoneEntry;
import com.signpost.snapshot.Snapshot;
import java.util.Set;
import net.runelite.api.QuestState;

/**
 * Derives the account's progression stage (1 early game .. 4 endgame) from combat level, total
 * level, finished quest count, and owned gear milestones. Every threshold lives
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
 * equipment (bank checked only when {@link Snapshot#isBankKnown()}), or the milestone id is in the
 * player's manually-marked-owned set ({@link GapEngine#isOwned}). Pure: no
 * {@link net.runelite.api.Client}, no I/O.
 */
public final class StageEstimator
{
	private StageEstimator()
	{
	}

	public static int estimate(Snapshot snapshot, KnowledgeBase kb)
	{
		return estimate(snapshot, kb, Set.of());
	}

	/** As {@link #estimate(Snapshot, KnowledgeBase)}, but a manually-owned gear milestone counts as owned too. */
	public static int estimate(Snapshot snapshot, KnowledgeBase kb, Set<String> ownedManually)
	{
		int combat = snapshot.combatLevel();
		int totalLevel = totalLevel(snapshot);
		int questsFinished = questsFinished(snapshot);

		if (ownedGearMilestoneCount(kb, snapshot, 4, ownedManually) >= 2
			|| (combat >= 120 && ownedGearMilestoneCount(kb, snapshot, 3, ownedManually) >= 2))
		{
			return 4;
		}
		if (ownedGearMilestoneCount(kb, snapshot, 3, ownedManually) >= 2 || (combat >= 110 && totalLevel >= 1900) || questsFinished >= 190)
		{
			return 3;
		}
		if (combat >= 85 || totalLevel >= 1400 || questsFinished >= 120 || ownedGearMilestoneCount(kb, snapshot, 2, ownedManually) >= 2)
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

	private static int ownedGearMilestoneCount(KnowledgeBase kb, Snapshot snapshot, int stage, Set<String> ownedManually)
	{
		int count = 0;
		for (MilestoneEntry entry : kb.getMilestones())
		{
			if (entry.getCategory() == MilestoneCategory.GEAR && entry.getStage() == stage && GapEngine.isOwned(entry, snapshot, ownedManually))
			{
				count++;
			}
		}
		return count;
	}
}
