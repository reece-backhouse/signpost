package dev.reece.nta.kb;

import java.util.List;
import lombok.Value;

/**
 * One task within an achievement diary tier.
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
}
