package com.signpost.engine.model;

import com.signpost.engine.DiaryTierProgress;
import com.signpost.snapshot.DiaryTier;
import com.signpost.snapshot.Snapshot;
import com.signpost.snapshot.SlayerState;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.Value;
import net.runelite.api.Skill;

/**
 * One completed run of the engine over a {@link Snapshot}: every unfinished-goal status, per-tier
 * diary progress, the ranked/picked/rest suggestion lists with their "why" text, and the resolved
 * preferences view, timestamped. Constructed only by {@link com.signpost.engine.Engine}; every
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
	/** {@code ranked} minus {@code picked} minus {@code later} - unlike {@link com.signpost.engine.SuggestSelector#rest}, a "later" goal never appears here; see {@link #later}. */
	List<RankedGoal> rest;
	/** The account's estimated progression stage (1..4), from {@link com.signpost.engine.StageEstimator}. */
	int accountStage;
	/** Every ranked goal with {@link RankedGoal#isLater()} true, in ranked order - a rendering convenience for a collapsed "Later" section. */
	List<RankedGoal> later;
	Map<String, String> whys;
	/** The multi-line "Why?" behind every ranked (and later) goal, keyed by goal id - {@link com.signpost.engine.WhyBuilder#explain}. */
	Map<String, List<String>> explanations;
	/** A milestone/slayer-target/boss goal's curated KB {@code reason} text, keyed by goal id. Absent for goals with none (quests, diaries, or a milestone with no reason text) - the panel must never read the {@link com.signpost.kb.KnowledgeBase} itself. */
	Map<String, String> reasons;
	/**
	 * The curated KB name of every goal in {@code prefs.ownedManually}, keyed by goal
	 * id - since {@link com.signpost.engine.GapEngine} never emits a manually-owned goal's status,
	 * it can't appear in {@code ranked}, so the panel's "Owned (manual)" section needs the name from
	 * here instead. An ownedManually id absent from the KB (stale hand-edited file) is simply omitted.
	 */
	Map<String, String> ownedManuallyNames;
	PrefsView prefs;
	/** The player's active focus goal drill-down, or {@code null} when no goal is focused or the focused goal is no longer present (e.g. completed) among {@code statuses}. */
	FocusDetail focus;
	/**
	 * Goals the previous {@link Advice} listed that this run no
	 * longer does because they were completed - not marked owned by hand, and for a skill target
	 * only when its level was actually reached. Empty when there was no previous advice.
	 */
	List<Goal> completedSinceLast;
	/**
	 * Each skill's observed training rate in xp per hour, measured by the
	 * plugin from {@code StatChanged} and passed in as data; a skill is absent until its rate is
	 * ready. The panels turn it into "about 45 min at 38k/h" via {@link com.signpost.engine.Eta}.
	 */
	Map<Skill, Long> xpPerHour;

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
		Map<String, List<String>> explanations,
		Map<String, String> reasons,
		Map<String, String> ownedManuallyNames,
		PrefsView prefs,
		FocusDetail focus,
		List<Goal> completedSinceLast)
	{
		this(snapshot, statuses, diaryProgress, computedAt, ranked, picked, rest, accountStage, later, whys, explanations, reasons,
			ownedManuallyNames, prefs, focus, completedSinceLast, Map.of());
	}

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
		Map<String, List<String>> explanations,
		Map<String, String> reasons,
		Map<String, String> ownedManuallyNames,
		PrefsView prefs,
		FocusDetail focus,
		List<Goal> completedSinceLast,
		Map<Skill, Long> xpPerHour)
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
		this.explanations = Map.copyOf(explanations);
		this.reasons = Map.copyOf(reasons);
		this.ownedManuallyNames = Map.copyOf(ownedManuallyNames);
		this.prefs = prefs;
		this.focus = focus;
		this.completedSinceLast = List.copyOf(completedSinceLast);
		this.xpPerHour = Map.copyOf(xpPerHour);
	}

	/** Task counts change per kill; update their display without re-ranking unchanged goals. */
	public Advice withSlayer(SlayerState slayer)
	{
		return new Advice(snapshot.toBuilder().slayer(slayer).build(), statuses, diaryProgress, computedAt,
			ranked, picked, rest, accountStage, later, whys, explanations, reasons, ownedManuallyNames,
			prefs, focus, List.of(), xpPerHour);
	}
}
