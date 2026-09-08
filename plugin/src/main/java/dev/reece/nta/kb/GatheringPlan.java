package dev.reece.nta.kb;

import java.util.List;
import lombok.Value;

/**
 * One curated step-by-step loop from {@code gathering.json} (spec ruling 28) for gathering a
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
}
