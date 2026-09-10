package com.signpost.kb;

import java.util.Set;
import lombok.Value;
import net.runelite.api.Skill;

/**
 * One experience lamp (or tome use) a quest awards: {@code xp} in any one of
 * {@code skills}, usable once that skill is at least {@code minLevel} (0 when the wiki names no
 * floor). A quest awarding "three lamps" carries three entries.
 */
@Value
public class QuestLamp
{
	long xp;
	Set<Skill> skills;
	int minLevel;
}
