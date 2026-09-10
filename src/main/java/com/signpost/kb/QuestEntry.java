package com.signpost.kb;

import java.util.List;
import java.util.Map;
import lombok.Value;
import net.runelite.api.Skill;

/**
 * One quest's requirements, as scraped from the wiki. {@code id} matches
 * {@link net.runelite.api.Quest#getId()}. {@code questPointsRequired}/{@code kudosRequired}/
 * {@code combatLevelRequired} are {@code null} when the quest has no such requirement (the data
 * source lists them as pseudo-skill entries; {@link com.signpost.kb.KnowledgeBase} normalises
 * them into these fields at load time). {@code prereqs} must be FINISHED; {@code prereqsStarted}
 * only need to have been started (the wiki's {@code Started:} prefix); every name in both lists
 * resolves to another quest in the knowledge base (validated at load time). {@code prereqNotes} is
 * raw prerequisite text that didn't resolve to any RuneLite quest (e.g. a Barbarian Training
 * sub-activity) - kept for display, never evaluated. {@code rewardXp} is the fixed
 * experience the quest awards per skill and {@code lamps} its choice lamps, both parsed conservatively
 * from the wiki (a reward the build could not read cleanly is absent, never guessed).
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
	Map<Skill, Long> rewardXp;
	List<QuestLamp> lamps;

	/** As the full constructor with no reward xp and no lamps. */
	public QuestEntry(int id, String name, String wikiTitle, List<SkillReq> skills, List<String> prereqs, List<String> prereqsStarted,
		List<String> prereqNotes, List<ItemReq> items, int questPoints, String source, Integer questPointsRequired, Integer kudosRequired,
		Integer combatLevelRequired)
	{
		this(id, name, wikiTitle, skills, prereqs, prereqsStarted, prereqNotes, items, questPoints, source, questPointsRequired, kudosRequired,
			combatLevelRequired, Map.of(), List.of());
	}

	public QuestEntry(int id, String name, String wikiTitle, List<SkillReq> skills, List<String> prereqs, List<String> prereqsStarted,
		List<String> prereqNotes, List<ItemReq> items, int questPoints, String source, Integer questPointsRequired, Integer kudosRequired,
		Integer combatLevelRequired, Map<Skill, Long> rewardXp, List<QuestLamp> lamps)
	{
		this.id = id;
		this.name = name;
		this.wikiTitle = wikiTitle;
		this.skills = skills;
		this.prereqs = prereqs;
		this.prereqsStarted = prereqsStarted;
		this.prereqNotes = prereqNotes;
		this.items = items;
		this.questPoints = questPoints;
		this.source = source;
		this.questPointsRequired = questPointsRequired;
		this.kudosRequired = kudosRequired;
		this.combatLevelRequired = combatLevelRequired;
		this.rewardXp = Map.copyOf(rewardXp);
		this.lamps = List.copyOf(lamps);
	}
}
