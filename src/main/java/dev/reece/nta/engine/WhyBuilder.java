package dev.reece.nta.engine;

import dev.reece.nta.engine.model.CombatLevelGap;
import dev.reece.nta.engine.model.DiaryTaskGap;
import dev.reece.nta.engine.model.DiaryTierGap;
import dev.reece.nta.engine.model.Gap;
import dev.reece.nta.engine.model.GearGap;
import dev.reece.nta.engine.model.Goal;
import dev.reece.nta.engine.model.GoalCategory;
import dev.reece.nta.engine.model.GoalRef;
import dev.reece.nta.engine.model.GoalStatus;
import dev.reece.nta.engine.model.ItemGap;
import dev.reece.nta.engine.model.KudosGap;
import dev.reece.nta.engine.model.PrerequisiteGap;
import dev.reece.nta.engine.model.Met;
import dev.reece.nta.engine.model.QuestPointsGap;
import dev.reece.nta.engine.model.QuestPrereqGap;
import dev.reece.nta.engine.model.RankedGoal;
import dev.reece.nta.engine.model.Route;
import dev.reece.nta.engine.model.SkillLevelGap;
import dev.reece.nta.kb.KnowledgeBase;
import dev.reece.nta.kb.MilestoneCategory;
import dev.reece.nta.kb.MilestoneEntry;
import dev.reece.nta.kb.OwnedItem;
import dev.reece.nta.snapshot.Snapshot;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.runelite.api.Experience;
import net.runelite.api.Skill;

/**
 * Deterministic, template-only "why" text for a {@link RankedGoal} (ticket D3): one to three
 * clauses joined with "; ", at most 140 characters, built only from the ranking inputs and the
 * knowledge base's curated {@code unlocks} field - never free text, and never {@code reason}
 * (shown separately by the UI). {@link #explain} (task 51, spec ruling 29) is the longer "Why?":
 * up to six lines of at most 90 characters naming what is met and what is missing. Pure: no
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
	private static final String ELLIPSIS = "\u2026";

	public String why(RankedGoal r, KnowledgeBase kb, Snapshot s)
	{
		return why(r, kb, s, Set.of());
	}

	/**
	 * As {@link #why(RankedGoal, KnowledgeBase, Snapshot)}; {@code targetSkills} are the skills with
	 * a ranked {@link GoalCategory#SKILL_TARGET}, so a method-linked untradeable (RL-006
	 * {@code speedsUp}) can say "speeds up Mining (your next Mining target)".
	 */
	public String why(RankedGoal r, KnowledgeBase kb, Snapshot s, Set<Skill> targetSkills)
	{
		GoalStatus status = r.getStatus();
		Goal goal = status.getGoal();
		List<Gap> gaps = status.getGaps();
		// A later goal (spec ruling 27) is never "Ready now", even with empty gaps - Ranker already
		// keeps it out of the ready tier for the same reason.
		boolean readyNow = !r.isLater() && status.isReady() && !status.isBankUnknown();

		List<String> clauses = new ArrayList<>();
		clauses.add(r.isLater() ? "later: stage " + goal.getStage() : (readyNow ? "Ready now" : awayClause(gaps)));

		if (clauses.size() < MAX_CLAUSES && status.isBankUnknown())
		{
			clauses.add("bank unknown");
		}

		// RL-003: unseen group storage counts as empty (never blocks "Ready now"), so say it was never
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

		// RL-006: an outfit (ownedIfMin > 1) with some pieces held says "2/4 pieces".
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

	/** As {@link #explain(RankedGoal, KnowledgeBase, Snapshot, int)}, estimating the account stage itself. */
	public List<String> explain(RankedGoal r, KnowledgeBase kb, Snapshot s)
	{
		return explain(r, kb, s, StageEstimator.estimate(s, kb));
	}

	/**
	 * Spec ruling 29: the "Why?" behind a suggestion, one line each, in order - a skill target's
	 * parents and bank coverage; recommended gear met (with names); stats met; what is missing
	 * (with have/need); a bank-unknown note; a stage warning; "Ready now". Every line is at most 90 characters, never
	 * ends with a period; at most six lines, always at least one.
	 */
	public List<String> explain(RankedGoal r, KnowledgeBase kb, Snapshot s, int accountStage)
	{
		GoalStatus status = r.getStatus();
		Goal goal = status.getGoal();
		List<String> lines = new ArrayList<>();

		boolean skillTarget = goal.getCategory() == GoalCategory.SKILL_TARGET;
		if (skillTarget)
		{
			SkillLevelGap gap = (SkillLevelGap) status.getGaps().get(0);
			for (GoalRef parent : status.getParents().stream().limit(MAX_PARENTS).collect(Collectors.toList()))
			{
				lines.add(fit("Needed for " + parent.getName() + " (" + parent.getLevel() + " " + gap.getSkill().getName() + ")"));
			}
			if (status.getBankRoute() != null)
			{
				lines.add(bankCoversLine(gap, status.getBankRoute()));
			}
		}

		MilestoneEntry entry = kb.milestoneById(goal.getId());
		if (entry != null && entry.getRecommended() != null && !entry.getRecommended().getGearOwnedAny().isEmpty())
		{
			List<String> owned = metLabels(status, Met.Kind.RECOMMENDED_GEAR);
			String prefix = "Meets " + owned.size() + " of " + entry.getRecommended().getGearOwnedAny().size() + " recommended gear";
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

		// RL-011 AC4: an item requirement with no bank to check against counts as missing
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
		}
		return "recommended: " + items.stream().limit(3).collect(Collectors.joining(", "));
	}

	/** "gear: own at least 2 of A, B, C… (have 0)" (task 46 - {@link GearGap} now carries a minimum, not "any one"). */
	private static String gearGapText(GearGap gap)
	{
		List<String> names = gap.getAcceptable().stream().map(OwnedItem::getName).limit(3).collect(Collectors.toList());
		String namesText = String.join(", ", names) + (gap.getAcceptable().size() > 3 ? "…" : "");
		return "gear: own at least " + gap.getRequired() + " of " + namesText + " (have " + gap.getOwned() + ")";
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

	/** Per rule, evaluated even though {@link dev.reece.nta.engine.GapEngine} never emits such an {@link ItemGap}. */
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
