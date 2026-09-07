package dev.reece.nta.engine.model;

import dev.reece.nta.kb.GatheringPlan;
import java.util.List;
import lombok.Value;

/**
 * A {@link GatheringPlan} attached to a {@link ShortfallItem}, plus whether the account's skills
 * meet it. {@code meetsRequirements} is false when any {@code requires.skills} level exceeds the
 * snapshot; {@code missing} then names each unmet one ("Agility 70 (have 60)"), otherwise empty.
 */
@Value
public class PlanOffer
{
	GatheringPlan plan;
	boolean meetsRequirements;
	List<String> missing;
}
