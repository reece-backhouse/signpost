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
 * them. Consecutive steps of the same method are merged into one (see {@link RoutePlanner}).
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
}
