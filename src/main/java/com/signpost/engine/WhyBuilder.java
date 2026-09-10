package com.signpost.engine;

import com.signpost.engine.model.GoalObjective;
import com.signpost.engine.model.RewardTarget;
import com.signpost.kb.RewardValue;
import com.signpost.snapshot.CombatAchievementTask;
import com.signpost.engine.model.PrayerUnlockGap;
import com.signpost.engine.model.SlayerPointsGap;
import com.signpost.engine.model.SlayerUnlockGap;
import com.signpost.engine.model.CombatLevelGap;
import com.signpost.engine.model.DiaryTaskGap;
import com.signpost.engine.model.DiaryTierGap;
import com.signpost.engine.model.Gap;
import com.signpost.engine.model.GearGap;
import com.signpost.engine.model.Goal;
import com.signpost.engine.model.GoalCategory;
import com.signpost.engine.model.GoalRef;
import com.signpost.engine.model.GoalStatus;
import com.signpost.engine.model.ItemGap;
import com.signpost.engine.model.KudosGap;
import com.signpost.engine.model.PrerequisiteGap;
import com.signpost.engine.model.Met;
import com.signpost.engine.model.QuestPointsGap;
import com.signpost.engine.model.QuestPrereqGap;
import com.signpost.engine.model.QuestXp;
import com.signpost.engine.model.RankedGoal;
import com.signpost.engine.model.Route;
import com.signpost.engine.model.SkillLevelGap;
import com.signpost.kb.KnowledgeBase;
import com.signpost.kb.MilestoneCategory;
import com.signpost.kb.MilestoneEntry;
import com.signpost.kb.OwnedItem;
import com.signpost.snapshot.Snapshot;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.runelite.api.Experience;
import net.runelite.api.Skill;

/**
 * Deterministic, template-only "why" text for a {@link RankedGoal}: one to three
 * clauses joined with "; ", at most 140 characters, built only from the ranking inputs and the
 * knowledge base's curated {@code unlocks} field - never free text, and never {@code reason}
 * (shown separately by the UI). {@link #explain} is the longer "Why?":
 * up to six lines, with wrapping quest lists preserving three full names. Pure: no
 * {@link net.runelite.api.Client}, no I/O.
 */
public final class WhyBuilder
{
	private static final int MAX_LEN = 140;
	private static final int MAX_CLAUSES = 3;
	private static final int MAX_UNLOCKS = 2;
	private static final int LINE_LEN = 90;
	private static final int MAX_LINES = 6;
	private static final int MAX_PARENTS = 3;
	private static final int MAX_GEAR_NAMES = 4;
	private static final int MAX_STATS = 5;
	private static final int MAX_MISSING = 4;
	/** "Unblocks:" names this many dependents, then "+N more". */
	private static final int MAX_UNBLOCKS = 3;
	private static final String ELLIPSIS = "\u2026";
	private static final String[] TASK_TIERS = {"Easy", "Medium", "Hard", "Elite", "Master", "Grandmaster"};

	private static String upgradeClause(Goal goal)
	{
		Goal.Upgrade upgrade = goal.getUpgrade();
		return upgrade.getReplaces() == null ? "next " + upgrade.getStyle() + " " + upgrade.getSlot() + " upgrade"
			: "replaces " + upgrade.getReplaces();
	}

	public String why(RankedGoal r, KnowledgeBase kb, Snapshot s)
	{
		return why(r, kb, s, Set.of());
	}

