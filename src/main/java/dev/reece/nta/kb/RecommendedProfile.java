package dev.reece.nta.kb;

import java.util.List;
import lombok.Value;

/**
 * The curated "actually ready" profile for a milestone (typically a boss), separate from its hard
 * entry {@code requirements} (spec ruling 27): recommended skill levels, a recommended combat
 * level, and gear, satisfied only once at least {@link #effectiveGearOwnedMin()} distinct
 * {@code gearOwnedAny} items are owned (task 46 - previously any single one counted, which let a
 * boss with a long "actually ready" gear list be satisfied by one unrelated item). Every field may
 * be empty/null when the milestone has no recommendation in that dimension; a milestone with no
 * recommendation at all has {@code recommended == null} on {@link MilestoneEntry}, not an empty
 * profile.
 */
@Value
public class RecommendedProfile
{
	List<RecommendedSkill> skills;
	Integer combatLevel;
	List<OwnedItem> gearOwnedAny;
	/** Curated minimum distinct {@code gearOwnedAny} items required; {@code null} defaults to {@code ceil(gearOwnedAny.size() / 2.0)}. */
	Integer gearOwnedMin;

	/** {@link #gearOwnedMin} if set, else half of {@link #gearOwnedAny}'s size, rounded up. */
	public int effectiveGearOwnedMin()
	{
		return gearOwnedMin != null ? gearOwnedMin : (int) Math.ceil(gearOwnedAny.size() / 2.0);
	}
}
