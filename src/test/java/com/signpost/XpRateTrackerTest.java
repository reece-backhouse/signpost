package com.signpost;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import net.runelite.api.Skill;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The per-skill xp sample ring and when its rate counts as ready. */
class XpRateTrackerTest
{
	private final Instant t0 = Instant.parse("2026-09-08T12:00:00Z");
	private final XpRateTracker tracker = new XpRateTracker();

	@Test
	void rateNeedsFiveMinutesOfTrainingAndTwoSamplesWithXpGained()
	{
		// the login baseline is not training time: the window starts at the first gain (t=3)
		tracker.record(Skill.HERBLORE, 100_000, t0);
		tracker.record(Skill.HERBLORE, 103_000, t0.plus(Duration.ofMinutes(3)));
		tracker.record(Skill.HERBLORE, 106_000, t0.plus(Duration.ofMinutes(6)));
		assertFalse(tracker.rates(t0.plus(Duration.ofMinutes(6))).containsKey(Skill.HERBLORE), "3 min of training is not ready");

		tracker.record(Skill.MINING, 500, t0);
		tracker.record(Skill.MINING, 500, t0.plus(Duration.ofMinutes(6)));
		assertFalse(tracker.rates(t0.plus(Duration.ofMinutes(6))).containsKey(Skill.MINING), "no xp gained: not ready");

		tracker.record(Skill.HERBLORE, 108_000, t0.plus(Duration.ofMinutes(8)));
		Map<Skill, Long> rates = tracker.rates(t0.plus(Duration.ofMinutes(8)));
		assertEquals(60_000L, rates.get(Skill.HERBLORE), "5,000 xp over the 5 training minutes is 60k/h");
	}

	@Test
	void samplesOlderThanThirtyMinutesFallOutOfTheWindow()
	{
		tracker.record(Skill.MINING, 0, t0);
		tracker.record(Skill.MINING, 500, t0.plus(Duration.ofMinutes(5)));
		tracker.record(Skill.MINING, 1_000, t0.plus(Duration.ofMinutes(10)));
		tracker.record(Skill.MINING, 2_000, t0.plus(Duration.ofMinutes(20)));
		assertEquals(6_000L, tracker.rates(t0.plus(Duration.ofMinutes(20))).get(Skill.MINING), "1,500 xp over 15 training minutes");

		// 36 min later only the t=10 and t=20 samples remain, and the older one is now the baseline: no rate
		assertTrue(tracker.rates(t0.plus(Duration.ofMinutes(36))).isEmpty());
		// 55 min later the window is empty
		assertTrue(tracker.rates(t0.plus(Duration.ofMinutes(55))).isEmpty());
	}

	@Test
	void leadingIdleSamplesDoNotDiluteTheRate()
	{
		// login baseline, 20 idle minutes, then 5 minutes of training: the rate is the training span only
		tracker.record(Skill.HERBLORE, 100_000, t0);
		tracker.record(Skill.HERBLORE, 100_000, t0.plus(Duration.ofMinutes(10)));
		assertFalse(tracker.record(Skill.HERBLORE, 101_000, t0.plus(Duration.ofMinutes(20))), "first gain: not ready yet");
		assertFalse(tracker.record(Skill.HERBLORE, 103_000, t0.plus(Duration.ofMinutes(23))), "3 min of training: not ready yet");
		assertTrue(tracker.record(Skill.HERBLORE, 105_000, t0.plus(Duration.ofMinutes(25))), "5 min of training: just became ready");
		assertEquals(48_000L, tracker.rates(t0.plus(Duration.ofMinutes(25))).get(Skill.HERBLORE), "4,000 xp over the 5 training minutes");
		assertFalse(tracker.record(Skill.HERBLORE, 106_000, t0.plus(Duration.ofMinutes(26))), "already ready: no second flip");
	}

	@Test
	void clearForgetsEverySkill()
	{
		tracker.record(Skill.MINING, 0, t0);
		tracker.record(Skill.MINING, 100, t0.plus(Duration.ofMinutes(1)));
		tracker.record(Skill.MINING, 2_000, t0.plus(Duration.ofMinutes(6)));
		assertFalse(tracker.rates(t0.plus(Duration.ofMinutes(6))).isEmpty());
		tracker.clear();
		assertTrue(tracker.rates(t0.plus(Duration.ofMinutes(6))).isEmpty());
	}
}
