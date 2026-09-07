package dev.reece.nta.kb;

import lombok.Value;
import net.runelite.api.Skill;

/**
 * One skill requirement for a quest or diary task. {@code skill} is {@code null} when
 * {@code skillName} is a pseudo-skill the data source uses that has no {@link Skill} constant
 * (e.g. "Quest point", "Kudos", "Combat") - callers needing those should read {@link #skillName}.
 */
@Value
public class SkillReq
{
	Skill skill;
	String skillName;
	int level;
	boolean boostable;
	boolean ironmanOnly;
}