	/**
	 * As {@link #why(RankedGoal, KnowledgeBase, Snapshot)}; {@code targetSkills} are the skills with
	 * a ranked {@link GoalCategory#SKILL_TARGET}, so a method-linked untradeable
	 * ({@code speedsUp}) can say "speeds up Mining (your next Mining target)".
	 */
	public String why(RankedGoal r, KnowledgeBase kb, Snapshot s, Set<Skill> targetSkills)
	{
		GoalStatus status = r.getStatus();
		Goal goal = status.getGoal();
		List<Gap> gaps = status.getGaps();
		// A later goal is never "Ready now", even with empty gaps - Ranker already
		// keeps it out of the ready tier for the same reason.
		boolean readyNow = !r.isLater() && status.isReady() && !status.isBankUnknown();
		if (status.getObjective() != null) return objectiveWhy(r);

		List<String> clauses = new ArrayList<>();
		clauses.add(r.isLater() ? "later: stage " + goal.getStage() : (readyNow ? "Ready now" : awayClause(gaps)));
		if (goal.getUpgrade() != null)
		{
			clauses.add(upgradeClause(goal));
		}

		if (clauses.size() < MAX_CLAUSES && status.isBankUnknown())
		{
			clauses.add("bank unknown");
		}

		// Unseen group storage counts as empty (never blocks "Ready now"), so say it was never
		// seen - unless the toggle is off, when there is nothing to open.
		if (clauses.size() < MAX_CLAUSES && s.getAccountType().isGroup() && s.isGroupStorageEnabled() && !s.isGroupStorageKnown())
		{
			clauses.add("group storage not seen");
		}

		if (clauses.size() < MAX_CLAUSES && hasRecommendedGaps(gaps))
		{
			clauses.add(recommendedClause(gaps));
		}

		MilestoneEntry entry = kb.milestoneById(goal.getId());

		// An outfit (ownedIfMin > 1) with some pieces held says "2/4 pieces".
		if (clauses.size() < MAX_CLAUSES && entry != null && entry.getOwnedIfMin() > 1)
		{
			int held = GapEngine.ownedIfHeld(entry, s);
			if (held > 0)
			{
				clauses.add(held + "/" + entry.getOwnedIf().size() + " pieces");
			}
		}

		if (clauses.size() < MAX_CLAUSES && entry != null && entry.getSpeedsUp() != null)
		{
			String skill = entry.getSpeedsUp().getName();
			clauses.add("speeds up " + skill + (targetSkills.contains(entry.getSpeedsUp()) ? " (your next " + skill + " target)" : ""));
		}

		if (clauses.size() < MAX_CLAUSES && entry != null && isUnlockCategory(goal.getCategory()) && !entry.getUnlocks().isEmpty())
		{
			clauses.add(unlocksClause(entry));
		}

		if (clauses.size() < MAX_CLAUSES && entry != null && isBiggestGearUpgrade(entry, kb, s))
		{
			clauses.add("biggest " + entry.getSubcategory() + " upgrade over what you own");
		}

		if (clauses.size() < MAX_CLAUSES && hasMaterialsInBank(gaps))
		{
			clauses.add("materials already in bank");
		}

		if (clauses.size() < MAX_CLAUSES && isJustLevels(gaps))
		{
			clauses.add(justLevelsClause(gaps));
		}

		return truncate(clauses);
	}

	private static String taskLabel(CombatAchievementTask task)
	{
		return task.getName() + " (" + TASK_TIERS[task.getTier() - 1] + ", " + task.getTier()
			+ (task.getTier() == 1 ? " point)" : " points)");
	}

	private static String objectiveWhy(RankedGoal ranked)
	{
		GoalStatus status = ranked.getStatus();
		GoalObjective objective = status.getObjective();
		List<String> clauses = new ArrayList<>();
		if (objective.isAchievementPriority())
		{
			clauses.add("Combat achievement: " + taskLabel(objective.getTasks().get(0)));
			if (objective.isGreenLogged()) clauses.add("collection log complete");
		}
		else if (!objective.getRewards().isEmpty())
		{
			RewardTarget target = objective.getRewards().get(0);
			String prefix = target.isOwnershipUnknown() ? "Check ownership: "
				: target.getValue() == RewardValue.SITUATIONAL ? "Situational option: " : "Next gain: ";
			clauses.add(prefix + target.getName() + " — " + target.getBenefit());
		}
		else clauses.add("No remaining progression objective");
		clauses.add(ranked.isLater() ? "later: stage " + status.getGoal().getStage()
			: status.isReady() && !status.isBankUnknown() ? "entry requirements met" : awayClause(status.getGaps()));
		return truncate(clauses);
	}


