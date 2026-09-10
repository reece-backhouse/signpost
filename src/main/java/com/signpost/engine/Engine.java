package com.signpost.engine;

import com.signpost.engine.model.Advice;
import com.signpost.engine.model.DiaryTaskGap;
import com.signpost.engine.model.FocusDetail;
import com.signpost.engine.model.Gap;
import com.signpost.engine.model.Goal;
import com.signpost.engine.model.GoalCategory;
import com.signpost.engine.model.GoalStatus;
import com.signpost.engine.model.NextStep;
import com.signpost.engine.model.NextStepType;
import com.signpost.engine.model.PrefsView;
import com.signpost.engine.model.RankedGoal;
import com.signpost.engine.model.Route;
import com.signpost.engine.model.Shortfall;
import com.signpost.engine.model.SkillLevelGap;
import com.signpost.engine.model.SkillPlan;
import com.signpost.kb.KnowledgeBase;
import com.signpost.kb.MilestoneEntry;
import com.signpost.snapshot.DiaryTier;
import com.signpost.snapshot.SkillState;
import com.signpost.snapshot.Snapshot;
import com.signpost.store.AccountData;
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

	/** As {@link #run(Snapshot, KnowledgeBase, AccountData, Instant)}, also reporting which of {@code previous}'s goals were completed since; {@code previous} may be {@code null}. */
	public Advice run(Snapshot snapshot, KnowledgeBase kb, AccountData data, Instant now, Advice previous)
	{
		return run(snapshot, kb, data, now, previous, Map.of());
	}

	/** As above, also carrying the plugin's observed {@code xpPerHour} per skill through to {@link Advice#getXpPerHour()}. */
	public Advice run(Snapshot snapshot, KnowledgeBase kb, AccountData data, Instant now, Advice previous, Map<Skill, Long> xpPerHour)
	{
		return run(snapshot, kb, data, now, previous, xpPerHour, false);
	}

	public Advice run(Snapshot snapshot, KnowledgeBase kb, AccountData data, Instant now, Advice previous,
		Map<Skill, Long> xpPerHour, boolean includeWilderness)
	{
		Map<DiaryTier, DiaryTierProgress> diaryProgress = DiaryProgress.compute(snapshot, kb);
		GearComparison gear = new GearComparison(snapshot, kb, data.getOwnedManually());
		List<GoalStatus> base = gapEngine.evaluate(snapshot, kb, diaryProgress, data.getOwnedManually(), gear);
		base.addAll(GearUpgradeSynthesiser.run(kb.getGearLadders(), snapshot, kb, data.getOwnedManually(), gapEngine, gear));
		PrefsView basePrefs = prefsResolver.resolve(data, base, now);
		int accountStage = StageEstimator.estimate(snapshot, kb, data.getOwnedManually());
		// Skill targets come from the visible, stage-appropriate goals' skill gaps,
		// then join the statuses so they rank, pin, snooze, and focus like any other goal.
		// A target fully covered by ready quests' reward xp is dropped and those quests
		// count as unblocking its parents (ranked with the fan-out bonus).
		SkillTargetSynthesiser.Synthesis synthesis = SkillTargetSynthesiser.run(base, basePrefs.getHidden(), accountStage, snapshot, kb);
		List<GoalStatus> targets = synthesis.getTargets();
		List<GoalStatus> statuses = new ArrayList<>(base);
		statuses.addAll(targets);
		PrefsView prefs = targets.isEmpty() ? basePrefs : prefsResolver.resolve(data, statuses, now);
		List<RankedGoal> ranked = ranker.rank(statuses, prefs.getHidden(), prefs.getPins(), accountStage, synthesis.getQuestUnblocks());
		List<RankedGoal> picked = suggestSelector.pick3(ranked, kb);
		List<RankedGoal> restAll = suggestSelector.rest(ranked, picked);
		List<RankedGoal> later = ranked.stream().filter(RankedGoal::isLater).collect(Collectors.toList());
		List<RankedGoal> rest = restAll.stream().filter(r -> !r.isLater()).collect(Collectors.toList());

		// The skills that have a ranked skill target, so "speeds up Mining" can point at it.
		Set<Skill> targetSkills = ranked.stream()
			.filter(r -> r.getStatus().getGoal().getCategory() == GoalCategory.SKILL_TARGET)
			.flatMap(r -> r.getStatus().getGaps().stream())
			.filter(gap -> gap instanceof SkillLevelGap)
			.map(gap -> ((SkillLevelGap) gap).getSkill())
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
			if (entry != null && r.getStatus().getObjective() == null && entry.getReason() != null && !entry.getReason().isEmpty())
			{
				reasons.put(goalId, entry.getReason());
			}
		}

		FocusDetail focus = computeFocus(prefs, statuses, snapshot, kb, includeWilderness);

		Map<String, String> ownedManuallyNames = new LinkedHashMap<>();
		for (String goalId : data.getOwnedManually())
		{
			MilestoneEntry entry = kb.milestoneById(goalId);
			if (entry != null)
			{
				ownedManuallyNames.put(goalId, entry.getName());
			}
		}
		for (com.signpost.kb.GearLadder ladder : kb.getGearLadders())
		{
			for (com.signpost.kb.GearLadder.Rung rung : ladder.getRungs())
			{
				String id = GearUpgradeSynthesiser.id(ladder, rung);
				if (data.getOwnedManually().contains(id)) ownedManuallyNames.put(id, rung.getName());
			}
		}

		return new Advice(snapshot, statuses, diaryProgress, now, ranked, picked, rest, accountStage, later, whys, explanations, reasons,
			ownedManuallyNames, prefs, focus, completedSince(previous, statuses, snapshot, data.getOwnedManually(), kb, gear), xpPerHour);
	}


	/**
	 * A goal {@code previous} listed that {@code current} no longer does counts as
	 * completed unless it was marked owned by hand, or it is a skill target whose level the player
	 * has not reached (a target also disappears when its parent is hidden or the stage moves on).
	 */
	static List<Goal> completedSince(Advice previous, List<GoalStatus> current, Snapshot snapshot, Set<String> ownedManually,
		KnowledgeBase kb, GearComparison gear)
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
			if (goal.getCategory() == GoalCategory.BOSS && old.getObjective() != null)
			{
				var progress = snapshot.getBossProgress().get(goal.getId());
				if (progress == null || !Boolean.TRUE.equals(progress.getGreenLogged())
					|| !progress.isCombatAchievementsKnown() || !progress.getRemainingTasks().isEmpty())
				{
					continue;
				}
			}
			MilestoneEntry entry = kb.milestoneById(goal.getId());
			if (entry != null && entry.getCategory() == com.signpost.kb.MilestoneCategory.GEAR
				&& entry.getOwnedIf().stream().filter(item -> gear.owns(item.getIds())).count() < entry.getOwnedIfMin())
			{
				continue;
			}
			if (goal.getUpgrade() != null && !gear.owns(goal.getUpgrade().getTarget().getVariants()))
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
	 * Computes the focus goal's full drill-down when {@code prefs.focusGoalId} matches a
	 * goal in {@code statuses} (ready or not - not filtered by hidden/snoozed/ignored, so a focused
	 * goal stays visible in Focus mode even if hidden from Suggest mode); {@code null} when no goal
	 * is focused or the focused goal is no longer present (e.g. completed, so
	 * {@link GapEngine#evaluate} no longer emits it). Reuses the same {@link NextStep}/{@link Route}/
	 * {@link Shortfall} objects {@link NextStepPicker} and {@link ShortfallResolver} compute - never
	 * a second computation.
	 */
	private FocusDetail computeFocus(PrefsView prefs, List<GoalStatus> statuses, Snapshot snapshot, KnowledgeBase kb, boolean includeWilderness)
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

		// A skill target's route was computed once by SkillTargetSynthesiser: reuse it.
		NextStep next = status.getBankRoute() != null
			? NextStep.skill((SkillLevelGap) status.getGaps().get(0), status.getBankRoute())
			: nextStepPicker.next(status, snapshot, kb);
		Route route = next.getType() == NextStepType.SKILL ? next.getRoute() : null;
		Shortfall shortfall = route != null && route.getUncoveredXp() > 0
			? ShortfallResolver.resolve(next.getSkillGap().getSkill(), route, kb, snapshot, includeWilderness)
			: null;
		int fromLevel = next.getType() == NextStepType.SKILL ? next.getSkillGap().getHave() : 0;
		int toLevel = next.getType() == NextStepType.SKILL ? next.getSkillGap().getNeed() : 0;

		List<SkillPlan> skillPlans = computeSkillPlans(status, next, snapshot, kb, includeWilderness);
		SkillPlan nextSkillPlan = next.getType() == NextStepType.SKILL
			? skillPlans.stream().filter(p -> p.getSkill() == next.getSkillGap().getSkill()).findFirst().orElse(null)
			: null;

		NextStep guided = NextStepGuidance.derive(next, shortfall, snapshot, kb);
		return new FocusDetail(status, guided, route, shortfall, fromLevel, toLevel, skillPlans, nextSkillPlan);
	}

	/**
	 * One {@link SkillPlan} per distinct skill of the focused goal's {@link SkillLevelGap}s
	 * - top-level, inside a {@link DiaryTaskGap}, and recommended; a skill appearing more than once
	 * keeps its highest target level. The gap NextStepPicker already picked (if any) reuses its
	 * already-computed {@link Route}/{@link Shortfall} rather than recomputing them.
	 */
	private List<SkillPlan> computeSkillPlans(GoalStatus status, NextStep next, Snapshot snapshot, KnowledgeBase kb, boolean includeWilderness)
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
			Shortfall shortfall = covered ? null : ShortfallResolver.resolve(gap.getSkill(), route, kb, snapshot, includeWilderness);
			SkillPlan plan = new SkillPlan(gap.getSkill(), gap.getHave(), gap.getNeed(), fromXp, toXp, gap.isRecommended(), route, shortfall, covered,
				f.source);
			plans.add(GroupStorageShares.apply(plan, snapshot.getGroupStorage()));
		}
		plans.sort(Comparator.comparingLong(p -> p.getToXp() - p.getFromXp()));
		return plans;
	}

	/** Recursively finds every {@link SkillLevelGap}, tagging each with where it came from. */
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
