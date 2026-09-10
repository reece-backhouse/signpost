package com.signpost.kb;

import java.util.List;
import lombok.Value;

/**
 * One task within an achievement diary tier. {@code combatLevelRequired} is {@code null} when the
 * task has no combat level requirement (normalised out of {@code skills} at load time, see
 * {@link com.signpost.kb.KnowledgeBase}).
 */
@Value
public class DiaryTask
{
	int ordinal;
	String text;
	List<SkillReq> skills;
	List<String> quests;
	List<String> items;
	List<String> notes;
	TaskCompletion completion;
	Integer combatLevelRequired;
}
