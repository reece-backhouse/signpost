package dev.reece.nta.kb;

import lombok.Value;

/**
 * One step of a {@link GatheringPlan} (or {@link GatheringAlternative}), with the requirements
 * an account must meet for the step to be worth showing (task 62): a Farming Guild patch step
 * needs 65 Farming, a Weiss patch step needs Making Friends with My Arm. {@code requires} only
 * carries {@code skills} and {@code quests}; {@code combatLevel}/{@code items}/{@code notes} are
 * always null/empty. The engine drops unmet steps from the offer; the whole plan's own
 * {@code requires} is judged separately.
 */
@Value
public class GatheringStep
{
	String text;
	GatheringRequires requires;
}
