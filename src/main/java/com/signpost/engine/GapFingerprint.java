package com.signpost.engine;

import com.signpost.engine.model.PrayerUnlockGap;
import com.signpost.engine.model.SlayerPointsGap;
import com.signpost.engine.model.SlayerUnlockGap;
import com.signpost.engine.model.CombatLevelGap;
import com.signpost.engine.model.DiaryTaskGap;
import com.signpost.engine.model.DiaryTierGap;
import com.signpost.engine.model.Gap;
import com.signpost.engine.model.GearGap;
import com.signpost.engine.model.GoalStatus;
import com.signpost.engine.model.ItemGap;
import com.signpost.engine.model.KudosGap;
import com.signpost.engine.model.PrerequisiteGap;
import com.signpost.engine.model.QuestPointsGap;
import com.signpost.engine.model.QuestPrereqGap;
import com.signpost.engine.model.SkillLevelGap;
import com.signpost.kb.OwnedItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A stable, order-independent digest over a {@link GoalStatus}'s gap kinds and keys (not its
 * counts or estimates), used by {@link PrefsResolver} to detect when a snoozed goal's
 * requirements have changed (a changed gap ends the snooze early). Pure: no
 * {@link net.runelite.api.Client}, no I/O.
 */
public final class GapFingerprint
{
	private GapFingerprint()
	{
	}

	public static String of(GoalStatus status)
	{
		List<String> keys = new ArrayList<>();
		for (Gap gap : status.getGaps())
		{
			keys.add(keyOf(gap));
		}
		Collections.sort(keys);
		return String.join("|", keys);
	}

	private static String keyOf(Gap gap)
	{
		if (gap instanceof SlayerPointsGap)
		{
			return "slayerPoints:" + ((SlayerPointsGap) gap).getNeed();
		}
		if (gap instanceof SlayerUnlockGap)
		{
			return "slayerUnlock:" + ((SlayerUnlockGap) gap).getReward().name();
		}
		if (gap instanceof PrayerUnlockGap)
		{
			return "prayer:" + ((PrayerUnlockGap) gap).getPrayer().name();
		}
		if (gap instanceof SkillLevelGap)
		{
			SkillLevelGap g = (SkillLevelGap) gap;
			return "skill:" + g.getSkill().name() + ":" + g.getNeed();
		}
		if (gap instanceof QuestPrereqGap)
		{
			return "quest:" + ((QuestPrereqGap) gap).getQuest().name();
		}
		if (gap instanceof ItemGap)
		{
			ItemGap g = (ItemGap) gap;
			return "item:" + g.getName() + ":" + g.getNeed();
		}
		if (gap instanceof DiaryTaskGap)
		{
			DiaryTaskGap g = (DiaryTaskGap) gap;
			List<String> inner = new ArrayList<>();
			for (Gap innerGap : g.getGaps())
			{
				inner.add(keyOf(innerGap));
			}
			Collections.sort(inner);
			return "diaryTask:" + g.getOrdinal() + ":[" + String.join(",", inner) + "]";
		}
		if (gap instanceof DiaryTierGap)
		{
			return "diaryTier:" + ((DiaryTierGap) gap).getTier().name();
		}
		if (gap instanceof QuestPointsGap)
		{
			return "questPoints:" + ((QuestPointsGap) gap).getNeed();
		}
		if (gap instanceof KudosGap)
		{
			return "kudos:" + ((KudosGap) gap).getNeed();
		}
		if (gap instanceof CombatLevelGap)
		{
			return "combat:" + ((CombatLevelGap) gap).getNeed();
		}
		if (gap instanceof GearGap)
		{
			List<String> ids = ((GearGap) gap).getAcceptable().stream().map(OwnedItem::getId).map(String::valueOf).collect(Collectors.toList());
			Collections.sort(ids);
			return "gear:[" + String.join(",", ids) + "]";
		}
		if (gap instanceof PrerequisiteGap)
		{
			return "prereq:" + ((PrerequisiteGap) gap).getGoalId();
		}
		throw new IllegalStateException("Unknown gap type: " + gap.getClass());
	}
}
