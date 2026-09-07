package dev.reece.nta.engine;

import dev.reece.nta.engine.model.CombatLevelGap;
import dev.reece.nta.engine.model.DiaryTaskGap;
import dev.reece.nta.engine.model.DiaryTierGap;
import dev.reece.nta.engine.model.Gap;
import dev.reece.nta.engine.model.GearGap;
import dev.reece.nta.engine.model.GoalStatus;
import dev.reece.nta.engine.model.ItemGap;
import dev.reece.nta.engine.model.KudosGap;
import dev.reece.nta.engine.model.QuestPointsGap;
import dev.reece.nta.engine.model.QuestPrereqGap;
import dev.reece.nta.engine.model.SkillLevelGap;
import dev.reece.nta.kb.OwnedItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A stable, order-independent digest over a {@link GoalStatus}'s gap kinds and keys (not its
 * counts or estimates), used by {@link PrefsResolver} to detect when a snoozed goal's
 * requirements have changed (ticket D6: a changed gap ends the snooze early). Pure: no
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
		throw new IllegalStateException("Unknown gap type: " + gap.getClass());
	}
}
