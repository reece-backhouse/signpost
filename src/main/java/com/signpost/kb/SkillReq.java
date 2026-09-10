package com.signpost.kb;

import lombok.Value;
import net.runelite.api.Skill;

/**
 * One real skill requirement for a quest or diary task. The data source's non-skill pseudo
 * requirements ("Quest point"/"Kudos" on quests, "Combat" on diary tasks) are normalised into
 * dedicated fields on {@link QuestEntry}/{@link DiaryTask} instead of appearing here.
 */
@Value
public class SkillReq
{
	Skill skill;
	int level;
	boolean boostable;
	boolean ironmanOnly;
}
