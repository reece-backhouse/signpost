package com.signpost.engine;

import com.google.gson.Gson;
import com.signpost.engine.model.Advice;
import com.signpost.engine.model.GoalObjective;
import com.signpost.engine.model.RewardTarget;
import com.signpost.kb.RewardValue;
import com.signpost.engine.model.Goal;
import com.signpost.engine.model.GoalCategory;
import com.signpost.engine.model.GoalStatus;
import com.signpost.kb.BossReward;
import com.signpost.kb.KnowledgeBase;
import com.signpost.kb.MilestoneCategory;
import com.signpost.snapshot.BossProgress;
import com.signpost.snapshot.CombatAchievementTask;
import com.signpost.snapshot.Snapshot;
import com.signpost.store.AccountData;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BossProgressionTest
{
	private static final String MOONS = "boss:moons-of-peril";
	private static final Instant NOW = Instant.parse("2026-09-09T12:00:00Z");
	private final Engine engine = new Engine(new BoostTable());

	@Test
	void meaningfulGainsOutrankMarginalGrindsWithoutDroppingUsefulUnlocks()
	{
		var oldObjective = new GoalObjective(List.of(new RewardTarget("Alternative weapon", "A niche option", RewardValue.SITUATIONAL, false)), List.of(), false, false, false);
		var nextObjective = new GoalObjective(List.of(new RewardTarget("Major upgrade", "Fills a combat gap", RewardValue.MAJOR, false)), List.of(), false, false, false);
		var oldGoal = new Goal("old", GoalCategory.BOSS, "Older boss", "", 10, 2);
		var nextGoal = new Goal("next", GoalCategory.BOSS, "Current boss", "", 5, 4);
		var unlockGoal = new Goal("unlock", GoalCategory.MILESTONE, "Useful unlock", "", 9, 2);
		var old = new GoalStatus(oldGoal, List.of(), true, false, List.of(), List.of(), List.of(), null, 0, List.of(), oldObjective);
		var next = new GoalStatus(nextGoal, List.of(new com.signpost.engine.model.CombatLevelGap(100, 110, false)), false, false,
			List.of(), List.of(), List.of(), null, 0, List.of(), nextObjective);
		var unlock = new GoalStatus(unlockGoal, List.of(), true, false, List.of());
		var ranked = new Ranker().rank(List.of(old, next, unlock), Set.of(), List.of(), 4);
		assertEquals(List.of("unlock", "next", "old"), ranked.stream().map(r -> r.getStatus().getGoal().getId())
			.collect(Collectors.toList()));
	}

	@Test
	void greenLogWithNoRemainingTasksRetiresBossEvenWhenPinnedAndItemsAreNoLongerHeld()
	{
		KnowledgeBase kb = moons();
		Snapshot snapshot = observed(new BossProgress(true, true, List.of()));
		AccountData prefs = AccountData.empty();
		prefs.getPins().add(MOONS);
		Advice advice = engine.run(snapshot, kb, prefs, NOW);
		assertTrue(advice.getRanked().stream().noneMatch(r -> r.getStatus().getGoal().getId().equals(MOONS)));
		assertNull(advice.getFocus());
	}

	@Test
	void greenLogReturnsOnlyForNamedUnfinishedAchievementAndRetiresWhenItCompletes()
	{
		CombatAchievementTask task = new CombatAchievementTask(550, "No mistakes", "Defeat the boss without taking avoidable damage.", 3);
		Snapshot snapshot = observed(new BossProgress(true, true, List.of(task)));
		AccountData prefs = AccountData.empty();
		prefs.setFocusGoalId(MOONS);
		Advice before = engine.run(snapshot, moons(), prefs, NOW);
		GoalObjective objective = before.getFocus().getStatus().getObjective();
		assertEquals(List.of(task), objective.getTasks());
		assertTrue(before.getWhys().get(MOONS).contains(task.getName()));
		assertTrue(before.getExplanations().get(MOONS).contains(task.getDescription()));
		assertTrue(before.getExplanations().get(MOONS).stream().anyMatch(s -> s.contains("3 points")));
		assertFalse(before.getWhys().get(MOONS).contains("Blood moon"));
		assertFalse(before.getReasons().containsKey(MOONS), "static drop advertising must not override the personal reason");

		Advice after = engine.run(observed(new BossProgress(true, true, List.of())), moons(), prefs, NOW.plusSeconds(1), before);
		assertTrue(after.getPicked().isEmpty());
		assertNull(after.getFocus());
		assertEquals(List.of(MOONS), after.getCompletedSinceLast().stream().map(g -> g.getId()).collect(java.util.stream.Collectors.toList()));
	}

	@Test
	void unknownAchievementsDoNotInventAReasonToReturnToAGreenLog()
	{
		Advice advice = engine.run(observed(new BossProgress(true, false, List.of())), moons());
		assertTrue(advice.getPicked().isEmpty());
	}

	@Test
	void unavailableAchievementDataDoesNotReportThePreviousObjectiveAsCompleted()
	{
		CombatAchievementTask task = new CombatAchievementTask(550, "No mistakes", "Avoid all special attacks.", 3);
		Advice before = engine.run(observed(new BossProgress(true, true, List.of(task))), moons());
		Advice after = engine.run(observed(new BossProgress(true, false, List.of())), moons(), AccountData.empty(), NOW, before);
		assertTrue(after.getCompletedSinceLast().isEmpty());
	}

	@Test
	void sharedAndSuperiorGearRemoveOnlyTheirMatchingRewardReasons()
	{
		KnowledgeBase kb = new KbBuilder().milestone("boss:test", MilestoneCategory.BOSS, "Boss", 8)
			.bossReward(new BossReward("Blood moon chestplate", List.of(29022), List.of(11832), null, RewardValue.USEFUL, "Progress toward " + "Blood moon chestplate", List.of()))
			.bossReward("Osmumten's fang", 26219).bossReward("Lightbearer", 25975).build();
		Snapshot snapshot = new SnapshotBuilder().bankItem(11832, "Bandos chestplate", 1)
			.groupStorageItem(26219, "Osmumten's fang", 1).build();
		Advice advice = engine.run(snapshot, kb);
		assertEquals(List.of("Lightbearer"), advice.getPicked().get(0).getStatus().getObjective().getRewards()
			.stream().map(RewardTarget::getName).collect(Collectors.toList()));
		assertFalse(advice.getWhys().get("boss:test").contains("fang"));
	}

	@Test
	void consumedPrayerScrollIsNotMissingAndExhaustedRewardsRetireTheBoss()
	{
		KnowledgeBase kb = new KbBuilder().milestone("boss:cox", MilestoneCategory.BOSS, "Chambers", 8)
			.bossReward(new BossReward("Rigour", List.of(21034), List.of(), "rigour", RewardValue.USEFUL, "Progress toward " + "Rigour", List.of())).build();
		Snapshot snapshot = new SnapshotBuilder().build().toBuilder().rigour(true)
			.bossProgress(Map.of("boss:cox", new BossProgress(false, true, List.of()))).build();
		assertTrue(engine.run(snapshot, kb).getPicked().isEmpty());
		assertEquals(List.of("Rigour"), engine.run(snapshot.toBuilder().rigour(false).build(), kb)
			.getPicked().get(0).getStatus().getObjective().getRewards().stream().map(RewardTarget::getName).collect(Collectors.toList()));
	}

	@Test
	void unknownBankDoesNotClaimMissingRewardsAreCertain()
	{
		Advice advice = engine.run(new SnapshotBuilder().bankUnknown().build(), moons());
		GoalStatus status = advice.getRanked().get(0).getStatus();
		assertTrue(status.getObjective().isBankUnknown());
		assertTrue(advice.getPicked().isEmpty(), "unseen rewards are not confirmed needs");
	}

	@Test
	void greenLogAlsoRetiresGearGoalsThatWouldSendPlayerBackForItsDrops()
	{
		KnowledgeBase kb = new KbBuilder().milestone(MOONS, MilestoneCategory.BOSS, "Moons", 8)
			.bossReward("Blood moon chestplate", 29022)
			.milestone("gear:blood", MilestoneCategory.GEAR, "Blood moon chestplate", 8)
			.ownedIf("Blood moon chestplate", 29022).obtainedFrom(MOONS).build();
		Advice advice = engine.run(observed(new BossProgress(true, true, List.of())), kb);
		assertTrue(advice.getStatuses().isEmpty());
	}

	@Test
	void fullMoonsArmourAndWeaponsRemoveMoonsWithoutPretendingToKnowCollectionCompletion()
	{
		KnowledgeBase kb = KnowledgeBase.load(new Gson());
		Map<Integer,Integer> items = new java.util.HashMap<>();
		kb.milestoneById(MOONS).getBossRewards().forEach(reward -> items.put(reward.getIds().get(0), 1));
		Snapshot snapshot = new SnapshotBuilder().build().toBuilder().bank(items).build();
		assertTrue(engine.run(snapshot, kb).getRanked().stream().noneMatch(r -> r.getStatus().getGoal().getId().equals(MOONS)));
		assertTrue(snapshot.getBossProgress().isEmpty(), "current item ownership is not historical green-log evidence");
	}

	@Test
	void outclassedGearGoalsRetireWithoutACollectionLogButOtherStylesRemainRelevant()
	{
		KnowledgeBase kb = new KbBuilder().milestone(MOONS, MilestoneCategory.BOSS, "Moons", 8)
			.bossReward(new BossReward("Blood moon chestplate", List.of(29022), List.of(11832), null, RewardValue.USEFUL, "Progress toward " + "Blood moon chestplate", List.of()))
			.bossReward(new BossReward("Blood moon tassets", List.of(29025), List.of(11834), null, RewardValue.USEFUL, "Progress toward " + "Blood moon tassets", List.of()))
			.bossReward("Blue moon chestplate", 29013)
			.milestone("gear:blood", MilestoneCategory.GEAR, "Blood moon armour", 8)
			.ownedIf("Blood moon chestplate", 29022).ownedIf("Blood moon tassets", 29025)
			.ownedIfMin(2).obtainedFrom(MOONS).build();
		SnapshotBuilder inventory = new SnapshotBuilder().bankItem(11832, "Bandos chestplate", 1);
		Advice partial = engine.run(inventory.build(), kb);
		assertTrue(partial.getStatuses().stream().anyMatch(s -> s.getGoal().getId().equals("gear:blood")));

		Snapshot bandos = inventory.groupStorageItem(11834, "Bandos tassets", 1).build();
		Advice upgraded = engine.run(bandos, kb);
		assertFalse(upgraded.getStatuses().stream().anyMatch(s -> s.getGoal().getId().equals("gear:blood")),
			"an uncollected set is not an upgrade when its slots are already outclassed");
		assertEquals(List.of("Blue moon chestplate"), upgraded.getStatuses().stream()
			.filter(s -> s.getGoal().getId().equals(MOONS)).findFirst().orElseThrow().getObjective().getRewards()
			.stream().map(RewardTarget::getName).collect(Collectors.toList()));
	}

	@Test
	void owningADropComponentStopsTheBossGrindButNotTheUnmadeGearGoal()
	{
		KnowledgeBase kb = new KbBuilder().milestone("boss:source", MilestoneCategory.BOSS, "Source", 8)
			.bossReward(new BossReward("Upgraded defender", List.of(2, 3), List.of(), null, RewardValue.MAJOR,
				"Upgrade the defender slot", List.of()))
			.milestone("gear:assembled", MilestoneCategory.GEAR, "Upgraded defender", 8)
			.ownedIf("Upgraded defender", 3).obtainedFrom("boss:source").build();
		Advice advice = engine.run(new SnapshotBuilder().bankItem(2, "Defender hilt", 1).build(), kb);
		assertTrue(advice.getStatuses().stream().noneMatch(s -> s.getGoal().getId().equals("boss:source")));
		assertTrue(advice.getStatuses().stream().anyMatch(s -> s.getGoal().getId().equals("gear:assembled")));
	}

	private static KnowledgeBase moons()
	{
		return new KbBuilder().milestone(MOONS, MilestoneCategory.BOSS, "Moons of Peril", 8)
			.bossReward("Blood moon chestplate", 29022).build();
	}

	private static Snapshot observed(BossProgress progress)
	{
		return new SnapshotBuilder().build().toBuilder().bossProgress(Map.of(MOONS, progress)).build();
	}
}
