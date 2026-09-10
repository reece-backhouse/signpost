package com.signpost.engine.model;

import lombok.Value;
import net.runelite.api.QuestState;

/**
 * Reward xp a skill target can take from a quest the player can do right now -
 * the quest's goal id, name and wiki url, its current state (for the quest icon), and the xp it
 * gives in the target's skill (fixed reward and any lamp allocated to it summed).
 */
@Value
public class QuestXp
{
	String questId;
	String name;
	String wikiUrl;
	QuestState state;
	long xp;
}
