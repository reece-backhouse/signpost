package dev.reece.nta.kb;

import java.util.List;
import lombok.Value;

/**
 * One quest's requirements, as scraped from the wiki. {@code id} matches
 * {@link net.runelite.api.Quest#getId()}.
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
}
