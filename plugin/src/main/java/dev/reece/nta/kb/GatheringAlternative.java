package dev.reece.nta.kb;

import java.util.List;
import lombok.Value;

/** A secondary route to a {@link GatheringPlan}'s item (e.g. killing blue dragons for scales instead of picking spawns). */
@Value
public class GatheringAlternative
{
	String title;
	List<String> steps;
	GatheringRequires requires;
}