	/** The selected achievement's exact requirements must not be truncated into an incomplete instruction. */
	private static List<String> objectiveExplain(RankedGoal ranked, int accountStage)
	{
		GoalStatus status = ranked.getStatus();
		GoalObjective objective = status.getObjective();
		List<String> lines = new ArrayList<>();
		if (objective.isGreenLogged()) lines.add("Collection log complete; return only for unfinished combat achievements");
		if (!objective.getTasks().isEmpty())
		{
			CombatAchievementTask task = objective.getTasks().get(0);
			lines.add("Next combat achievement: " + taskLabel(task));
			lines.add(task.getDescription());
			if (objective.getTasks().size() > 1) lines.add((objective.getTasks().size() - 1) + " other unfinished combat achievements");
		}
		for (RewardTarget target : objective.getRewards())
		{
			String label = target.isOwnershipUnknown() ? "Ownership unconfirmed: "
				: target.getValue() == RewardValue.SITUATIONAL ? "Situational gain: " : "Remaining gain: ";
			lines.add(label + target.getName() + " — " + target.getBenefit());
		}
		if (objective.isBankUnknown()) lines.add("Partial or unseen bank: absent from this snapshot does not mean unowned; confirm before grinding");
		if (status.getGoal().getCategory() == GoalCategory.BOSS && !objective.isAchievementsKnown())
		{
			lines.add("Combat achievement data unavailable; not assumed unfinished");
		}
		for (Met met : status.getMet())
		{
			if (met.getKind() == Met.Kind.RECOMMENDED_GEAR) lines.add(met.getLabel());
		}
		if (!status.getGaps().isEmpty()) lines.add(listLine("Missing entry requirements: ", missingItems(status), MAX_MISSING));
		else lines.add("Entry requirements met" + (objective.getTasks().isEmpty() ? "" : "; achievement-specific restrictions still apply"));
		if (status.getGoal().getStage() > accountStage)
		{
			lines.add("Stage " + status.getGoal().getStage() + " goal, you are stage " + accountStage);
		}
		return List.copyOf(lines);
	}

	/** As {@link #explain(RankedGoal, KnowledgeBase, Snapshot, int)}, estimating the account stage itself. */
	public List<String> explain(RankedGoal r, KnowledgeBase kb, Snapshot s)
	{
		return explain(r, kb, s, StageEstimator.estimate(s, kb));
	}

	/**
	 * The "Why?" behind a suggestion, one line each, in order - a skill target's
	 * parents and bank coverage; recommended gear met (with names); stats met; what is missing
	 * (with have/need); a bank-unknown note; a stage warning; "Ready now". Every line is at most 90 characters, never
	 * ends with a period; at most six lines, always at least one.
	 */
	public List<String> explain(RankedGoal r, KnowledgeBase kb, Snapshot s, int accountStage)
	{
		GoalStatus status = r.getStatus();
		Goal goal = status.getGoal();
		List<String> lines = new ArrayList<>();
		if (status.getObjective() != null) return objectiveExplain(r, accountStage);

		// A curated priority reason ("Unlocks the route to Piety") leads.
		String reason = kb.priorityReason(goal.getId());
		if (reason != null)
		{
			lines.add(fit(reason));
		}
		if (goal.getUpgrade() != null)
		{
			lines.add(fit(upgradeClause(goal)));
			lines.add(fit(status.getNotes().get(0)));
		}

		boolean skillTarget = goal.getCategory() == GoalCategory.SKILL_TARGET;
		if (skillTarget)
		{
			SkillLevelGap gap = (SkillLevelGap) status.getGaps().get(0);
			for (GoalRef parent : status.getParents().stream().limit(MAX_PARENTS).collect(Collectors.toList()))
			{
				lines.add(fit("Needed for " + parent.getName() + " (" + parent.getLevel() + " " + gap.getSkill().getName() + ")"));
			}
			if (!status.getQuestXp().isEmpty())
			{
				lines.add(questXpLine(gap, status.getQuestXp()));
			}
			if (status.getBankRoute() != null)
			{
				lines.add(bankCoversLine(gap, status.getBankRoute()));
			}
		}

		if (!r.getUnblocks().isEmpty())
		{
			lines.add(unblocksLine(r.getUnblocks()));
		}

		MilestoneEntry entry = kb.milestoneById(goal.getId());
		if (entry != null && (entry.getSlayerReward() != null || entry.getRequiredSlayerUnlock() != null))
		{
			if (entry.getReason() != null && !entry.getReason().isEmpty()) lines.add(fit(entry.getReason()));
			if (entry.getSlayerPoints() != null)
			{
				lines.add("Slayer points " + s.getSlayer().getPoints() + "/" + entry.getSlayerPoints());
			}
		}
		List<String> prayers = metLabels(status, Met.Kind.RECOMMENDED_PRAYER);
		if (!prayers.isEmpty()) lines.add(listLine("Prayers unlocked: ", prayers, MAX_STATS));
		if (entry != null && entry.getRecommended() != null && !entry.getRecommended().getGear().isEmpty())
		{
			List<String> owned = metLabels(status, Met.Kind.RECOMMENDED_GEAR);
			String prefix = "Meets " + owned.size() + " of " + entry.getRecommended().getGear().size() + " required gear roles";
			lines.add(owned.isEmpty() ? prefix : listLine(prefix + ": ", owned, MAX_GEAR_NAMES));
		}

		List<String> stats = new ArrayList<>();
		for (Met met : status.getMet())
		{
			if (met.getKind() == Met.Kind.SKILL || met.getKind() == Met.Kind.RECOMMENDED_SKILL)
			{
				stats.add(stripHave(met.getLabel()));
			}
		}
		if (!stats.isEmpty())
		{
			lines.add(listLine("Stats met: ", stats, MAX_STATS));
		}

		if (!skillTarget && !status.getGaps().isEmpty())
		{
			lines.add(listLine("Missing: ", missingItems(status), MAX_MISSING));
		}

		// An item requirement with no bank to check against counts as missing
		if (status.isBankUnknown())
		{
			lines.add("Bank not seen yet, materials assumed missing");
		}

		if (goal.getStage() > accountStage)
		{
			lines.add("Stage " + goal.getStage() + " goal, you are stage " + accountStage);
		}

		if (!r.isLater() && status.isReady() && !status.isBankUnknown())
		{
			lines.add("Ready now");
		}

		return lines.size() <= MAX_LINES ? lines : List.copyOf(lines.subList(0, MAX_LINES));
	}

