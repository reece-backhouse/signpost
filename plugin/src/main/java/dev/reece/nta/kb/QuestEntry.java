package dev.reece.nta.kb;

import java.util.List;
import lombok.Value;

/**
 * One quest's requirements, as scraped from the wiki. {@code id} matches
 * {@link net.runelite.api.Quest#getId()}. {@code questPointsRequired}/{@code kudosRequired}/
 * {@code combatLevelRequired} are {@code null} when the quest has no such requirement (the data
 * source lists them as pseudo-skill entries; {@link dev.reece.nta.kb.KnowledgeBase} normalises
 * them into these fields at load time). {@code prereqs} must be FINISHED; {@code prereqsStarted}
 * only need to have been started (the wiki's {@code Started:} prefix); every name in both lists
 * resolves to another quest in the knowledge base (validated at load time). {@code prereqNotes} is
 * raw prerequisite text that didn't resolve to any RuneLite quest (e.g. a Barbarian Training
 * sub-activity) - kept for display, never evaluated.
 */
@Value
public class QuestEntry
{
	int id;
	String name;
	String wikiTitle;
	List<SkillReq> skills;
	List<String> prereqs;
	List<String> prereqsStarted;
	List<String> prereqNotes;
	List<ItemReq> items;
	int questPoints;
	String source;
	Integer questPointsRequired;
	Integer kudosRequired;
	Integer combatLevelRequired;
}
