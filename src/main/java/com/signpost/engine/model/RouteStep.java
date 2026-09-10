package com.signpost.engine.model;

import com.signpost.kb.MethodEntry;
import java.util.List;
import java.util.Map;
import lombok.Value;

/**
 * One entry in a {@link Route}: performing {@code method} {@code count} times, taking the player
 * from {@code fromLevel} to {@code toLevel} (capped at 99). {@code crafts} are the 0-xp
 * intermediate methods (see {@link MethodEntry#isIntermediate()}) run first, from the simulated
 * bank alone, to afford {@code method}'s materials - e.g. making unfinished potions before brewing
 * them. Every run of the same method across the route is merged into its first step - count,
 * xp, materials and crafts summed, {@code fromLevel} the first run's, {@code toLevel} the last's
 * - so a method the planner returns to after a better one runs dry is listed once (see
 * {@link com.signpost.engine.RoutePlanner}). A quest step has {@code quest} set
 * and {@code method} null: doing that quest once credits its reward xp before any method runs.
 */
@Value
public class RouteStep
{
	MethodEntry method;
	int count;
	int fromLevel;
	int toLevel;
	long xpGained;
	Map<Integer, Integer> materialsUsed;
	List<RouteStep> crafts;
	/** How much of {@code materialsUsed} the group ironman shared storage supplied, by item id (set by {@link com.signpost.engine.GroupStorageShares}); empty until then. */
	Map<Integer, Integer> fromGroupStorage;
	QuestXp quest;

	public RouteStep(MethodEntry method, int count, int fromLevel, int toLevel, long xpGained, Map<Integer, Integer> materialsUsed,
		List<RouteStep> crafts)
	{
		this(method, count, fromLevel, toLevel, xpGained, materialsUsed, crafts, Map.of(), null);
	}

	public RouteStep(MethodEntry method, int count, int fromLevel, int toLevel, long xpGained, Map<Integer, Integer> materialsUsed,
		List<RouteStep> crafts, Map<Integer, Integer> fromGroupStorage)
	{
		this(method, count, fromLevel, toLevel, xpGained, materialsUsed, crafts, fromGroupStorage, null);
	}

	public RouteStep(MethodEntry method, int count, int fromLevel, int toLevel, long xpGained, Map<Integer, Integer> materialsUsed,
		List<RouteStep> crafts, QuestXp quest)
	{
		this(method, count, fromLevel, toLevel, xpGained, materialsUsed, crafts, Map.of(), quest);
	}

	public RouteStep(MethodEntry method, int count, int fromLevel, int toLevel, long xpGained, Map<Integer, Integer> materialsUsed,
		List<RouteStep> crafts, Map<Integer, Integer> fromGroupStorage, QuestXp quest)
	{
		this.method = method;
		this.count = count;
		this.fromLevel = fromLevel;
		this.toLevel = toLevel;
		this.xpGained = xpGained;
		this.materialsUsed = materialsUsed;
		this.crafts = crafts;
		this.fromGroupStorage = fromGroupStorage;
		this.quest = quest;
	}
}