	/** "Unblocks: A, B, C +N more" - the dependents come already in rank order. */
	private static String unblocksLine(List<String> unblocks)
	{
		int shown = Math.min(MAX_UNBLOCKS, unblocks.size());
		String line = "Unblocks: " + String.join(", ", unblocks.subList(0, shown));
		int more = unblocks.size() - shown;
		return more > 0 ? line + " +" + more + " more" : line;
	}

	/** "Quests you can do now give 27,500 Attack xp: A, B, C +N more" - sources come largest first. */
	private static String questXpLine(SkillLevelGap gap, List<QuestXp> quests)
	{
		long total = quests.stream().mapToLong(QuestXp::getXp).sum();
		List<String> names = quests.stream().map(QuestXp::getName).collect(Collectors.toList());
		String prefix = "Quests you can do now give " + String.format("%,d", total) + " " + gap.getSkill().getName() + " xp: ";
		int shown = Math.min(MAX_UNBLOCKS, names.size());
		int more = names.size() - shown;
		return prefix + String.join(", ", names.subList(0, shown)) + (more > 0 ? " +" + more + " more" : "");
	}

	private static String bankCoversLine(SkillLevelGap gap, Route route)
	{
		if (route.getUncoveredXp() == 0)
		{
			return "Bank covers " + gap.getHave() + "-" + gap.getNeed();
		}
		int reached = Experience.getLevelForXp((int) Math.min(route.getFinalXp(), Integer.MAX_VALUE));
		String covers = reached > gap.getHave() ? gap.getHave() + "-" + reached : "nothing";
		return "Bank covers " + covers + "; short " + route.getUncoveredXp() + " xp";
	}

	private static List<String> metLabels(GoalStatus status, Met.Kind kind)
	{
		return status.getMet().stream().filter(m -> m.getKind() == kind).map(Met::getLabel).collect(Collectors.toList());
	}

	/** "Attack 70 (have 92)" to "Attack 70". */
	private static String stripHave(String label)
	{
		int i = label.indexOf(" (have ");
		return i < 0 ? label : label.substring(0, i);
	}

