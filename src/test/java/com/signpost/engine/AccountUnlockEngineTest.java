package com.signpost.engine;

import com.google.gson.Gson;
import com.signpost.engine.model.Advice;
import com.signpost.engine.model.GoalStatus;
import com.signpost.engine.model.PrayerUnlockGap;
import com.signpost.engine.model.SlayerPointsGap;
import com.signpost.kb.KnowledgeBase;
import com.signpost.kb.MilestoneCategory;
import com.signpost.snapshot.PrayerUnlock;
import com.signpost.snapshot.SlayerReward;
import com.signpost.snapshot.SlayerState;
import com.signpost.snapshot.Snapshot;
import com.signpost.snapshot.Spellbook;
import com.signpost.store.AccountData;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountUnlockEngineTest
{
	private final GapEngine engine = new GapEngine(new BoostTable());

	@Test
	void aRewardBecomesAffordableAtItsCostButOnlyTheUnlockRemovesIt()
	{
		KnowledgeBase kb = KnowledgeBase.load(new Gson());
		Snapshot base = new SnapshotBuilder().build();
		Snapshot shortByOne = base.toBuilder().slayer(slayer(49, Set.of())).build();
		GoalStatus missing = status(engine.evaluate(shortByOne, kb), "slayer:bigger-and-badder");
		assertFalse(missing.isReady());
		assertTrue(missing.getGaps().contains(new SlayerPointsGap(49, 50)));
		Snapshot affordable = base.toBuilder().slayer(slayer(50, Set.of())).build();
		assertTrue(status(engine.evaluate(affordable, kb), "slayer:bigger-and-badder").isReady());
		Snapshot purchased = base.toBuilder().slayer(slayer(0, Set.of(SlayerReward.BIGGER_AND_BADDER))).build();
		assertFalse(hasGoal(engine.evaluate(purchased, kb), "slayer:bigger-and-badder"));
	}

	@Test
	void anImbuedCosmeticHelmetCompletesBothHelmetStagesWithoutRequiringAnotherBlackMask()
	{
		KnowledgeBase kb = KnowledgeBase.load(new Gson());
		Snapshot snapshot = new SnapshotBuilder().inventoryItem(26675, "Black slayer helmet (i)", 1).build();
		List<GoalStatus> statuses = engine.evaluate(snapshot, kb);
		assertFalse(hasGoal(statuses, "milestone:slayer-helmet"));
		assertFalse(hasGoal(statuses, "milestone:slayer-helmet-imbued"));
	}

	@Test
	void unlockedPrayersDoNotRequireTheConsumedScrollAndABankedScrollDoesNotImplyAnUnlock()
	{
		KnowledgeBase kb = KnowledgeBase.load(new Gson());
		Snapshot locked = new SnapshotBuilder().skill(Skill.PRAYER, 99).skill(Skill.DEFENCE, 99)
			.bankItem(21034, "Dexterous prayer scroll", 1).bankItem(21079, "Arcane prayer scroll", 1)
			.bankItem(21047, "Torn prayer scroll", 1).build();
		assertTrue(hasGoal(engine.evaluate(locked, kb), "milestone:rigour"));
		assertTrue(hasGoal(engine.evaluate(locked, kb), "milestone:augury"));
		Snapshot unlocked = new SnapshotBuilder().build().toBuilder().rigour(true).augury(true).preserve(true).build();
		List<GoalStatus> statuses = engine.evaluate(unlocked, kb);
		assertFalse(hasGoal(statuses, "milestone:rigour"));
		assertFalse(hasGoal(statuses, "milestone:augury"));
		assertFalse(hasGoal(statuses, "milestone:preserve"));
	}

	@Test
	void aBossPrayerGapLinksToItsUnlockAndDisappearsOnlyAfterReadingThatFlag()
	{
		KnowledgeBase kb = new KbBuilder().milestone("boss:test", MilestoneCategory.BOSS, "Test boss", 5)
			.recommendedPrayer(PrayerUnlock.RIGOUR).build();
		Snapshot locked = new SnapshotBuilder().build();
		GoalStatus before = status(engine.evaluate(locked, kb), "boss:test");
		assertFalse(before.isReady());
		PrayerUnlockGap gap = (PrayerUnlockGap) before.getGaps().get(0);
		assertEquals("milestone:rigour", gap.getPrayer().getMilestoneId());
		assertTrue(status(engine.evaluate(locked.toBuilder().rigour(true).build(), kb), "boss:test").isReady());
	}

	@Test
	void lunarAccessIsKnownFromEitherTheSelectedSpellbookOrTheCompletedQuest()
	{
		KnowledgeBase kb = KnowledgeBase.load(new Gson());
		Snapshot base = new SnapshotBuilder().build();
		assertTrue(hasGoal(engine.evaluate(base, kb), "milestone:lunar-spellbook"));
		assertFalse(hasGoal(engine.evaluate(base.toBuilder().spellbook(Spellbook.LUNAR).build(), kb), "milestone:lunar-spellbook"));
		Snapshot finished = new SnapshotBuilder().quest(Quest.LUNAR_DIPLOMACY, QuestState.FINISHED).build();
		assertFalse(hasGoal(engine.evaluate(finished, kb), "milestone:lunar-spellbook"));
	}

	@Test
	void runesCarriedInAPouchCoverTrainingInsteadOfProducingAGatheringShortfall()
	{
		KnowledgeBase kb = new KbBuilder().quest(0, "Magic training goal").skill(Skill.MAGIC, 2)
			.method(Skill.MAGIC, "Spell", 1, 10).material(561, 1).build();
		AccountData prefs = new AccountData(Map.of(), null, Map.of(), Set.of(), List.of(), "quest:0", Set.of());
		Engine full = new Engine(new BoostTable());
		Snapshot base = new SnapshotBuilder().build();
		Advice empty = full.run(base, kb, prefs, Instant.EPOCH);
		Advice carried = full.run(base.toBuilder().runePouch(Map.of(561, 9)).build(), kb, prefs, Instant.EPOCH);
		assertTrue(empty.getFocus().getRoute().getUncoveredXp() > 0);
		assertEquals(0, carried.getFocus().getRoute().getUncoveredXp());
		assertEquals(9, carried.getFocus().getRoute().getSteps().get(0).getCount());
	}

	private static SlayerState slayer(int points, Set<SlayerReward> unlocks)
	{
		return new SlayerState(null, 0, 5, "Duradel", 0, points, unlocks);
	}

	private static boolean hasGoal(List<GoalStatus> statuses, String id)
	{
		return statuses.stream().anyMatch(status -> status.getGoal().getId().equals(id));
	}

	private static GoalStatus status(List<GoalStatus> statuses, String id)
	{
		return statuses.stream().filter(status -> status.getGoal().getId().equals(id)).findFirst()
			.orElseThrow(() -> new AssertionError("Missing goal " + id));
	}
}
