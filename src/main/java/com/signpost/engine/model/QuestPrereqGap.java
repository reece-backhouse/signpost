package com.signpost.engine.model;

import lombok.EqualsAndHashCode;
import lombok.Value;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;

/**
 * An unfinished prerequisite quest, resolved transitively (a prerequisite's own unfinished
 * prerequisites appear as their own {@link QuestPrereqGap}s). {@code startHere} is true only on
 * the deepest unfinished quest(s): those with no unfinished prerequisite of their own - always true
 * for a {@code startOnly} gap, since those are never recursed into. {@code startOnly} is true when
 * this is a "must have started" prerequisite (the wiki's {@code Started:} prefix): satisfied by
 * {@code QuestState.IN_PROGRESS}, not just {@code FINISHED}. {@code wikiUrl} is built by
 * {@link com.signpost.engine.GapEngine} from the knowledge base's {@code QuestEntry.wikiTitle},
 * the same way {@link Goal#getWikiUrl()} is - never from {@code quest.getName()}, which 404s for
 * a subquest whose wiki title differs (e.g. a Recipe for Disaster subquest's title has a
 * {@code "Recipe for Disaster/"} prefix the RuneLite {@code Quest} name doesn't).
 */
@Value
@EqualsAndHashCode(callSuper = false)
public class QuestPrereqGap extends Gap
{
	Quest quest;
	QuestState state;
	boolean startHere;
	boolean startOnly;
	String wikiUrl;
}