	/**
	 * One short text per unmet requirement, with have/need where there is one; a {@link GearGap}
	 * contributes the acceptable items not yet owned (those the met list doesn't name); diary tasks
	 * are counted rather than listed.
	 */
	private static List<String> missingItems(GoalStatus status)
	{
		List<String> items = new ArrayList<>();
		Set<String> ownedGear = new LinkedHashSet<>(metLabels(status, Met.Kind.RECOMMENDED_GEAR));
		int diaryTasks = 0;
		for (Gap gap : status.getGaps())
		{
			if (gap instanceof SkillLevelGap)
			{
				SkillLevelGap g = (SkillLevelGap) gap;
				items.add(g.getSkill().getName() + " " + g.getNeed() + " (have " + g.getHave() + ")");
			}
			else if (gap instanceof GearGap)
			{
				for (OwnedItem item : ((GearGap) gap).getAcceptable())
				{
					if (!ownedGear.contains(item.getName()))
					{
						items.add(item.getName());
					}
				}
			}
			else if (gap instanceof QuestPrereqGap)
			{
				items.add("quest " + ((QuestPrereqGap) gap).getQuest().getName());
			}
			else if (gap instanceof ItemGap)
			{
				ItemGap g = (ItemGap) gap;
				items.add(g.getName() + (g.getNeed() > 1 ? " \u00d7" + g.getNeed() : ""));
			}
			else if (gap instanceof CombatLevelGap)
			{
				CombatLevelGap g = (CombatLevelGap) gap;
				items.add("combat " + g.getNeed() + " (have " + g.getHave() + ")");
			}
			else if (gap instanceof QuestPointsGap)
			{
				QuestPointsGap g = (QuestPointsGap) gap;
				items.add(g.getNeed() + " quest points (have " + g.getHave() + ")");
			}
			else if (gap instanceof KudosGap)
			{
				KudosGap g = (KudosGap) gap;
				items.add(g.getNeed() + " kudos (have " + g.getHave() + ")");
			}
			else if (gap instanceof DiaryTierGap)
			{
				items.add(((DiaryTierGap) gap).getTier().name().toLowerCase().replace('_', ' ') + " diary");
			}
			else if (gap instanceof PrerequisiteGap)
			{
				items.add(((PrerequisiteGap) gap).getName() + " first");
			}
			else if (gap instanceof DiaryTaskGap)
			{
				diaryTasks++;
			}
			else if (gap instanceof SlayerPointsGap)
			{
				SlayerPointsGap g = (SlayerPointsGap) gap;
				items.add("Slayer points " + g.getHave() + "/" + g.getNeed());
			}
			else if (gap instanceof SlayerUnlockGap)
			{
				items.add(((SlayerUnlockGap) gap).getReward().getDisplayName() + ": not unlocked");
			}
			else if (gap instanceof PrayerUnlockGap)
			{
				items.add(((PrayerUnlockGap) gap).getPrayer().getDisplayName() + ": not unlocked");
			}
		}
		if (diaryTasks > 0)
		{
			items.add(diaryTasks + " diary task" + (diaryTasks == 1 ? "" : "s"));
		}
		return items;
	}

	/**
	 * {@code prefix} plus up to {@code max} of {@code items} joined by ", ", then an ellipsis when any
	 * were left out; items are dropped from the end until the line fits {@link #LINE_LEN}.
	 */
	private static String listLine(String prefix, List<String> items, int max)
	{
		int keep = Math.min(max, items.size());
		while (true)
		{
			String line = prefix + String.join(", ", items.subList(0, keep)) + (keep < items.size() ? ELLIPSIS : "");
			if (line.length() <= LINE_LEN || keep <= 1)
			{
				return fit(line);
			}
			keep--;
		}
	}

	/** Hard cap for a single line that is still too long (a very long name): cut with an ellipsis. */
	private static String fit(String line)
	{
		return line.length() <= LINE_LEN ? line : line.substring(0, LINE_LEN - 1) + ELLIPSIS;
	}

	private static boolean hasRecommendedGaps(List<Gap> gaps)
	{
		for (Gap gap : gaps)
		{
			if (isRecommended(gap))
			{
				return true;
			}
		}
		return false;
	}

	private static boolean isRecommended(Gap gap)
	{
		return (gap instanceof SkillLevelGap && ((SkillLevelGap) gap).isRecommended())
			|| (gap instanceof CombatLevelGap && ((CombatLevelGap) gap).isRecommended())
			|| (gap instanceof PrayerUnlockGap && ((PrayerUnlockGap) gap).isRecommended())
			|| gap instanceof GearGap;
	}

	/** "recommended: 85 Ranged (have 70), Bandos or better" - up to three recommended items, one clause. */
	private static String recommendedClause(List<Gap> gaps)
	{
		List<String> items = new ArrayList<>();
		for (Gap gap : gaps)
		{
			if (gap instanceof SkillLevelGap && ((SkillLevelGap) gap).isRecommended())
			{
				SkillLevelGap g = (SkillLevelGap) gap;
				items.add(g.getNeed() + " " + g.getSkill().getName() + " (have " + g.getHave() + ")");
			}
			else if (gap instanceof CombatLevelGap && ((CombatLevelGap) gap).isRecommended())
			{
				CombatLevelGap g = (CombatLevelGap) gap;
				items.add("combat " + g.getNeed() + " (have " + g.getHave() + ")");
			}
			else if (gap instanceof GearGap)
			{
				items.add(gearGapText((GearGap) gap));
			}
			else if (gap instanceof PrayerUnlockGap)
			{
				items.add(((PrayerUnlockGap) gap).getPrayer().getDisplayName() + ": not unlocked");
			}
		}
		return "recommended: " + items.stream().limit(3).collect(Collectors.joining(", "));
	}

