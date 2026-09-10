package com.signpost.engine.model;

import java.util.List;
import java.util.Map;
import lombok.Value;

/**
 * The result of {@link com.signpost.engine.RoutePlanner#route}: an ordered training plan from a
 * bank. {@code uncoveredXp} is the remaining xp delta the bank couldn't cover
 * (0 when the route reached the target); {@code finalXp} is the simulated ending xp (may exceed
 * the target slightly, since the last step doesn't split an action); {@code simulatedBank} is the
 * bank after every step's materials/outputs.
 */
@Value
public class Route
{
	List<RouteStep> steps;
	long uncoveredXp;
	long finalXp;
	Map<Integer, Integer> simulatedBank;
}
