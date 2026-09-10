package com.signpost.engine.model;

import lombok.Value;
import java.util.List;
import java.util.stream.Collectors;

/**
 * "Do this next": exactly one of the four factories below is used, matching
 * {@link #getType()}. Variant payloads are null when unused; guidance is added after selection.
 */
@Value
public class NextStep
{
	NextStepType type;
	QuestPrereqGap questGap;
	SkillLevelGap skillGap;
	Route route;
	ItemGap itemGap;
	List<BringItemStatus> bringItems;
	String where;
	String doText;

	private NextStep(NextStepType type, QuestPrereqGap questGap, SkillLevelGap skillGap, Route route, ItemGap itemGap)
	{
		this(type, questGap, skillGap, route, itemGap, List.of(), null, null);
	}

	private NextStep(NextStepType type, QuestPrereqGap questGap, SkillLevelGap skillGap, Route route, ItemGap itemGap,
		List<BringItemStatus> bringItems, String where, String doText)
	{
		this.type = type;
		this.questGap = questGap;
		this.skillGap = skillGap;
		this.route = route;
		this.itemGap = itemGap;
		this.bringItems = List.copyOf(bringItems);
		this.where = where;
		this.doText = doText;
	}

	public NextStep withGuidance(List<BringItemStatus> items, String where, String doText)
	{
		return new NextStep(type, questGap, skillGap, route, itemGap, items, where, doText);
	}

	public String getBring()
	{
		return bringItems.isEmpty() ? "Nothing extra" : bringItems.stream().map(BringItemStatus::text).collect(Collectors.joining(", "));
	}

	public boolean hasGuidance()
	{
		return where != null && doText != null;
	}

	/** An unfinished prerequisite quest whose own requirements are fully met. */
	public static NextStep quest(QuestPrereqGap gap)
	{
		return new NextStep(NextStepType.QUEST, gap, null, null, null);
	}

	/** A skill gap with its fully covering or partial route from the bank. */
	public static NextStep skill(SkillLevelGap gap, Route route)
	{
		return new NextStep(NextStepType.SKILL, null, gap, route, null);
	}

	/** The first missing item. */
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
