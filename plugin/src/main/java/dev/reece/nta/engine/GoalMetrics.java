package dev.reece.nta.engine;

import dev.reece.nta.engine.model.DiaryTaskGap;
import dev.reece.nta.engine.model.Gap;
import dev.reece.nta.engine.model.GearGap;
import dev.reece.nta.engine.model.ItemGap;
import dev.reece.nta.engine.model.SkillLevelGap;
import java.util.List;

/**
 * Shared leaf-counting and XP-summing over a goal's gap tree (ruling 14), used by both
 * {@link Ranker} (the score formula) and {@link WhyBuilder} ("N requirement(s) away") so the two
 * stay consistent.
 */
final class GoalMetrics
{
	private GoalMetrics()
	{
	}

	/**
	 * Every top-level gap counts 1, except: an {@link ItemGap} with an unknown {@code have} (bank
	 * not seen) counts 0 (ruling 14 - it's not a confirmed shortfall, just unknown), and likewise a
	 * {@link GearGap} with {@code bankUnknown} true; a {@link DiaryTaskGap} counts its own
	 * {@link #unmetCount} over its inner gaps (minimum 1).
	 */
	static int unmetCount(List<Gap> gaps)
	{
		int count = 0;
		for (Gap gap : gaps)
		{
			if (gap instanceof DiaryTaskGap)
			{
				count += Math.max(1, unmetCount(((DiaryTaskGap) gap).getGaps()));
			}
			else if (gap instanceof ItemGap && ((ItemGap) gap).getHave() == null)
			{
				continue;
			}
			else if (gap instanceof GearGap && ((GearGap) gap).isBankUnknown())
			{
				continue;
			}
			else
			{
				count += 1;
			}
		}
		return count;
	}

	/** Sum of {@link SkillLevelGap#getXpDelta()} over top-level and nested (inside a {@link DiaryTaskGap}) gaps. */
	static long xpDeltaSum(List<Gap> gaps)
	{
		long sum = 0;
		for (Gap gap : gaps)
		{
			if (gap instanceof SkillLevelGap)
			{
				sum += ((SkillLevelGap) gap).getXpDelta();
			}
			else if (gap instanceof DiaryTaskGap)
			{
				sum += xpDeltaSum(((DiaryTaskGap) gap).getGaps());
			}
		}
		return sum;
	}
}
