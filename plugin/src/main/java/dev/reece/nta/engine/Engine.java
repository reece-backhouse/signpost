package dev.reece.nta.engine;

import dev.reece.nta.engine.model.Advice;
import dev.reece.nta.engine.model.FocusDetail;
import dev.reece.nta.engine.model.GoalStatus;
import dev.reece.nta.engine.model.NextStep;
import dev.reece.nta.engine.model.NextStepType;
import dev.reece.nta.engine.model.PrefsView;
import dev.reece.nta.engine.model.RankedGoal;
import dev.reece.nta.engine.model.Route;
import dev.reece.nta.engine.model.Shortfall;
import dev.reece.nta.engine.model.SkillLevelGap;
import dev.reece.nta.kb.KnowledgeBase;
import dev.reece.nta.kb.MilestoneEntry;
import dev.reece.nta.snapshot.DiaryTier;
import dev.reece.nta.snapshot.Snapshot;
import dev.reece.nta.store.AccountData;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Value;

/**
 * Pure entry point wiring {@link GapEngine}, {@link DiaryProgress}, {@link PrefsResolver},
 * {@link SkillTargetSynthesiser}, {@link Ranker}, {@link SuggestSelector}, and {@link WhyBuilder} together: one {@link #run} call
 * turns a {@link Snapshot} + {@link KnowledgeBase} + {@link AccountData} into a complete
 * {@link Advice}. No {@link net.runelite.api.Client}, no I/O (global constraint: engine code is
 * pure).
 */
@Value
public class Engine
{
	GapEngine gapEngine;
	BoostTable boostTable;
	PrefsResolver prefsResolver;
	Ranker ranker;
	SuggestSelector suggestSelector;
	WhyBuilder whyBuilder;
	NextStepPicker nextStepPicker;

	public Engine(BoostTable boostTable)
	{
		this.boostTable = boostTable;
		this.gapEngine = new GapEngine(boostTable);
		this.prefsResolver = new PrefsResolver();
		this.ranker = new Ranker();
		this.suggestSelector = new SuggestSelector();
		this.whyBuilder = new WhyBuilder();
		this.nextStepPicker = new NextStepPicker();
	}

	public Advice run(Snapshot snapshot, KnowledgeBase kb, AccountData data, Instant now)
	{
		Map<DiaryTier, DiaryTierProgress> diaryProgress = DiaryProgress.compute(snapshot, kb);
		List<GoalStatus> base = gapEngine.evaluate(snapshot, kb, diaryProgress);
		PrefsView basePrefs = prefsResolver.resolve(data, base, now);
		int accountStage = StageEstimator.estimate(snapshot, kb);
		// Spec ruling 28: skill targets come from the visible, stage-appropriate goals' skill gaps,
		// then join the statuses so they rank, pin, snooze, and focus like any other goal.
		List<GoalStatus> targets = SkillTargetSynthesiser.synthesise(base, basePrefs.getHidden(), accountStage, snapshot, kb);
		List<GoalStatus> statuses = new ArrayList<>(base);
		statuses.addAll(targets);
		PrefsView prefs = targets.isEmpty() ? basePrefs : prefsResolver.resolve(data, statuses, now);
		List<RankedGoal> ranked = ranker.rank(statuses, prefs.getHidden(), prefs.getPins(), accountStage);
		List<RankedGoal> picked = suggestSelector.pick3(ranked, kb);
		List<RankedGoal> restAll = suggestSelector.rest(ranked, picked);
		List<RankedGoal> later = ranked.stream().filter(RankedGoal::isLater).collect(Collectors.toList());
		List<RankedGoal> rest = restAll.stream().filter(r -> !r.isLater()).collect(Collectors.toList());

		Map<String, String> whys = new LinkedHashMap<>();
		Map<String, List<String>> explanations = new LinkedHashMap<>();
		Map<String, String> reasons = new LinkedHashMap<>();
		for (RankedGoal r : ranked)
		{
			String goalId = r.getStatus().getGoal().getId();
			whys.put(goalId, whyBuilder.why(r, kb, snapshot));
			explanations.put(goalId, whyBuilder.explain(r, kb, snapshot, accountStage));

			MilestoneEntry entry = kb.milestoneById(goalId);
			if (entry != null && entry.getReason() != null && !entry.getReason().isEmpty())
			{
				reasons.put(goalId, entry.getReason());
			}
		}

		FocusDetail focus = computeFocus(prefs, statuses, snapshot, kb);

		return new Advice(snapshot, statuses, diaryProgress, now, ranked, picked, rest, accountStage, later, whys, explanations, reasons, prefs, focus);
	}

	/** Thin overload for callers with no account data (e.g. existing tests): behaves as {@link #run} with an empty {@link AccountData} and the current time. */
	public Advice run(Snapshot snapshot, KnowledgeBase kb)
	{
		return run(snapshot, kb, AccountData.empty(), Instant.now());
	}

	/**
	 * Ticket E: computes the focus goal's full drill-down when {@code prefs.focusGoalId} matches a
	 * goal in {@code statuses} (ready or not - not filtered by hidden/snoozed/ignored, so a focused
	 * goal stays visible in Focus mode even if hidden from Suggest mode); {@code null} when no goal
	 * is focused or the focused goal is no longer present (e.g. completed, so
	 * {@link GapEngine#evaluate} no longer emits it). Reuses the same {@link NextStep}/{@link Route}/
	 * {@link Shortfall} objects {@link NextStepPicker} and {@link ShortfallResolver} compute - never
	 * a second computation.
	 */
	private FocusDetail computeFocus(PrefsView prefs, List<GoalStatus> statuses, Snapshot snapshot, KnowledgeBase kb)
	{
		String focusGoalId = prefs.getFocusGoalId();
		if (focusGoalId == null)
		{
			return null;
		}

		GoalStatus status = null;
		for (GoalStatus candidate : statuses)
		{
			if (candidate.getGoal().getId().equals(focusGoalId))
			{
				status = candidate;
				break;
			}
		}
		if (status == null)
		{
			return null;
		}

		// A skill target's route was computed once by SkillTargetSynthesiser: reuse it (spec ruling 28).
		NextStep next = status.getBankRoute() != null
			? NextStep.skill((SkillLevelGap) status.getGaps().get(0), status.getBankRoute())
			: nextStepPicker.next(status, snapshot, kb);
		Route route = next.getType() == NextStepType.SKILL ? next.getRoute() : null;
		Shortfall shortfall = route != null && route.getUncoveredXp() > 0
			? ShortfallResolver.resolve(next.getSkillGap().getSkill(), route, kb, snapshot)
			: null;
		int fromLevel = next.getType() == NextStepType.SKILL ? next.getSkillGap().getHave() : 0;
		int toLevel = next.getType() == NextStepType.SKILL ? next.getSkillGap().getNeed() : 0;
		return new FocusDetail(status, next, route, shortfall, fromLevel, toLevel);
	}
}
