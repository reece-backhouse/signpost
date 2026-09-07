package dev.reece.nta.engine.model;

import dev.reece.nta.engine.DiaryTierProgress;
import dev.reece.nta.snapshot.DiaryTier;
import dev.reece.nta.snapshot.Snapshot;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.Value;

/**
 * One completed run of the engine over a {@link Snapshot}: every unfinished-goal status plus
 * per-tier diary progress, timestamped. Constructed only by {@link dev.reece.nta.engine.Engine};
 * {@code statuses} and {@code diaryProgress} are unmodifiable.
 */
@Value
public class Advice
{
	Snapshot snapshot;
	List<GoalStatus> statuses;
	Map<DiaryTier, DiaryTierProgress> diaryProgress;
	Instant computedAt;

	public Advice(Snapshot snapshot, List<GoalStatus> statuses, Map<DiaryTier, DiaryTierProgress> diaryProgress, Instant computedAt)
	{
		this.snapshot = snapshot;
		this.statuses = List.copyOf(statuses);
		this.diaryProgress = Map.copyOf(diaryProgress);
		this.computedAt = computedAt;
	}
}
