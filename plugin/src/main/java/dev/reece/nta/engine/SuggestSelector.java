package dev.reece.nta.engine;

import dev.reece.nta.engine.model.GoalCategory;
import dev.reece.nta.engine.model.RankedGoal;
import dev.reece.nta.kb.KnowledgeBase;
import dev.reece.nta.kb.MilestoneEntry;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Picks the panel's "Pick one" three suggestions from an already-{@link Ranker}-ordered list, per
 * spec ruling 17 / ticket D4: pins fill slots first (up to three, regardless of category), then
 * the ranked order fills what's left; the last of the three slots must be a different
 * {@link GoalCategory} from the first two when they share one, unless fewer than two categories
 * exist among the remaining candidates. A goal {@link Ranker} marked {@code later} (spec ruling 27)
 * is never picked - it can still appear in {@link #rest}. Task 46: a non-pinned candidate is also
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
			if (!r.isLater() && !used.contains(id(r)) && !pairsWithAlreadyPicked(r, kb, used))
			{
				picked.add(r);
				used.add(id(r));
			}
		}

		if (picked.size() < SLOTS)
		{
			GoalCategory first = category(picked.get(0));
			GoalCategory second = picked.size() > 1 ? category(picked.get(1)) : null;

			RankedGoal third = second != null && first == second ? nextOfDifferentCategory(ranked, used, first, kb) : null;
			if (third == null)
			{
				third = nextUnused(ranked, used, kb);
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
	 * its own drops (spec: God Wars Dungeon must never sit alongside Bandos armour). Checks both
	 * directions: {@code r} a gear entry whose {@link MilestoneEntry#getObtainedFrom()} boss is
	 * already in {@code used}, or {@code r} a boss whose id is some already-{@code used} entry's
	 * {@code obtainedFrom}. {@code kb == null} always returns false (no lookup possible).
	 */
	private static boolean pairsWithAlreadyPicked(RankedGoal r, KnowledgeBase kb, Set<String> used)
	{
		if (kb == null)
		{
			return false;
		}
		String goalId = id(r);
		MilestoneEntry entry = kb.milestoneById(goalId);
		if (entry != null && entry.getObtainedFrom() != null && used.contains(entry.getObtainedFrom()))
		{
			return true;
		}
		for (String usedId : used)
		{
			MilestoneEntry usedEntry = kb.milestoneById(usedId);
			if (usedEntry != null && goalId.equals(usedEntry.getObtainedFrom()))
			{
				return true;
			}
		}
		return false;
	}

	private static RankedGoal nextOfDifferentCategory(List<RankedGoal> ranked, Set<String> used, GoalCategory exclude, KnowledgeBase kb)
	{
		for (RankedGoal r : ranked)
		{
			if (!r.isLater() && !used.contains(id(r)) && category(r) != exclude && !pairsWithAlreadyPicked(r, kb, used))
			{
				return r;
			}
		}
		return null;
	}

	private static RankedGoal nextUnused(List<RankedGoal> ranked, Set<String> used, KnowledgeBase kb)
	{
		for (RankedGoal r : ranked)
		{
			if (!r.isLater() && !used.contains(id(r)) && !pairsWithAlreadyPicked(r, kb, used))
			{
				return r;
			}
		}
		return null;
	}

	private static GoalCategory category(RankedGoal r)
	{
		return r.getStatus().getGoal().getCategory();
	}

	private static String id(RankedGoal r)
	{
		return r.getStatus().getGoal().getId();
	}
}
