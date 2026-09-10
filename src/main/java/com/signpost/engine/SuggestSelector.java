package com.signpost.engine;

import com.signpost.engine.model.Gap;
import com.signpost.engine.model.GoalCategory;
import com.signpost.engine.model.PrerequisiteGap;
import com.signpost.engine.model.RankedGoal;
import com.signpost.kb.KnowledgeBase;
import com.signpost.kb.MilestoneEntry;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Picks the panel's "Pick one" three suggestions from an already-{@link Ranker}-ordered list:
 * pins fill slots first (up to three, regardless of category), then
 * the ranked order fills what's left. Category diversity breaks equally valuable choices,
 * never replacing a stronger progression goal with a marginal grind. A goal marked
 * {@code later}
 * is never picked - it can still appear in {@link #rest}. A non-pinned candidate is also
 * skipped when it would pair a boss with one of its own drops (either order) already picked - see
 * {@link #pairsWithAlreadyPicked}. Pure: no {@link net.runelite.api.Client}, no I/O.
 */
public final class SuggestSelector
{
	private static final int SLOTS = 3;

	/** As {@link #pick3(List, KnowledgeBase)} with no boss/drop dedupe - for callers with no {@link KnowledgeBase} on hand. */
	public List<RankedGoal> pick3(List<RankedGoal> ranked)
	{
		return pick3(ranked, null);
	}

	/** {@code kb} may be {@code null} to skip the boss/drop pairing rule entirely. */
	public List<RankedGoal> pick3(List<RankedGoal> ranked, KnowledgeBase kb)
	{
		if (ranked.isEmpty())
		{
			return List.of();
		}
		// Gear uses provider readiness/rank, not invented GE prices. Reserve a style only after picking it.
		Set<String> ladderProviders = kb == null ? Set.of() : kb.getGearLadders().stream()
			.flatMap(l -> l.getRungs().stream()).map(r -> r.getProvider()).collect(Collectors.toSet());
		ranked = ranked.stream().filter(r -> {
			MilestoneEntry entry = kb == null ? null : kb.milestoneById(id(r));
			return r.isPinned() || entry == null || entry.getCategory() != com.signpost.kb.MilestoneCategory.GEAR
				|| !ladderProviders.contains(id(r));
		}).collect(Collectors.toList());

		List<RankedGoal> picked = new ArrayList<>();
		Set<String> used = new LinkedHashSet<>();

		// Pinned entries sit at the front of `ranked` (Ranker's contract): take up to three of them
		// first, before any category or pairing consideration - a pin is an explicit user override.
		for (RankedGoal r : ranked)
		{
			if (picked.size() >= SLOTS || !r.isPinned())
			{
				break;
			}
			if (sameUpgradeStyle(r, picked))
			{
				continue;
			}
			picked.add(r);
			used.add(id(r));
		}

		// Fill up to slot 2 from the ranked order (pins, if any, already occupy the front of it).
		for (RankedGoal r : ranked)
		{
			if (picked.size() >= 2)
			{
				break;
			}
			if (eligible(r, used) && !used.contains(id(r)) && !pairsWithAlreadyPicked(r, kb, picked))
			{
				picked.add(r);
				used.add(id(r));
			}
		}

		if (picked.isEmpty())
		{
			// Every ranked goal is "later" (or paired away): nothing to suggest, and no slot-1
			// category to compare against.
			return List.of();
		}

		if (picked.size() < SLOTS)
		{
			GoalCategory first = category(picked.get(0));
			GoalCategory second = picked.size() > 1 ? category(picked.get(1)) : null;

			RankedGoal third = nextUnused(ranked, used, kb, picked);
			RankedGoal diverse = second != null && first == second ? nextOfDifferentCategory(ranked, used, first, kb, picked) : null;
			if (third != null && diverse != null && Double.compare(diverse.getScore(), third.getScore()) == 0
				&& diverse.getStatus().isReady() == third.getStatus().isReady()
				&& diverse.getStatus().isBankUnknown() == third.getStatus().isBankUnknown())
			{
				third = diverse;
			}
			if (third != null)
			{
				picked.add(third);
			}
		}

		return List.copyOf(picked);
	}

	/** {@code ranked} minus {@code picked}, order preserved - the "next 10 / show more" list. */
	public List<RankedGoal> rest(List<RankedGoal> ranked, List<RankedGoal> picked)
	{
		Set<String> pickedIds = picked.stream().map(SuggestSelector::id).collect(Collectors.toCollection(LinkedHashSet::new));
		return ranked.stream().filter(r -> !pickedIds.contains(id(r))).collect(Collectors.toUnmodifiableList());
	}

	/**
	 * True when picking {@code r} would put a boss milestone in the same "Pick one" three as one of
	 * its own drops (e.g. God Wars Dungeon alongside Bandos armour). Checks both
	 * directions: {@code r} a gear entry whose {@link MilestoneEntry#getObtainedFrom()} boss is
	 * already in {@code used}, or {@code r} a boss whose id is some already-{@code used} entry's
	 * {@code obtainedFrom}. {@code kb == null} always returns false (no lookup possible).
	 */
	private static boolean pairsWithAlreadyPicked(RankedGoal r, KnowledgeBase kb, List<RankedGoal> picked)
	{
		if (sameUpgradeStyle(r, picked)) return true;
		if (kb == null) return false;
		String source = providerId(r);
		MilestoneEntry entry = kb.milestoneById(source);
		for (RankedGoal other : picked)
		{
			String otherSource = providerId(other);
			MilestoneEntry otherEntry = kb.milestoneById(otherSource);
			if (source.equals(otherSource)
				|| entry != null && otherSource.equals(entry.getObtainedFrom())
				|| otherEntry != null && source.equals(otherEntry.getObtainedFrom()))
			{
				return true;
			}
		}
		return false;
	}

	private static boolean sameUpgradeStyle(RankedGoal candidate, List<RankedGoal> picked)
	{
		var upgrade = candidate.getStatus().getGoal().getUpgrade();
		if (upgrade == null) return false;
		for (RankedGoal other : picked)
		{
			var existing = other.getStatus().getGoal().getUpgrade();
			if (existing != null && existing.getStyle().equals(upgrade.getStyle())) return true;
		}
		return false;
	}

	private static String providerId(RankedGoal r)
	{
		return r.getStatus().getGoal().getUpgrade() == null ? id(r)
			: r.getStatus().getGoal().getUpgrade().getTarget().getProvider();
	}

	private static RankedGoal nextOfDifferentCategory(List<RankedGoal> ranked, Set<String> used, GoalCategory exclude, KnowledgeBase kb, List<RankedGoal> picked)
	{
		for (RankedGoal r : ranked)
		{
			if (eligible(r, used) && !used.contains(id(r)) && category(r) != exclude && !pairsWithAlreadyPicked(r, kb, picked))
			{
				return r;
			}
		}
		return null;
	}

	private static RankedGoal nextUnused(List<RankedGoal> ranked, Set<String> used, KnowledgeBase kb, List<RankedGoal> picked)
	{
		for (RankedGoal r : ranked)
		{
			if (eligible(r, used) && !used.contains(id(r)) && !pairsWithAlreadyPicked(r, kb, picked))
			{
				return r;
			}
		}
		return null;
	}

	/**
	 * Automatic picks must follow their unmet prerequisites even when category diversity, hiding,
	 * or the later/pairing filters skip a prerequisite. Pins bypass this check; blocked goals remain
	 * in the ranked/rest list and can still be focused explicitly.
	 */
	private static boolean eligible(RankedGoal r, Set<String> used)
	{
		if (r.isLater() || r.getStatus().isUncoveredTarget())
		{
			return false;
		}
		// An unseen item is not a confirmed upgrade objective. Explicit pins can request a check.
		if (r.getStatus().getObjective() != null && !r.getStatus().getObjective().hasConfirmedReason()) return false;
		for (Gap gap : r.getStatus().getGaps())
		{
			if (gap instanceof PrerequisiteGap && !used.contains(((PrerequisiteGap) gap).getGoalId()))
			{
				return false;
			}
		}
		return true;
	}

	private static GoalCategory category(RankedGoal r)
	{
		GoalCategory category = r.getStatus().getGoal().getCategory();
		return category == GoalCategory.GEAR_UPGRADE ? GoalCategory.MILESTONE : category;
	}

	private static String id(RankedGoal r)
	{
		return r.getStatus().getGoal().getId();
	}
}
