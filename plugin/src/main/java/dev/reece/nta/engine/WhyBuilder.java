package dev.reece.nta.engine;

import dev.reece.nta.engine.model.CombatLevelGap;
import dev.reece.nta.engine.model.Gap;
import dev.reece.nta.engine.model.GearGap;
import dev.reece.nta.engine.model.Goal;
import dev.reece.nta.engine.model.GoalCategory;
import dev.reece.nta.engine.model.GoalStatus;
import dev.reece.nta.engine.model.ItemGap;
import dev.reece.nta.engine.model.RankedGoal;
import dev.reece.nta.engine.model.SkillLevelGap;
import dev.reece.nta.kb.KnowledgeBase;
import dev.reece.nta.kb.MilestoneCategory;
import dev.reece.nta.kb.MilestoneEntry;
import dev.reece.nta.kb.OwnedItem;
import dev.reece.nta.snapshot.Snapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Deterministic, template-only "why" text for a {@link RankedGoal} (ticket D3): one to three
 * clauses joined with "; ", at most 140 characters, built only from the ranking inputs and the
 * knowledge base's curated {@code unlocks} field - never free text, and never {@code reason}
 * (shown separately by the UI). Pure: no {@link net.runelite.api.Client}, no I/O.
 */
public final class WhyBuilder
{
	private static final int MAX_LEN = 140;
	private static final int MAX_CLAUSES = 3;
	private static final int MAX_UNLOCKS = 2;

	public String why(RankedGoal r, KnowledgeBase kb, Snapshot s)
	{
		GoalStatus status = r.getStatus();
		Goal goal = status.getGoal();
		List<Gap> gaps = status.getGaps();
		boolean readyNow = status.isReady() && !status.isBankUnknown();

		List<String> clauses = new ArrayList<>();
		clauses.add(readyNow ? "Ready now" : awayClause(gaps));

		if (clauses.size() < MAX_CLAUSES && status.isBankUnknown())
		{
			clauses.add("bank unknown");
		}

		if (clauses.size() < MAX_CLAUSES && hasRecommendedGaps(gaps))
		{
			clauses.add(recommendedClause(gaps));
		}

		MilestoneEntry entry = kb.milestoneById(goal.getId());

		if (clauses.size() < MAX_CLAUSES && entry != null && isUnlockCategory(goal.getCategory()) && !entry.getUnlocks().isEmpty())
		{
			clauses.add(unlocksClause(entry));
		}

		if (clauses.size() < MAX_CLAUSES && entry != null && isBiggestGearUpgrade(entry, kb, s))
		{
			clauses.add("biggest " + entry.getSubcategory() + " upgrade over what you own");
		}

		if (clauses.size() < MAX_CLAUSES && hasMaterialsInBank(gaps))
		{
			clauses.add("materials already in bank");
		}

		if (clauses.size() < MAX_CLAUSES && isJustLevels(gaps))
		{
			clauses.add(justLevelsClause(gaps));
		}

		return truncate(clauses);
	}

	private static boolean hasRecommendedGaps(List<Gap> gaps)
	{
		for (Gap gap : gaps)
		{
			if (isRecommended(gap))
			{
				return true;
			}
		}
		return false;
	}

	private static boolean isRecommended(Gap gap)
	{
		return (gap instanceof SkillLevelGap && ((SkillLevelGap) gap).isRecommended())
			|| (gap instanceof CombatLevelGap && ((CombatLevelGap) gap).isRecommended())
			|| gap instanceof GearGap;
	}

	/** "recommended: 85 Ranged (have 70), Bandos or better" - up to three recommended items, one clause. */
	private static String recommendedClause(List<Gap> gaps)
	{
		List<String> items = new ArrayList<>();
		for (Gap gap : gaps)
		{
			if (gap instanceof SkillLevelGap && ((SkillLevelGap) gap).isRecommended())
			{
				SkillLevelGap g = (SkillLevelGap) gap;
				items.add(g.getNeed() + " " + g.getSkill().getName() + " (have " + g.getHave() + ")");
			}
			else if (gap instanceof CombatLevelGap && ((CombatLevelGap) gap).isRecommended())
			{
				CombatLevelGap g = (CombatLevelGap) gap;
				items.add("combat " + g.getNeed() + " (have " + g.getHave() + ")");
			}
			else if (gap instanceof GearGap)
			{
				items.add(((GearGap) gap).getAcceptable().get(0).getName() + " or better");
			}
		}
		return "recommended: " + items.stream().limit(3).collect(Collectors.joining(", "));
	}

