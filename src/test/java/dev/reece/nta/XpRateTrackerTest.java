package dev.reece.nta;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import net.runelite.api.Skill;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** RL-012 AC1/AC2: the per-skill xp sample ring and when its rate counts as ready. */
class XpRateTrackerTest
{
	private final Instant t0 = Instant.parse("2026-09-08T12:00:00Z");
	private final XpRateTracker tracker = new XpRateTracker();

	@Test
	void rateNeedsFiveMinutesAndTwoSamplesWithXpGained()
	{
		tracker.record(Skill.HERBLORE, 100_000, t0);
		tracker.record(Skill.HERBLORE, 103_000, t0.plus(Duration.ofMinutes(3)));
		assertFalse(tracker.rates(t0.plus(Duration.ofMinutes(3))).containsKey(Skill.HERBLORE), "3 min is not ready");

		tracker.record(Skill.MINING, 500, t0);
		tracker.record(Skill.MINING, 500, t0.plus(Duration.ofMinutes(6)));
		assertFalse(tracker.rates(t0.plus(Duration.ofMinutes(6))).containsKey(Skill.MINING), "no xp gained: not ready");

		tracker.record(Skill.HERBLORE, 106_000, t0.plus(Duration.ofMinutes(6)));
		Map<Skill, Long> rates = tracker.rates(t0.plus(Duration.ofMinutes(6)));
		assertEquals(60_000L, rates.get(Skill.HERBLORE), "6,000 xp over 6 min is 60k/h");
	}

	@Test
	void samplesOlderThanThirtyMinutesFallOutOfTheWindow()
	{
		tracker.record(Skill.MINING, 0, t0);
		tracker.record(Skill.MINING, 1_000, t0.plus(Duration.ofMinutes(10)));
		tracker.record(Skill.MINING, 2_000, t0.plus(Duration.ofMinutes(20)));
		assertEquals(6_000L, tracker.rates(t0.plus(Duration.ofMinutes(20))).get(Skill.MINING));

		// 35 min later only the two newest samples remain: 1,000 xp over 10 min
		Instant later = t0.plus(Duration.ofMinutes(35));
		assertEquals(6_000L, tracker.rates(later).get(Skill.MINING));
		// 55 min later the window holds one sample: no rate
		assertTrue(tracker.rates(t0.plus(Duration.ofMinutes(55))).isEmpty());
	}

	@Test
	void clearForgetsEverySkill()
	{
		tracker.record(Skill.MINING, 0, t0);
		tracker.record(Skill.MINING, 1_000, t0.plus(Duration.ofMinutes(5)));
		tracker.record(Skill.MINING, 2_000, t0.plus(Duration.ofMinutes(6)));
		assertFalse(tracker.rates(t0.plus(Duration.ofMinutes(6))).isEmpty());
		tracker.clear();
		assertTrue(tracker.rates(t0.plus(Duration.ofMinutes(6))).isEmpty());
	}
}
