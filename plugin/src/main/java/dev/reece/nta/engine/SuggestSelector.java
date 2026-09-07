package dev.reece.nta.engine;

import dev.reece.nta.engine.model.GoalCategory;
import dev.reece.nta.engine.model.RankedGoal;
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
 * exist among the remaining candidates. Pure: no {@link net.runelite.api.Client}, no I/O.
 */
public final class SuggestSelector
{
	private static final int SLOTS = 3;

	public List<RankedGoal> pick3(List<RankedGoal> ranked)
	{
		if (ranked.isEmpty())
		{
			return List.of();
		}

		List<RankedGoal> picked = new ArrayList<>();
		Set<String> used = new LinkedHashSet<>();

		// Pinned entries sit at the front of `ranked` (Ranker's contract): take up to three of them
		// first, before any category consideration.
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
			if (used.add(id(r)))
			{
				picked.add(r);
			}
		}

		if (picked.size() < SLOTS)
		{
			GoalCategory first = category(picked.get(0));
			GoalCategory second = picked.size() > 1 ? category(picked.get(1)) : null;

			RankedGoal third = second != null && first == second ? nextOfDifferentCategory(ranked, used, first) : null;
			if (third == null)
			{
				third = nextUnused(ranked, used);
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

	private static RankedGoal nextOfDifferentCategory(List<RankedGoal> ranked, Set<String> used, GoalCategory exclude)
	{
		for (RankedGoal r : ranked)
		{
			if (!used.contains(id(r)) && category(r) != exclude)
			{
				return r;
			}
		}
		return null;
	}

	private static RankedGoal nextUnused(List<RankedGoal> ranked, Set<String> used)
	{
		for (RankedGoal r : ranked)
		{
			if (!used.contains(id(r)))
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
