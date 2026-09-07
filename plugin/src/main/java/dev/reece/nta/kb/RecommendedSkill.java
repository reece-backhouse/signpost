package dev.reece.nta.kb;

import lombok.Value;
import net.runelite.api.Skill;

/** One skill level recommended (not required) for a milestone, e.g. "80 Ranged" for a boss. */
@Value
public class RecommendedSkill
{
	Skill skill;
	int level;
}