	private static String awayClause(List<Gap> gaps)
	{
		int unmet = GoalMetrics.unmetCount(gaps);
		return unmet + " requirement" + (unmet == 1 ? "" : "s") + " away";
	}

	private static boolean isUnlockCategory(GoalCategory category)
	{
		return category == GoalCategory.MILESTONE || category == GoalCategory.BOSS || category == GoalCategory.SLAYER_TARGET;
	}

	private static String unlocksClause(MilestoneEntry entry)
	{
		return "unlocks " + entry.getUnlocks().stream().limit(MAX_UNLOCKS).collect(Collectors.joining(", "));
	}

	/**
	 * True for a gear milestone whose {@code gearTier} exceeds the highest {@code gearTier} among
	 * gear milestones in the same subcategory that the player already owns (any {@code ownedIf} id
	 * held in bank, inventory, or equipment). No owned gear in the subcategory counts as no tier to
	 * beat, so the entry always qualifies then.
	 */
	private static boolean isBiggestGearUpgrade(MilestoneEntry entry, KnowledgeBase kb, Snapshot s)
	{
		if (entry.getCategory() != MilestoneCategory.GEAR || entry.getGearTier() == null || entry.getSubcategory() == null)
		{
			return false;
		}
		Integer ownedMaxTier = null;
		for (MilestoneEntry other : kb.getMilestones())
		{
			if (other.getCategory() != MilestoneCategory.GEAR
				|| other.getGearTier() == null
				|| !entry.getSubcategory().equals(other.getSubcategory())
				|| !isOwned(other, s))
			{
				continue;
			}
			if (ownedMaxTier == null || other.getGearTier() > ownedMaxTier)
			{
				ownedMaxTier = other.getGearTier();
			}
		}
		return ownedMaxTier == null || entry.getGearTier() > ownedMaxTier;
	}

	private static boolean isOwned(MilestoneEntry entry, Snapshot s)
	{
		for (OwnedItem owned : entry.getOwnedIf())
		{
			if (s.getInventory().getOrDefault(owned.getId(), 0) > 0
				|| s.getEquipment().getOrDefault(owned.getId(), 0) > 0
				|| s.getBank().getOrDefault(owned.getId(), 0) > 0)
			{
				return true;
			}
		}
		return false;
	}

	/** Per rule, evaluated even though {@link dev.reece.nta.engine.GapEngine} never emits such an {@link ItemGap}. */
	private static boolean hasMaterialsInBank(List<Gap> gaps)
	{
		List<ItemGap> itemGaps = gaps.stream().filter(g -> g instanceof ItemGap).map(g -> (ItemGap) g).collect(Collectors.toList());
		return !itemGaps.isEmpty() && itemGaps.stream().allMatch(g -> g.getHave() != null && g.getHave() >= g.getNeed());
	}

	private static boolean isJustLevels(List<Gap> gaps)
	{
		return !gaps.isEmpty() && gaps.stream().allMatch(g -> g instanceof SkillLevelGap);
	}

	private static String justLevelsClause(List<Gap> gaps)
	{
		String skills = gaps.stream()
			.map(g -> (SkillLevelGap) g)
			.map(g -> g.getSkill().getName() + " " + g.getHave() + "/" + g.getNeed())
			.collect(Collectors.joining(", "));
		return "just levels: " + skills;
	}

	/** Drops trailing clauses (never mid-word) until it fits; a single clause still too long is cut with "…". */
	static String truncate(List<String> clauses)
	{
		List<String> kept = new ArrayList<>(clauses);
		String joined = String.join("; ", kept);
		while (joined.length() > MAX_LEN && kept.size() > 1)
		{
			kept.remove(kept.size() - 1);
			joined = String.join("; ", kept);
		}
		if (joined.length() <= MAX_LEN)
		{
			return joined;
		}
		return kept.get(0).substring(0, MAX_LEN - 1) + "…";
	}
}
