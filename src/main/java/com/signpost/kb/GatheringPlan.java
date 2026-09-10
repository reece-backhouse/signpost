package com.signpost.kb;

import java.util.List;
import lombok.Value;

/**
 * One curated step-by-step loop from {@code gathering.json} for gathering a
 * material shortfall - "teleport to Falador, take the Taverley pipe shortcut, pick up the blue
 * dragon scale spawns...". {@code id} is the {@code materials.json} item id it targets, resolved by
 * kb-build. {@code ratePerHour} is {@code null} for the curated Farming loops (yield is per-cycle,
 * not a steady hourly rate - see the plan's {@code requires.notes}).
 */
@Value
public class GatheringPlan
{
	String item;
	int id;
	String title;
	GatheringRequires requires;
	Integer ratePerHour;
	List<GatheringStep> steps;
	List<GatheringAlternative> alternatives;
	String wikiUrl;
	GatheringRisk risk;

	public GatheringPlan(String item, int id, String title, GatheringRequires requires, Integer ratePerHour,
		List<GatheringStep> steps, List<GatheringAlternative> alternatives, String wikiUrl)
	{
		this(item, id, title, requires, ratePerHour, steps, alternatives, wikiUrl, GatheringRisk.NONE);
	}

	public GatheringPlan(String item, int id, String title, GatheringRequires requires, Integer ratePerHour,
		List<GatheringStep> steps, List<GatheringAlternative> alternatives, String wikiUrl, GatheringRisk risk)
	{
		this.item = item;
		this.id = id;
		this.title = title;
		this.requires = requires;
		this.ratePerHour = ratePerHour;
		this.steps = List.copyOf(steps);
		this.alternatives = List.copyOf(alternatives);
		this.wikiUrl = wikiUrl;
		this.risk = risk;
	}
}
