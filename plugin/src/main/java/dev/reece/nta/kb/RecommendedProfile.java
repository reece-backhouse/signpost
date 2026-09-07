package dev.reece.nta.kb;

import java.util.List;
import lombok.Value;

/**
 * The curated "actually ready" profile for a milestone (typically a boss), separate from its hard
 * entry {@code requirements} (spec ruling 27): recommended skill levels, a recommended combat
 * level, and gear where owning any one acceptable item counts as met. Every field may be empty/null
 * when the milestone has no recommendation in that dimension; a milestone with no recommendation at
 * all has {@code recommended == null} on {@link MilestoneEntry}, not an empty profile.
 */
@Value
public class RecommendedProfile
{
	List<RecommendedSkill> skills;
	Integer combatLevel;
	List<OwnedItem> gearOwnedAny;
}
