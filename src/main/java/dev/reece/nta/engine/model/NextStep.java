package dev.reece.nta.engine.model;

import lombok.Value;

/**
 * "Do this next" (ticket E3): exactly one of the four factories below is used, matching
 * {@link #getType()}. Every other field is {@code null} for that variant.
 */
@Value
public class NextStep
{
	NextStepType type;
	QuestPrereqGap questGap;
	SkillLevelGap skillGap;
	Route route;
	ItemGap itemGap;

	private NextStep(NextStepType type, QuestPrereqGap questGap, SkillLevelGap skillGap, Route route, ItemGap itemGap)
	{
		this.type = type;
		this.questGap = questGap;
		this.skillGap = skillGap;
		this.route = route;
		this.itemGap = itemGap;
	}

	/** E3(a): an unfinished prerequisite quest whose own requirements are fully met. */
	public static NextStep quest(QuestPrereqGap gap)
	{
		return new NextStep(NextStepType.QUEST, gap, null, null, null);
	}

	/** E3(b)/(c): a skill gap with its route from the bank (fully covering it for (b), partial for (c)). */
	public static NextStep skill(SkillLevelGap gap, Route route)
	{
		return new NextStep(NextStepType.SKILL, null, gap, route, null);
	}

	/** E3(d): the first missing item. */
	public static NextStep item(ItemGap gap)
	{
		return new NextStep(NextStepType.ITEM, null, null, null, gap);
	}

	/** Nothing left to pick: every requirement is already met. */
	public static NextStep none()
	{
		return new NextStep(NextStepType.NONE, null, null, null, null);
	}
}
