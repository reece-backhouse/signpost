package com.signpost.kb;

import java.util.List;
import lombok.Value;

/**
 * What a {@link GatheringPlan} (or {@link GatheringAlternative}) needs to run: real skill levels,
 * quest names, and item names, plus free-text {@code notes}. {@code combatLevel} is split out the
 * same way {@link QuestEntry}/{@link DiaryTask} split "Combat" out of their skill lists - the
 * curated data's one combat-gated plan (Dragon bones) names it as a skill entry, but
 * {@code net.runelite.api.Skill} has no COMBAT value. {@code notes} is {@code null} for the dozen
 * curated alternatives that don't carry one.
 */
@Value
public class GatheringRequires
{
	List<SkillReq> skills;
	Integer combatLevel;
	List<String> quests;
	List<BringItem> items;
	String notes;
}
