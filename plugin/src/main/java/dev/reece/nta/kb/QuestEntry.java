package dev.reece.nta.kb;

import java.util.List;
import lombok.Value;

/**
 * One quest's requirements, as scraped from the wiki. {@code id} matches
 * {@link net.runelite.api.Quest#getId()}. {@code questPointsRequired}/{@code kudosRequired}/
 * {@code combatLevelRequired} are {@code null} when the quest has no such requirement (the data
 * source lists them as pseudo-skill entries; {@link dev.reece.nta.kb.KnowledgeBase} normalises
 * them into these fields at load time).
 */
@Value
public class QuestEntry
{
	int id;
	String name;
	String wikiTitle;
	List<SkillReq> skills;
	List<String> prereqs;
	List<ItemReq> items;
	int questPoints;
	String source;
	Integer questPointsRequired;
	Integer kudosRequired;
	Integer combatLevelRequired;
}
