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
	/** {@code ranked} minus {@code picked} minus {@code later} - unlike {@link dev.reece.nta.engine.SuggestSelector#rest}, a "later" goal never appears here (spec ruling 27); see {@link #later}. */
	List<RankedGoal> rest;
	/** The account's estimated progression stage (1..4, spec ruling 27), from {@link dev.reece.nta.engine.StageEstimator}. */
	int accountStage;
	/** Every ranked goal with {@link RankedGoal#isLater()} true, in ranked order - a rendering convenience for a collapsed "Later" section. */
	List<RankedGoal> later;
	Map<String, String> whys;
	/** A milestone/slayer-target/boss goal's curated KB {@code reason} text, keyed by goal id. Absent for goals with none (quests, diaries, or a milestone with no reason text) - the panel must never read the {@link dev.reece.nta.kb.KnowledgeBase} itself. */
	Map<String, String> reasons;
	PrefsView prefs;
	/** The player's active focus goal drill-down (ticket E, spec ruling 26), or {@code null} when no goal is focused or the focused goal is no longer present (e.g. completed) among {@code statuses}. */
	FocusDetail focus;

	public Advice(
		Snapshot snapshot,
		List<GoalStatus> statuses,
		Map<DiaryTier, DiaryTierProgress> diaryProgress,
		Instant computedAt,
		List<RankedGoal> ranked,
		List<RankedGoal> picked,
		List<RankedGoal> rest,
		int accountStage,
		List<RankedGoal> later,
		Map<String, String> whys,
		Map<String, String> reasons,
		PrefsView prefs,
		FocusDetail focus)
	{
		this.snapshot = snapshot;
		this.statuses = List.copyOf(statuses);
		this.diaryProgress = Map.copyOf(diaryProgress);
		this.computedAt = computedAt;
		this.ranked = List.copyOf(ranked);
		this.picked = List.copyOf(picked);
		this.rest = List.copyOf(rest);
		this.accountStage = accountStage;
		this.later = List.copyOf(later);
		this.whys = Map.copyOf(whys);
		this.reasons = Map.copyOf(reasons);
		this.prefs = prefs;
		this.focus = focus;
	}
}
