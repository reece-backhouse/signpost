package com.signpost.kb;

import java.util.List;
import lombok.Value;

/** A secondary route to a {@link GatheringPlan}'s item (e.g. killing blue dragons for scales instead of picking spawns). */
@Value
public class GatheringAlternative
{
	String title;
	List<GatheringStep> steps;
	GatheringRequires requires;
	GatheringRisk risk;

	public GatheringAlternative(String title, List<GatheringStep> steps, GatheringRequires requires)
	{
		this(title, steps, requires, GatheringRisk.NONE);
	}

	public GatheringAlternative(String title, List<GatheringStep> steps, GatheringRequires requires, GatheringRisk risk)
	{
		this.title = title;
		this.steps = List.copyOf(steps);
		this.requires = requires;
		this.risk = risk;
	}
}
