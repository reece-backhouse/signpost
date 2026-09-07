package dev.reece.nta.engine.model;

import lombok.EqualsAndHashCode;
import lombok.Value;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;

/**
 * An unfinished prerequisite quest, resolved transitively (a prerequisite's own unfinished
 * prerequisites appear as their own {@link QuestPrereqGap}s). {@code startHere} is true only on
 * the deepest unfinished quest(s): those with no unfinished prerequisite of their own.
 */
@Value
@EqualsAndHashCode(callSuper = false)
public class QuestPrereqGap extends Gap
{
	Quest quest;
	QuestState state;
	boolean startHere;
}
