package com.signpost;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.Map;
import net.runelite.api.Skill;

/**
 * An in-memory ring of (time, xp) samples per skill fed from {@code StatChanged},
 * bounded to the last {@link #WINDOW}; nothing is persisted. Leading samples with no gain (the
 * login baseline, boost drains while idle) are skipped: the window starts at the first sample
 * that gained xp, so it measures training time only. A skill's rate is then ready once that window
 * spans at least {@link #MIN_SPAN} and holds at least two samples with xp gained between the
 * first and the last; it is that plain first-to-last slope in xp per hour. Written on the client
 * thread, read on the engine executor - hence synchronized.
 */
public class XpRateTracker
{
	static final Duration WINDOW = Duration.ofMinutes(30);
	static final Duration MIN_SPAN = Duration.ofMinutes(5);
	// A hard cap on top of the time window so a per-tick skill can't grow the deque unbounded
	private static final int MAX_SAMPLES = 1024;

	private final Map<Skill, Deque<long[]>> samples = new EnumMap<>(Skill.class);

	/** Records one sample; returns true when this sample is what made {@code skill}'s rate ready. */
	public synchronized boolean record(Skill skill, long xp, Instant now)
	{
		Deque<long[]> ring = samples.computeIfAbsent(skill, s -> new ArrayDeque<>());
		trim(ring, now);
		boolean wasReady = rate(ring) != null;
		ring.addLast(new long[]{now.toEpochMilli(), xp});
		while (ring.size() > MAX_SAMPLES)
		{
			ring.pollFirst();
		}
		return !wasReady && rate(ring) != null;
	}

	/** Every skill whose rate is ready as of {@code now}, in xp per hour. */
	public synchronized Map<Skill, Long> rates(Instant now)
	{
		Map<Skill, Long> out = new EnumMap<>(Skill.class);
		for (Map.Entry<Skill, Deque<long[]>> entry : samples.entrySet())
		{
			Deque<long[]> ring = entry.getValue();
			trim(ring, now);
			Long rate = rate(ring);
			if (rate != null)
			{
				out.put(entry.getKey(), rate);
			}
		}
		return out;
	}

	public synchronized void clear()
	{
		samples.clear();
	}

	private static void trim(Deque<long[]> ring, Instant now)
	{
		long cutoff = now.minus(WINDOW).toEpochMilli();
		while (!ring.isEmpty() && ring.peekFirst()[0] < cutoff)
		{
			ring.pollFirst();
		}
	}

	private static Long rate(Deque<long[]> ring)
	{
		// skip idle lead-in: the window starts at the first sample that gained xp over the one before,
		// so the login baseline and idle boost drains never count as time (one xp drop of gain is lost).
		// A mid-session AFK gap still dilutes the slope; split on gaps if it matters
		long[] first = null;
		long[] previous = null;
		for (long[] sample : ring)
		{
			if (previous != null && sample[1] > previous[1])
			{
				first = sample;
				break;
			}
			previous = sample;
		}
		long[] last = ring.peekLast();
		if (first == null || first == last)
		{
			return null;
		}
		long spanMs = last[0] - first[0];
		if (spanMs < MIN_SPAN.toMillis() || last[1] <= first[1])
		{
			return null;
		}
		return (last[1] - first[1]) * 3_600_000L / spanMs;
	}
}
