package dev.reece.nta.engine.model;

import dev.reece.nta.kb.MethodEntry;
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
 * - so a method the planner returns to after a better one runs dry is listed once (task 59, see
 * {@link dev.reece.nta.engine.RoutePlanner}).
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
	/** RL-003: how much of {@code materialsUsed} the group ironman shared storage supplied, by item id (set by {@link dev.reece.nta.engine.GroupStorageShares}); empty until then. */
	Map<Integer, Integer> fromGroupStorage;

	public RouteStep(MethodEntry method, int count, int fromLevel, int toLevel, long xpGained, Map<Integer, Integer> materialsUsed,
		List<RouteStep> crafts)
	{
		this(method, count, fromLevel, toLevel, xpGained, materialsUsed, crafts, Map.of());
	}

	public RouteStep(MethodEntry method, int count, int fromLevel, int toLevel, long xpGained, Map<Integer, Integer> materialsUsed,
		List<RouteStep> crafts, Map<Integer, Integer> fromGroupStorage)
	{
		this.method = method;
		this.count = count;
		this.fromLevel = fromLevel;
		this.toLevel = toLevel;
		this.xpGained = xpGained;
		this.materialsUsed = materialsUsed;
		this.crafts = crafts;
		this.fromGroupStorage = fromGroupStorage;
	}
}
