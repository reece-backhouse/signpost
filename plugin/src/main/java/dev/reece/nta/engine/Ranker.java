package dev.reece.nta.engine;

import dev.reece.nta.engine.model.GoalStatus;
import dev.reece.nta.engine.model.RankedGoal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Orders {@link GoalStatus}es into {@link RankedGoal}s per spec ruling 14: pinned goals first (in
 * pin order), then goals that are ready right now, then everything else by
 * {@code priority × closeness} descending. Pure: no {@link net.runelite.api.Client}, no I/O
 * (global constraint: engine code is pure).
 */
public final class Ranker
{
	private static final double XP_SCALE = 250_000.0;

	private static final Comparator<RankedGoal> BY_SCORE_THEN_PRIORITY_THEN_NAME = Comparator
		.comparingDouble(RankedGoal::getScore).reversed()
		.thenComparing((RankedGoal r) -> r.getStatus().getGoal().getPriority(), Comparator.reverseOrder())
		.thenComparing(r -> r.getStatus().getGoal().getName());

	public List<RankedGoal> rank(List<GoalStatus> statuses, Set<String> hidden, List<String> pins)
	{
		Map<String, GoalStatus> byId = new LinkedHashMap<>();
		for (GoalStatus status : statuses)
		{
			if (!hidden.contains(status.getGoal().getId()))
			{
				byId.put(status.getGoal().getId(), status);
			}
		}

		List<RankedGoal> pinned = new ArrayList<>();
		Set<String> used = new LinkedHashSet<>();
		for (String id : pins)
		{
			GoalStatus status = byId.get(id);
			if (status == null || !used.add(id))
			{
				continue;
			}
			pinned.add(new RankedGoal(status, score(status), true));
		}

		List<RankedGoal> ready = new ArrayList<>();
		List<RankedGoal> rest = new ArrayList<>();
		for (GoalStatus status : byId.values())
		{
			if (used.contains(status.getGoal().getId()))
			{
				continue;
			}
			RankedGoal ranked = new RankedGoal(status, score(status), false);
			(isReadyNow(status) ? ready : rest).add(ranked);
		}
		ready.sort(BY_SCORE_THEN_PRIORITY_THEN_NAME);
		rest.sort(BY_SCORE_THEN_PRIORITY_THEN_NAME);

		List<RankedGoal> result = new ArrayList<>(pinned.size() + ready.size() + rest.size());
		result.addAll(pinned);
		result.addAll(ready);
		result.addAll(rest);
		return result;
	}

	/** Ready per D2, except a status with an unseen bank is never "Ready now" even with no gaps (ruling 14). */
	private static boolean isReadyNow(GoalStatus status)
	{
		return status.isReady() && !status.isBankUnknown();
	}

	/** {@code score = priority × closeness}, {@code closeness = 1 / (1 + unmet + xpDelta / 250000)} (ruling 14). */
	static double score(GoalStatus status)
	{
		int unmet = GoalMetrics.unmetCount(status.getGaps());
		long xpDelta = GoalMetrics.xpDeltaSum(status.getGaps());
		double closeness = 1.0 / (1 + unmet + xpDelta / XP_SCALE);
		return status.getGoal().getPriority() * closeness;
	}
}
