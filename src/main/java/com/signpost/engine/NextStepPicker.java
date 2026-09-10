package com.signpost.engine;

import com.signpost.engine.model.DiaryTaskGap;
import com.signpost.engine.model.Gap;
import com.signpost.engine.model.GoalStatus;
import com.signpost.engine.model.ItemGap;
import com.signpost.engine.model.NextStep;
import com.signpost.engine.model.QuestPrereqGap;
import com.signpost.engine.model.Route;
import com.signpost.engine.model.SkillLevelGap;
import com.signpost.kb.KnowledgeBase;
import com.signpost.snapshot.SkillState;
import com.signpost.snapshot.Snapshot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Experience;
import net.runelite.api.Skill;

/**
 * "Do this next": picks one unmet requirement from a {@link GoalStatus}, in order:
 * (a) a start-here quest prereq, (b) the smallest-xp-delta skill gap with a bank-covered route,
 * (c) the smallest-xp-delta skill gap overall with its partial route, (d) the first missing
 * item. {@code none()} when the goal has no gaps left. No {@link net.runelite.api.Client}, no I/O.
 */
public final class NextStepPicker
{
	public NextStep next(GoalStatus status, Snapshot snapshot, KnowledgeBase kb)
	{
		List<QuestPrereqGap> quests = new ArrayList<>();
		List<SkillLevelGap> skills = new ArrayList<>();
		List<ItemGap> items = new ArrayList<>();
		collect(status.getGaps(), quests, skills, items);

		for (QuestPrereqGap gap : quests)
		{
			if (gap.isStartHere())
			{
				return NextStep.quest(gap);
			}
		}

		Map<Integer, Integer> bankAll = bankAll(snapshot);
		Map<List<Object>, Route> cache = new HashMap<>();

		SkillLevelGap covered = null;
		Route coveredRoute = null;
		for (SkillLevelGap gap : skills)
		{
			Route route = routeFor(gap, snapshot, bankAll, kb, cache);
			if (route.getUncoveredXp() == 0 && (covered == null || gap.getXpDelta() < covered.getXpDelta()))
			{
				covered = gap;
				coveredRoute = route;
			}
		}
		if (covered != null)
		{
			return NextStep.skill(covered, coveredRoute);
		}

		SkillLevelGap smallest = null;
		Route smallestRoute = null;
		for (SkillLevelGap gap : skills)
		{
			if (smallest == null || gap.getXpDelta() < smallest.getXpDelta())
			{
				smallest = gap;
				smallestRoute = routeFor(gap, snapshot, bankAll, kb, cache);
			}
		}
		if (smallest != null)
		{
			return NextStep.skill(smallest, smallestRoute);
		}

		if (!items.isEmpty())
		{
			return NextStep.item(items.get(0));
		}

		return NextStep.none();
	}

	private static Route routeFor(SkillLevelGap gap, Snapshot snapshot, Map<Integer, Integer> bankAll, KnowledgeBase kb,
		Map<List<Object>, Route> cache)
	{
		long toXp = Experience.getXpForLevel(gap.getNeed());
		List<Object> key = List.of(gap.getSkill(), toXp);
		Route cached = cache.get(key);
		if (cached != null)
		{
			return cached;
		}
		Route route = RoutePlanner.route(gap.getSkill(), currentXp(snapshot, gap.getSkill()), toXp, bankAll, kb);
		cache.put(key, route);
		return route;
	}

	private static long currentXp(Snapshot snapshot, Skill skill)
	{
		SkillState state = snapshot.getSkills().get(skill);
		return state == null ? 0 : state.getXp();
	}

	/**
	 * Bank &cup; inventory &cup; equipment &cup; group storage by item id - the materials a
	 * {@link RoutePlanner} route may draw on (the shared storage is one pool with the bank;
	 * {@link Snapshot#groupStorageShare} gives the per-item breakdown). Shared with {@link SkillTargetSynthesiser}.
	 */
	static Map<Integer, Integer> bankAll(Snapshot snapshot)
	{
		Map<Integer, Integer> result = new LinkedHashMap<>();
		mergeInto(result, snapshot.getBank());
		mergeInto(result, snapshot.getGroupStorage());
		mergeInto(result, snapshot.getInventory());
		mergeInto(result, snapshot.getRunePouch());
		mergeInto(result, snapshot.getEquipment());
		return result;
	}

	private static void mergeInto(Map<Integer, Integer> into, Map<Integer, Integer> from)
	{
		from.forEach((id, qty) -> into.merge(id, qty, Integer::sum));
	}

	/** Recursively finds every {@link QuestPrereqGap}/{@link SkillLevelGap}/{@link ItemGap}, including inside {@link DiaryTaskGap}s. */
	private static void collect(List<Gap> gaps, List<QuestPrereqGap> quests, List<SkillLevelGap> skills, List<ItemGap> items)
	{
		for (Gap gap : gaps)
		{
			if (gap instanceof QuestPrereqGap)
			{
				quests.add((QuestPrereqGap) gap);
			}
			else if (gap instanceof SkillLevelGap)
			{
				skills.add((SkillLevelGap) gap);
			}
			else if (gap instanceof ItemGap)
			{
				items.add((ItemGap) gap);
			}
			else if (gap instanceof DiaryTaskGap)
			{
				collect(((DiaryTaskGap) gap).getGaps(), quests, skills, items);
			}
		}
	}
}
