package dev.reece.nta.engine.model;

import dev.reece.nta.kb.GatheringPlan;
import java.util.List;
import lombok.Value;

/**
 * A {@link GatheringPlan} attached to a {@link ShortfallItem}, plus whether the account's skills
 * meet it. {@code meetsRequirements} is false when any {@code requires.skills} level exceeds the
 * snapshot; {@code missing} then names each unmet one ("Agility 70 (have 60)"), otherwise empty.
 * {@code steps} is the plan's step text with every step whose own requirements the account
 * doesn't meet dropped (task 62), in plan order; {@code alternativeSteps} is the same per
 * alternative, index-aligned with {@code plan.getAlternatives()}. Panels render these, never the
 * plan's raw steps.
 */
@Value
public class PlanOffer
{
	GatheringPlan plan;
	boolean meetsRequirements;
	List<String> missing;
	List<String> steps;
	List<List<String>> alternativeSteps;
}
