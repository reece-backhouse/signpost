package dev.reece.nta.engine;

import dev.reece.nta.engine.model.DiaryTaskGap;
import dev.reece.nta.engine.model.Gap;
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

	/** Every top-level gap counts 1, except a {@link DiaryTaskGap}, which counts its inner gaps (minimum 1). */
	static int unmetCount(List<Gap> gaps)
	{
		int count = 0;
		for (Gap gap : gaps)
		{
			if (gap instanceof DiaryTaskGap)
			{
				count += Math.max(1, ((DiaryTaskGap) gap).getGaps().size());
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
