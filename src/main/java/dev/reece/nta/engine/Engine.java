package dev.reece.nta.engine;

import dev.reece.nta.engine.model.Advice;
import dev.reece.nta.engine.model.DiaryTaskGap;
import dev.reece.nta.engine.model.FocusDetail;
import dev.reece.nta.engine.model.Gap;
import dev.reece.nta.engine.model.Goal;
import dev.reece.nta.engine.model.GoalCategory;
import dev.reece.nta.engine.model.GoalStatus;
import dev.reece.nta.engine.model.NextStep;
import dev.reece.nta.engine.model.NextStepType;
import dev.reece.nta.engine.model.PrefsView;
import dev.reece.nta.engine.model.RankedGoal;
import dev.reece.nta.engine.model.Route;
import dev.reece.nta.engine.model.Shortfall;
import dev.reece.nta.engine.model.SkillLevelGap;
import dev.reece.nta.engine.model.SkillPlan;
import dev.reece.nta.kb.KnowledgeBase;
import dev.reece.nta.kb.MilestoneEntry;
import dev.reece.nta.snapshot.DiaryTier;
import dev.reece.nta.snapshot.SkillState;
import dev.reece.nta.snapshot.Snapshot;
import dev.reece.nta.store.AccountData;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Value;
import net.runelite.api.Experience;
import net.runelite.api.Skill;

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
		return run(snapshot, kb, data, now, null);
	}

	/** As {@link #run(Snapshot, KnowledgeBase, AccountData, Instant)}, also reporting which of {@code previous}'s goals were completed since (RL-011 AC5, spec ruling 31); {@code previous} may be {@code null}. */
	public Advice run(Snapshot snapshot, KnowledgeBase kb, AccountData data, Instant now, Advice previous)
	{
		return run(snapshot, kb, data, now, previous, Map.of());
	}

	/** As above, also carrying the plugin's observed {@code xpPerHour} per skill through to {@link Advice#getXpPerHour()} (RL-012, spec ruling 34). */
	public Advice run(Snapshot snapshot, KnowledgeBase kb, AccountData data, Instant now, Advice previous, Map<Skill, Long> xpPerHour)
	{
		Map<DiaryTier, DiaryTierProgress> diaryProgress = DiaryProgress.compute(snapshot, kb);
		List<GoalStatus> base = gapEngine.evaluate(snapshot, kb, diaryProgress, data.getOwnedManually());
		PrefsView basePrefs = prefsResolver.resolve(data, base, now);
		int accountStage = StageEstimator.estimate(snapshot, kb, data.getOwnedManually());
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

		// RL-006: the skills that have a ranked skill target, so "speeds up Mining" can point at it.
		Set<Skill> targetSkills = ranked.stream()
			.filter(r -> r.getStatus().getGoal().getCategory() == GoalCategory.SKILL_TARGET)
			.map(r -> ((SkillLevelGap) r.getStatus().getGaps().get(0)).getSkill())
			.collect(Collectors.toSet());

		Map<String, String> whys = new LinkedHashMap<>();
		Map<String, List<String>> explanations = new LinkedHashMap<>();
		Map<String, String> reasons = new LinkedHashMap<>();
		for (RankedGoal r : ranked)
		{
			String goalId = r.getStatus().getGoal().getId();
			whys.put(goalId, whyBuilder.why(r, kb, snapshot, targetSkills));
			explanations.put(goalId, whyBuilder.explain(r, kb, snapshot, accountStage));

			MilestoneEntry entry = kb.milestoneById(goalId);
			if (entry != null && entry.getReason() != null && !entry.getReason().isEmpty())
			{
				reasons.put(goalId, entry.getReason());
			}
		}

		FocusDetail focus = computeFocus(prefs, statuses, snapshot, kb);

		Map<String, String> ownedManuallyNames = new LinkedHashMap<>();
		for (String goalId : data.getOwnedManually())
		{
			MilestoneEntry entry = kb.milestoneById(goalId);
			if (entry != null)
			{
				ownedManuallyNames.put(goalId, entry.getName());
			}
		}

		return new Advice(snapshot, statuses, diaryProgress, now, ranked, picked, rest, accountStage, later, whys, explanations, reasons,
			ownedManuallyNames, prefs, focus, completedSince(previous, statuses, snapshot, data.getOwnedManually()), xpPerHour);
	}

	/**
	 * Spec ruling 31: a goal {@code previous} listed that {@code current} no longer does counts as
	 * completed unless it was marked owned by hand, or it is a skill target whose level the player
	 * has not reached (a target also disappears when its parent is hidden or the stage moves on).
	 */
	static List<Goal> completedSince(Advice previous, List<GoalStatus> current, Snapshot snapshot, Set<String> ownedManually)
	{
		if (previous == null)
		{
			return List.of();
		}
		Set<String> currentIds = current.stream().map(s -> s.getGoal().getId()).collect(Collectors.toSet());
		List<Goal> completed = new ArrayList<>();
		for (GoalStatus old : previous.getStatuses())
		{
			Goal goal = old.getGoal();
			if (currentIds.contains(goal.getId()) || ownedManually.contains(goal.getId()))
			{
				continue;
			}
			if (goal.getCategory() == GoalCategory.SKILL_TARGET)
			{
				SkillLevelGap gap = (SkillLevelGap) old.getGaps().get(0);
				SkillState state = snapshot.getSkills().get(gap.getSkill());
				if (state == null || state.getLevel() < gap.getNeed())
				{
					continue;
				}
			}
			completed.add(goal);
		}
		return completed;
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

		List<SkillPlan> skillPlans = computeSkillPlans(status, next, snapshot, kb);
		SkillPlan nextSkillPlan = next.getType() == NextStepType.SKILL
			? skillPlans.stream().filter(p -> p.getSkill() == next.getSkillGap().getSkill()).findFirst().orElse(null)
			: null;

		return new FocusDetail(status, next, route, shortfall, fromLevel, toLevel, skillPlans, nextSkillPlan);
	}

	/**
	 * Task 56: one {@link SkillPlan} per distinct skill of the focused goal's {@link SkillLevelGap}s
	 * - top-level, inside a {@link DiaryTaskGap}, and recommended; a skill appearing more than once
	 * keeps its highest target level. The gap NextStepPicker already picked (if any) reuses its
	 * already-computed {@link Route}/{@link Shortfall} rather than recomputing them.
	 */
	private List<SkillPlan> computeSkillPlans(GoalStatus status, NextStep next, Snapshot snapshot, KnowledgeBase kb)
	{
		List<FlatSkillGap> flat = new ArrayList<>();
		collectSkillGaps(status.getGaps(), null, flat);

		Map<Skill, FlatSkillGap> bySkill = new LinkedHashMap<>();
		for (FlatSkillGap f : flat)
		{
			FlatSkillGap existing = bySkill.get(f.gap.getSkill());
			if (existing == null || f.gap.getNeed() > existing.gap.getNeed())
			{
				bySkill.put(f.gap.getSkill(), f);
			}
		}

		Map<Integer, Integer> bankAll = NextStepPicker.bankAll(snapshot);
		List<SkillPlan> plans = new ArrayList<>();
		for (FlatSkillGap f : bySkill.values())
		{
			SkillLevelGap gap = f.gap;
			long fromXp = currentXp(snapshot, gap.getSkill());
			long toXp = Experience.getXpForLevel(gap.getNeed());
			Route route = next.getType() == NextStepType.SKILL && next.getSkillGap() == gap
				? next.getRoute()
				: RoutePlanner.route(gap.getSkill(), fromXp, toXp, bankAll, kb);
			boolean covered = route.getUncoveredXp() == 0;
			Shortfall shortfall = covered ? null : ShortfallResolver.resolve(gap.getSkill(), route, kb, snapshot);
			SkillPlan plan = new SkillPlan(gap.getSkill(), gap.getHave(), gap.getNeed(), fromXp, toXp, gap.isRecommended(), route, shortfall, covered,
				f.source);
			plans.add(GroupStorageShares.apply(plan, snapshot.getGroupStorage()));
		}
		plans.sort(Comparator.comparingLong(p -> p.getToXp() - p.getFromXp()));
		return plans;
	}

	/** Recursively finds every {@link SkillLevelGap}, tagging each with where it came from (spec ruling 30). */
	private static void collectSkillGaps(List<Gap> gaps, Integer diaryTaskOrdinal, List<FlatSkillGap> out)
	{
		for (Gap gap : gaps)
		{
			if (gap instanceof SkillLevelGap)
			{
				SkillLevelGap skillGap = (SkillLevelGap) gap;
				String source = diaryTaskOrdinal != null
					? "diary task " + diaryTaskOrdinal
					: skillGap.isRecommended() ? "recommended" : "quest";
				out.add(new FlatSkillGap(skillGap, source));
			}
			else if (gap instanceof DiaryTaskGap)
			{
				DiaryTaskGap taskGap = (DiaryTaskGap) gap;
				collectSkillGaps(taskGap.getGaps(), taskGap.getOrdinal(), out);
			}
		}
	}

	private static long currentXp(Snapshot snapshot, Skill skill)
	{
		SkillState state = snapshot.getSkills().get(skill);
		return state == null ? 0 : state.getXp();
	}

	private static final class FlatSkillGap
	{
		final SkillLevelGap gap;
		final String source;

		FlatSkillGap(SkillLevelGap gap, String source)
		{
			this.gap = gap;
			this.source = source;
		}
	}
}
