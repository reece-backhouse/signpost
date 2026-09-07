package dev.reece.nta.engine.model;

import dev.reece.nta.engine.DiaryTierProgress;
import dev.reece.nta.snapshot.DiaryTier;
import dev.reece.nta.snapshot.Snapshot;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.Value;

/**
 * One completed run of the engine over a {@link Snapshot}: every unfinished-goal status, per-tier
 * diary progress, the ranked/picked/rest suggestion lists with their "why" text, and the resolved
 * preferences view, timestamped. Constructed only by {@link dev.reece.nta.engine.Engine}; every
 * collection is unmodifiable.
 */
@Value
public class Advice
{
	Snapshot snapshot;
	List<GoalStatus> statuses;
	Map<DiaryTier, DiaryTierProgress> diaryProgress;
	Instant computedAt;
	List<RankedGoal> ranked;
	List<RankedGoal> picked;
	List<RankedGoal> rest;
	Map<String, String> whys;
	/** A milestone/slayer-target/boss goal's curated KB {@code reason} text, keyed by goal id. Absent for goals with none (quests, diaries, or a milestone with no reason text) - the panel must never read the {@link dev.reece.nta.kb.KnowledgeBase} itself. */
	Map<String, String> reasons;
	PrefsView prefs;

	public Advice(
		Snapshot snapshot,
		List<GoalStatus> statuses,
		Map<DiaryTier, DiaryTierProgress> diaryProgress,
		Instant computedAt,
		List<RankedGoal> ranked,
		List<RankedGoal> picked,
		List<RankedGoal> rest,
		Map<String, String> whys,
		Map<String, String> reasons,
		PrefsView prefs)
	{
		this.snapshot = snapshot;
		this.statuses = List.copyOf(statuses);
		this.diaryProgress = Map.copyOf(diaryProgress);
		this.computedAt = computedAt;
		this.ranked = List.copyOf(ranked);
		this.picked = List.copyOf(picked);
		this.rest = List.copyOf(rest);
		this.whys = Map.copyOf(whys);
		this.reasons = Map.copyOf(reasons);
		this.prefs = prefs;
	}
}