	/** A missing combat role, rather than an arbitrary count of unrelated items. */
	private static String gearGapText(GearGap gap)
	{
		List<String> names = gap.getAcceptable().stream().map(OwnedItem::getName).limit(3).collect(Collectors.toList());
		String namesText = String.join(", ", names) + (gap.getAcceptable().size() > 3 ? "…" : "");
		return gap.getRole() + ": " + (gap.isBankUnknown() ? "check for " : "need ") + namesText + " (or a tracked upgrade)";
	}

	private static String awayClause(List<Gap> gaps)
	{
		int unmet = GoalMetrics.unmetCount(gaps);
		return unmet + " requirement" + (unmet == 1 ? "" : "s") + " away";
	}

	private static boolean isUnlockCategory(GoalCategory category)
	{
		return category == GoalCategory.MILESTONE || category == GoalCategory.BOSS || category == GoalCategory.SLAYER_TARGET;
	}

	private static String unlocksClause(MilestoneEntry entry)
	{
		return "unlocks " + entry.getUnlocks().stream().limit(MAX_UNLOCKS).collect(Collectors.joining(", "));
	}

	/**
	 * True for a gear milestone whose {@code gearTier} exceeds the highest {@code gearTier} among
	 * gear milestones in the same subcategory that the player already owns (any {@code ownedIf} id
	 * held in bank, inventory, or equipment). No owned gear in the subcategory counts as no tier to
	 * beat, so the entry always qualifies then.
	 */
	private static boolean isBiggestGearUpgrade(MilestoneEntry entry, KnowledgeBase kb, Snapshot s)
	{
		if (entry.getCategory() != MilestoneCategory.GEAR || entry.getGearTier() == null || entry.getSubcategory() == null)
		{
			return false;
		}
		Integer ownedMaxTier = null;
		for (MilestoneEntry other : kb.getMilestones())
		{
			if (other.getCategory() != MilestoneCategory.GEAR
				|| other.getGearTier() == null
				|| !entry.getSubcategory().equals(other.getSubcategory())
				|| !isOwned(other, s))
			{
				continue;
			}
			if (ownedMaxTier == null || other.getGearTier() > ownedMaxTier)
			{
				ownedMaxTier = other.getGearTier();
			}
		}
		return ownedMaxTier == null || entry.getGearTier() > ownedMaxTier;
	}

	private static boolean isOwned(MilestoneEntry entry, Snapshot s)
	{
		for (OwnedItem owned : entry.getOwnedIf())
		{
			if (GapEngine.anyIdHeld(owned.getIds(), s))
			{
				return true;
			}
		}
		return false;
	}

	/** Per rule, evaluated even though {@link com.signpost.engine.GapEngine} never emits such an {@link ItemGap}. */
	private static boolean hasMaterialsInBank(List<Gap> gaps)
	{
		List<ItemGap> itemGaps = gaps.stream().filter(g -> g instanceof ItemGap).map(g -> (ItemGap) g).collect(Collectors.toList());
		return !itemGaps.isEmpty() && itemGaps.stream().allMatch(g -> g.getHave() != null && g.getHave() >= g.getNeed());
	}

	private static boolean isJustLevels(List<Gap> gaps)
	{
		return !gaps.isEmpty() && gaps.stream().allMatch(g -> g instanceof SkillLevelGap);
	}

	private static String justLevelsClause(List<Gap> gaps)
	{
		String skills = gaps.stream()
			.map(g -> (SkillLevelGap) g)
			.map(g -> g.getSkill().getName() + " " + g.getHave() + "/" + g.getNeed())
			.collect(Collectors.joining(", "));
		return "just levels: " + skills;
	}

	/** Drops trailing clauses (never mid-word) until it fits; a single clause still too long is cut with "…". */
	static String truncate(List<String> clauses)
	{
		List<String> kept = new ArrayList<>(clauses);
		String joined = String.join("; ", kept);
		while (joined.length() > MAX_LEN && kept.size() > 1)
		{
			kept.remove(kept.size() - 1);
			joined = String.join("; ", kept);
		}
		if (joined.length() <= MAX_LEN)
		{
			return joined;
		}
		return kept.get(0).substring(0, MAX_LEN - 1) + "…";
	}
}
