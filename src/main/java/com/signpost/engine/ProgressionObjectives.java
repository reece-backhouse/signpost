package com.signpost.engine;

import com.signpost.engine.model.GoalObjective;
import com.signpost.engine.model.RewardTarget;
import com.signpost.kb.BossReward;
import com.signpost.kb.GearLadder;
import com.signpost.kb.KnowledgeBase;
import com.signpost.kb.MilestoneCategory;
import com.signpost.kb.MilestoneEntry;
import com.signpost.kb.OwnedItem;
import com.signpost.kb.RewardValue;
import com.signpost.snapshot.BossProgress;
import com.signpost.snapshot.CombatAchievementTask;
import com.signpost.snapshot.Snapshot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Remaining gains are evaluated against the account, independently of entry readiness. */
final class ProgressionObjectives
{
	private static final Comparator<RewardTarget> TARGET_ORDER = Comparator.comparing(RewardTarget::isOwnershipUnknown)
		.thenComparing(RewardTarget::getValue);
	private static final Comparator<CombatAchievementTask> TASK_ORDER = Comparator.comparingInt(CombatAchievementTask::getTier)
		.thenComparingInt(CombatAchievementTask::getId);

	private ProgressionObjectives() {}

	static GoalObjective evaluate(MilestoneEntry entry, Snapshot snapshot, KnowledgeBase kb, GearComparison gear)
	{
		if (entry.getCategory() != MilestoneCategory.BOSS && entry.getCategory() != MilestoneCategory.GEAR) return null;
		BossProgress progress = snapshot.getBossProgress().get(entry.getId());
		if (entry.getCategory() == MilestoneCategory.GEAR && entry.getOwnedIf().isEmpty()) return null;
		boolean boss = entry.getCategory() == MilestoneCategory.BOSS;
		if (boss && progress == null && entry.getBossRewards().isEmpty()) return null;
		boolean green = boss ? progress != null && Boolean.TRUE.equals(progress.getGreenLogged()) : providerGreenLogged(entry, snapshot);
		List<RewardTarget> targets = new ArrayList<>();
		if (!green)
		{
			if (boss)
			{
				for (BossReward reward : entry.getBossRewards())
				{
					if (!covered(reward, snapshot, gear)) targets.add(target(reward, reward.getName(), reward.getIds(), kb, gear));
				}
			}
			else
			{
				MilestoneEntry provider = entry.getObtainedFrom() == null ? null : kb.milestoneById(entry.getObtainedFrom());
				for (OwnedItem item : entry.getOwnedIf())
				{
					if (gear.covers(item.getIds())) continue;
					BossReward reward = matchingReward(provider, item.getIds(), kb);
					if (reward != null)
					{
						targets.add(target(reward, item.getName(), item.getIds(), kb, gear));
					}
					else
					{
						targets.add(gearTarget(entry, item.getName(), item.getIds(), kb, gear));
					}
				}
			}
		}
		targets.sort(TARGET_ORDER);
		List<CombatAchievementTask> tasks = progress != null && progress.isCombatAchievementsKnown()
			? new ArrayList<>(progress.getRemainingTasks()) : new ArrayList<>();
		tasks.sort(TASK_ORDER);
		return new GoalObjective(targets, tasks, green, progress != null && progress.isCombatAchievementsKnown(),
			targets.stream().anyMatch(RewardTarget::isOwnershipUnknown));
	}

	static GoalObjective upgrade(MilestoneEntry provider, GearLadder.Rung rung, KnowledgeBase kb, GearComparison gear)
	{
		MilestoneEntry rewardSource = provider.getCategory() == MilestoneCategory.BOSS ? provider
			: provider.getObtainedFrom() == null ? null : kb.milestoneById(provider.getObtainedFrom());
		BossReward reward = matchingReward(rewardSource, rung.getVariants(), kb);
		RewardTarget target = reward == null ? gearTarget(provider, rung.getName(), rung.getVariants(), kb, gear)
			: target(reward, rung.getName(), rung.getVariants(), kb, gear);
		return new GoalObjective(List.of(target), List.of(), false, false, target.isOwnershipUnknown());
	}

	private static RewardTarget gearTarget(MilestoneEntry entry, String name, List<Integer> ids, KnowledgeBase kb, GearComparison gear)
	{
		String benefit = String.join("; ", entry.getUnlocks());
		if (benefit.isBlank()) benefit = kb.getGearCatalog().benefit(ids.get(0));
		if (benefit == null || benefit.isBlank()) benefit = entry.getReason();
		if (benefit == null || benefit.isBlank()) benefit = "Completes the remaining " + entry.getName() + " objective";
		return new RewardTarget(name, withReplacement(benefit, ids, kb, gear), RewardValue.USEFUL, !gear.ownershipKnown(ids));
	}

	static boolean providerGreenLogged(MilestoneEntry provider, Snapshot snapshot)
	{
		if (provider == null) return false;
		String bossId = provider.getCategory() == MilestoneCategory.BOSS ? provider.getId() : provider.getObtainedFrom();
		if (bossId == null) return false;
		BossProgress progress = snapshot.getBossProgress().get(bossId);
		return progress != null && Boolean.TRUE.equals(progress.getGreenLogged());
	}

	private static boolean covered(BossReward reward, Snapshot snapshot, GearComparison gear)
	{
		return "rigour".equals(reward.getPrayer()) && snapshot.isRigour()
			|| "augury".equals(reward.getPrayer()) && snapshot.isAugury()
			|| gear.covers(reward.getIds());
	}

	private static RewardTarget target(BossReward reward, String name, List<Integer> ownershipIds, KnowledgeBase kb, GearComparison gear)
	{
		RewardValue value = reward.getValue();
		String benefit = reward.getBenefit();
		List<List<Integer>> context = reward.getSituationalWhenAllOwned();
		if (!context.isEmpty() && context.stream().allMatch(gear::covers))
		{
			if (value.ordinal() < RewardValue.SITUATIONAL.ordinal()) value = RewardValue.SITUATIONAL;
			String existing = gear.coveringName(context.get(0));
			benefit += (benefit.endsWith(".") ? " " : "; ") + "situational rather than a primary upgrade with " + existing + " already available";
		}
		boolean known = reward.getPrayer() != null || gear.ownershipKnown(ownershipIds);
		return new RewardTarget(name, withReplacement(benefit, ownershipIds, kb, gear), value, !known);
	}

	private static String withReplacement(String benefit, List<Integer> targets, KnowledgeBase kb, GearComparison gear)
	{
		Integer best = null;
		for (int owned : gear.ownedIds())
		{
			for (int target : targets)
			{
				if (kb.getGearCatalog().canonicalId(target) != owned && kb.getGearCatalog().covers(target, owned)
					&& (best == null || kb.getGearCatalog().covers(owned, best))) best = owned;
			}
		}
		String name = best == null ? null : kb.getGearCatalog().name(best);
		return name == null ? benefit : benefit + (benefit.endsWith(".") ? " " : "; ") + "replaces your " + name;
	}

	private static BossReward matchingReward(MilestoneEntry provider, List<Integer> ids, KnowledgeBase kb)
	{
		if (provider == null) return null;
		for (BossReward reward : provider.getBossRewards())
		{
			for (int id : ids)
			{
				for (int rewardId : reward.getIds())
				{
					if (kb.getGearCatalog().canonicalId(id) == kb.getGearCatalog().canonicalId(rewardId)) return reward;
				}
			}
		}
		return null;
	}
}
