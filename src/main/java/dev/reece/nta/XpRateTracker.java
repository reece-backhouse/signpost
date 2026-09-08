package dev.reece.nta;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.Map;
import net.runelite.api.Skill;

/**
 * RL-012 AC1/AC2: an in-memory ring of (time, xp) samples per skill fed from {@code StatChanged},
 * bounded to the last {@link #WINDOW}; nothing is persisted. A skill's rate is ready once the
 * window spans at least {@link #MIN_SPAN} and holds at least two samples with xp gained between
 * the first and the last; it is that plain first-to-last slope in xp per hour. Written on the client thread,
 * read on the engine executor - hence synchronized.
 */
public class XpRateTracker
{
	static final Duration WINDOW = Duration.ofMinutes(30);
	static final Duration MIN_SPAN = Duration.ofMinutes(5);
	// ponytail: a hard cap on top of the time window so a per-tick skill can't grow the deque unbounded
	private static final int MAX_SAMPLES = 1024;

	private final Map<Skill, Deque<long[]>> samples = new EnumMap<>(Skill.class);

	public synchronized void record(Skill skill, long xp, Instant now)
	{
		Deque<long[]> ring = samples.computeIfAbsent(skill, s -> new ArrayDeque<>());
		ring.addLast(new long[]{now.toEpochMilli(), xp});
		trim(ring, now);
		while (ring.size() > MAX_SAMPLES)
		{
			ring.pollFirst();
		}
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
		if (ring.size() < 2)
		{
			return null;
		}
		long[] first = ring.peekFirst();
		long[] last = ring.peekLast();
		long spanMs = last[0] - first[0];
		if (spanMs < MIN_SPAN.toMillis() || last[1] <= first[1])
		{
			return null;
		}
		return (last[1] - first[1]) * 3_600_000L / spanMs;
	}
}
