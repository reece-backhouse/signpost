package dev.reece.nta.engine;

import dev.reece.nta.engine.model.CombatLevelGap;
import dev.reece.nta.engine.model.DiaryTaskGap;
import dev.reece.nta.engine.model.Gap;
import dev.reece.nta.engine.model.GoalStatus;
import dev.reece.nta.engine.model.ItemGap;
import dev.reece.nta.engine.model.ItemSource;
import dev.reece.nta.engine.model.KudosGap;
import dev.reece.nta.engine.model.QuestPointsGap;
import dev.reece.nta.engine.model.QuestPrereqGap;
import dev.reece.nta.engine.model.SkillLevelGap;
import dev.reece.nta.kb.KnowledgeBase;
import dev.reece.nta.snapshot.AccountType;
import dev.reece.nta.snapshot.DiaryTier;
import dev.reece.nta.snapshot.Snapshot;
import java.util.List;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Real quest names/ids below are pulled from the bundled quest data so entries built by
 * {@link KbBuilder} resolve to a real {@link Quest} constant; their fictional prereq/item/skill
 * requirements are entirely fixture-defined and don't reflect the actual game.
 */
class GapEngineTest
{
	private static final int ANIMAL_MAGNETISM_ID = 0;   // Quest.ANIMAL_MAGNETISM
	private static final int BIOHAZARD_ID = 9;           // Quest.BIOHAZARD
	private static final int CLOCK_TOWER_ID = 14;         // Quest.CLOCK_TOWER
	private static final int COOKS_ASSISTANT_ID = 17;     // Quest.COOKS_ASSISTANT

	private final GapEngine engine = new GapEngine(new BoostTable());

	// --- C1: each gap type is produced once by a fitting scenario. ---

	@Test
	void c1SkillLevelGapProducedWhenSkillBelowRequirement()
	{
		KnowledgeBase kb = new KbBuilder().quest(CLOCK_TOWER_ID, "Clock Tower").skill(Skill.MINING, 20).build();
		Snapshot snapshot = new SnapshotBuilder().skill(Skill.MINING, 5).build();

		GoalStatus status = onlyGoal(engine.evaluate(snapshot, kb));
		SkillLevelGap gap = (SkillLevelGap) onlyGap(status);

		assertEquals(Skill.MINING, gap.getSkill());
		assertEquals(5, gap.getHave());
		assertEquals(20, gap.getNeed());
	}

	@Test
	void c1QuestPrereqGapProducedForUnfinishedPrereq()
	{
		KnowledgeBase kb = new KbBuilder()
			.quest(CLOCK_TOWER_ID, "Clock Tower").prereq("Biohazard")
			.quest(BIOHAZARD_ID, "Biohazard")
			.build();
		Snapshot snapshot = new SnapshotBuilder().quest(Quest.BIOHAZARD, QuestState.NOT_STARTED).build();

		GoalStatus status = goalFor(engine.evaluate(snapshot, kb), "quest:" + CLOCK_TOWER_ID);
		QuestPrereqGap gap = (QuestPrereqGap) onlyGap(status);

		assertEquals(Quest.BIOHAZARD, gap.getQuest());
		assertTrue(gap.isStartHere());
	}

	@Test
	void c1ItemGapProducedWhenBankShort()
	{
		KnowledgeBase kb = new KbBuilder().quest(CLOCK_TOWER_ID, "Clock Tower").item("Bucket", 2).build();
		Snapshot snapshot = new SnapshotBuilder().bankItem(1, "Bucket", 1).build();

		GoalStatus status = onlyGoal(engine.evaluate(snapshot, kb));
		ItemGap gap = (ItemGap) onlyGap(status);

		assertEquals("Bucket", gap.getName());
		assertEquals(1, gap.getHave());
		assertEquals(2, gap.getNeed());
	}

	@Test
	void c1DiaryTaskGapProducedForIncompleteTask()
	{
		KnowledgeBase kb = new KbBuilder().diary(DiaryTier.VARROCK_EASY).task(1, "Mine some tin").skill(Skill.MINING, 10).build();
		Snapshot snapshot = new SnapshotBuilder().build();

		GoalStatus status = onlyGoal(engine.evaluate(snapshot, kb));
		DiaryTaskGap gap = (DiaryTaskGap) onlyGap(status);

		assertEquals(1, gap.getOrdinal());
		assertEquals("Mine some tin", gap.getText());
		assertEquals(1, gap.getGaps().size());
	}

	@Test
	void c1QuestPointsGapProducedWhenShort()
	{
		KnowledgeBase kb = new KbBuilder().quest(CLOCK_TOWER_ID, "Clock Tower").questPoints(10).build();
		Snapshot snapshot = new SnapshotBuilder().questPoints(3).build();

		GoalStatus status = onlyGoal(engine.evaluate(snapshot, kb));
		QuestPointsGap gap = (QuestPointsGap) onlyGap(status);

		assertEquals(3, gap.getHave());
		assertEquals(10, gap.getNeed());
	}

	@Test
	void c1KudosGapProducedWhenShort()
	{
		KnowledgeBase kb = new KbBuilder().quest(CLOCK_TOWER_ID, "Clock Tower").kudos(50).build();
		Snapshot snapshot = new SnapshotBuilder().kudos(10).build();

		GoalStatus status = onlyGoal(engine.evaluate(snapshot, kb));
		KudosGap gap = (KudosGap) onlyGap(status);

		assertEquals(10, gap.getHave());
		assertEquals(50, gap.getNeed());
	}

	@Test
	void c1CombatLevelGapProducedWhenShort()
	{
		KnowledgeBase kb = new KbBuilder().quest(CLOCK_TOWER_ID, "Clock Tower").combat(40).build();
		Snapshot snapshot = new SnapshotBuilder().build();

		GoalStatus status = onlyGoal(engine.evaluate(snapshot, kb));
		CombatLevelGap gap = (CombatLevelGap) onlyGap(status);

		assertEquals(snapshot.combatLevel(), gap.getHave());
		assertEquals(40, gap.getNeed());
	}

	// --- C2: transitive chain, deepest incomplete flagged. ---

	@Test
	void c2TransitiveChainAllUnfinishedFlagsOnlyTheDeepest()
	{
		// Clock Tower needs Biohazard needs Cook's Assistant, all unfinished.
		KnowledgeBase kb = new KbBuilder()
			.quest(CLOCK_TOWER_ID, "Clock Tower").prereq("Biohazard")
			.quest(BIOHAZARD_ID, "Biohazard").prereq("Cook's Assistant")
			.quest(COOKS_ASSISTANT_ID, "Cook's Assistant")
			.build();
		Snapshot snapshot = new SnapshotBuilder()
			.quest(Quest.BIOHAZARD, QuestState.NOT_STARTED)
			.quest(Quest.COOKS_ASSISTANT, QuestState.NOT_STARTED)
			.build();

		GoalStatus status = goalFor(engine.evaluate(snapshot, kb), "quest:" + CLOCK_TOWER_ID);
		assertEquals(2, status.getGaps().size());

		QuestPrereqGap biohazard = prereqGap(status, Quest.BIOHAZARD);
		QuestPrereqGap cooksAssistant = prereqGap(status, Quest.COOKS_ASSISTANT);
		assertFalse(biohazard.isStartHere());
		assertTrue(cooksAssistant.isStartHere());
	}

	@Test
	void c2TransitiveChainFinishedRootLeavesOnlyTheParentFlagged()
	{
		KnowledgeBase kb = new KbBuilder()
			.quest(CLOCK_TOWER_ID, "Clock Tower").prereq("Biohazard")
			.quest(BIOHAZARD_ID, "Biohazard").prereq("Cook's Assistant")
			.quest(COOKS_ASSISTANT_ID, "Cook's Assistant")
			.build();
		Snapshot snapshot = new SnapshotBuilder()
			.quest(Quest.BIOHAZARD, QuestState.NOT_STARTED)
			.quest(Quest.COOKS_ASSISTANT, QuestState.FINISHED)
			.build();

		GoalStatus status = goalFor(engine.evaluate(snapshot, kb), "quest:" + CLOCK_TOWER_ID);
		QuestPrereqGap only = (QuestPrereqGap) onlyGap(status);

		assertEquals(Quest.BIOHAZARD, only.getQuest());
		assertTrue(only.isStartHere());
	}

	@Test
	void c2UnknownPrereqNameThrowsNamingQuestAndPrereq()
	{
		KnowledgeBase kb = new KbBuilder().quest(CLOCK_TOWER_ID, "Clock Tower").prereq("Not A Real Quest").build();
		Snapshot snapshot = new SnapshotBuilder().build();

		IllegalStateException e = assertThrows(IllegalStateException.class, () -> engine.evaluate(snapshot, kb));
		assertTrue(e.getMessage().contains("Clock Tower"), e.getMessage());
		assertTrue(e.getMessage().contains("Not A Real Quest"), e.getMessage());
	}

	@Test
	void cycleSafePrereqsDoNotHang()
	{
		KnowledgeBase kb = new KbBuilder()
			.quest(CLOCK_TOWER_ID, "Clock Tower").prereq("Biohazard")
			.quest(BIOHAZARD_ID, "Biohazard").prereq("Clock Tower")
			.build();
		Snapshot snapshot = new SnapshotBuilder().quest(Quest.BIOHAZARD, QuestState.NOT_STARTED).build();

		List<GoalStatus> statuses = engine.evaluate(snapshot, kb);

		GoalStatus clockTower = goalFor(statuses, "quest:" + CLOCK_TOWER_ID);
		assertEquals(1, clockTower.getGaps().size());
	}

	// --- C3: have sums bank + inventory + equipment across ids sharing the name; unknown bank. ---

	@Test
	void c3HaveSumsAcrossBankInventoryAndEquipmentByName()
	{
		KnowledgeBase kb = new KbBuilder().quest(CLOCK_TOWER_ID, "Clock Tower").item("Rune sword", 3).build();
		Snapshot snapshot = new SnapshotBuilder()
			.bankItem(1, "Rune sword", 1)
			.inventoryItem(2, "Rune sword", 1)
			.equipmentItem(3, "Rune sword", 1)
			.build();

		GoalStatus status = onlyGoal(engine.evaluate(snapshot, kb));
		assertTrue(status.isReady(), "3 owned across bank+inventory+equipment should meet a requirement of 3");
	}

	@Test
	void c3UnknownBankGivesNullHaveAndGoalNeverReady()
	{
		KnowledgeBase kb = new KbBuilder().quest(CLOCK_TOWER_ID, "Clock Tower").item("Bucket", 1).build();
		Snapshot snapshot = new SnapshotBuilder().bankUnknown().build();

		GoalStatus status = onlyGoal(engine.evaluate(snapshot, kb));
		ItemGap gap = (ItemGap) onlyGap(status);

		assertNull(gap.getHave());
		assertTrue(status.isBankUnknown());
		assertFalse(status.isReady());
	}

	// --- C4: sourcesFor / mustObtain. ---

	@Test
	void c4SourcesForDropsGeForEveryIronTypeAndKeepsItForNormal()
	{
		List<ItemSource> sources = List.of(new ItemSource("GE", "Grand Exchange", null), new ItemSource("DROP", "Goblin", null));

		assertEquals(2, GapEngine.sourcesFor(sources, AccountType.NORMAL).size());
		for (AccountType iron : List.of(AccountType.IRONMAN, AccountType.ULTIMATE, AccountType.HARDCORE,
			AccountType.GROUP, AccountType.HARDCORE_GROUP, AccountType.UNRANKED_GROUP))
		{
			List<ItemSource> filtered = GapEngine.sourcesFor(sources, iron);
			assertEquals(1, filtered.size(), iron + " should drop the GE source");
			assertEquals("DROP", filtered.get(0).getType());
		}
	}

	@Test
	void c4MustObtainTrueWhenOnlyGeSourceExistedForAnIron()
	{
		List<ItemSource> geOnly = List.of(new ItemSource("GE", "Grand Exchange", null));

		assertTrue(GapEngine.mustObtain(geOnly, AccountType.IRONMAN));
		assertFalse(GapEngine.mustObtain(geOnly, AccountType.NORMAL));
		assertFalse(GapEngine.mustObtain(List.of(), AccountType.IRONMAN), "no sources at all is not \"must obtain\"");
	}

	// --- C5: boostable skill requirements. ---

	@Test
	void c5BoostableWithinMaxBoostSetsBoostableFrom()
	{
		// AGILITY max boost is 5; requirement 70, have 66 -> boostable from 65.
		KnowledgeBase kb = new KbBuilder().quest(CLOCK_TOWER_ID, "Clock Tower").skill(Skill.AGILITY, 70, true).build();
		Snapshot snapshot = new SnapshotBuilder().skill(Skill.AGILITY, 66).build();

		SkillLevelGap gap = (SkillLevelGap) onlyGap(onlyGoal(engine.evaluate(snapshot, kb)));

		assertEquals(65, gap.getBoostableFrom());
	}

	@Test
	void c5BoostableOutsideMaxBoostLeavesBoostableFromNull()
	{
		KnowledgeBase kb = new KbBuilder().quest(CLOCK_TOWER_ID, "Clock Tower").skill(Skill.AGILITY, 70, true).build();
		Snapshot snapshot = new SnapshotBuilder().skill(Skill.AGILITY, 60).build();

		SkillLevelGap gap = (SkillLevelGap) onlyGap(onlyGoal(engine.evaluate(snapshot, kb)));

		assertNull(gap.getBoostableFrom());
	}

	@Test
	void c5NonBoostableLeavesBoostableFromNull()
	{
		KnowledgeBase kb = new KbBuilder().quest(CLOCK_TOWER_ID, "Clock Tower").skill(Skill.AGILITY, 70, false).build();
		Snapshot snapshot = new SnapshotBuilder().skill(Skill.AGILITY, 66).build();

		SkillLevelGap gap = (SkillLevelGap) onlyGap(onlyGoal(engine.evaluate(snapshot, kb)));

		assertFalse(gap.isBoostable());
		assertNull(gap.getBoostableFrom());
	}

	// --- Ruling 22: ironman-only requirement. ---

	@Test
	void ruling22IronmanOnlyReqIgnoredForNormalAccount()
	{
		KnowledgeBase kb = new KbBuilder().quest(CLOCK_TOWER_ID, "Clock Tower").skill(Skill.FISHING, 40, false, true).build();
		Snapshot snapshot = new SnapshotBuilder().accountType(AccountType.NORMAL).build();

		GoalStatus status = onlyGoal(engine.evaluate(snapshot, kb));

		assertTrue(status.getGaps().isEmpty());
		assertTrue(status.isReady());
	}

	@Test
	void ruling22IronmanOnlyReqAppliedForGroupIronman()
	{
		KnowledgeBase kb = new KbBuilder().quest(CLOCK_TOWER_ID, "Clock Tower").skill(Skill.FISHING, 40, false, true).build();
		Snapshot snapshot = new SnapshotBuilder().accountType(AccountType.GROUP).build();

		SkillLevelGap gap = (SkillLevelGap) onlyGap(onlyGoal(engine.evaluate(snapshot, kb)));

		assertEquals(Skill.FISHING, gap.getSkill());
	}

	// --- Ruling 16: diary tier goal with DiaryTask gaps. ---

	@Test
	void ruling16DiaryTierWithSomeTasksCompleteYieldsOneDiaryTaskGapPerIncompleteTask()
	{
		KnowledgeBase kb = new KbBuilder()
			.diary(DiaryTier.VARROCK_EASY)
			.task(1, "Mine some tin").skill(Skill.MINING, 5).completion(1176, 0)
			.task(2, "Talk to Aggie").completion(1176, 1)
			.task(3, "Fish some shrimp").skill(Skill.FISHING, 5).note("optional note").completion(1176, 2)
			.build();
		// bit 1 set -> task 2 complete; tasks 1 and 3 incomplete.
		Snapshot snapshot = new SnapshotBuilder().diaryVarp(1176, 0b010).build();

		GoalStatus status = onlyGoal(engine.evaluate(snapshot, kb));
		assertEquals(2, status.getGaps().size());

		DiaryTaskGap task1 = diaryTaskGap(status, 1);
		DiaryTaskGap task3 = diaryTaskGap(status, 3);
		assertEquals(1, task1.getGaps().size());
		assertEquals(1, task3.getGaps().size());
		assertTrue(task3.getNotes().contains("optional note"));
	}

	// --- DiaryTaskGap quest-requirement path (task.getQuests()). ---

	@Test
	void diaryTaskQuestReqNotStartedYieldsQuestPrereqGap()
	{
		KnowledgeBase kb = new KbBuilder()
			.quest(COOKS_ASSISTANT_ID, "Cook's Assistant")
			.diary(DiaryTier.VARROCK_EASY).task(1, "Talk to the cook").quest("Cook's Assistant").completion(1176, 0)
			.build();
		Snapshot snapshot = new SnapshotBuilder().quest(Quest.COOKS_ASSISTANT, QuestState.NOT_STARTED).build();

		GoalStatus status = goalFor(engine.evaluate(snapshot, kb), "diary:VARROCK_EASY");
		DiaryTaskGap task = (DiaryTaskGap) onlyGap(status);
		QuestPrereqGap gap = (QuestPrereqGap) onlyGapOf(task);

		assertEquals(Quest.COOKS_ASSISTANT, gap.getQuest());
		assertEquals(QuestState.NOT_STARTED, gap.getState());
	}

	@Test
	void diaryTaskQuestReqInProgressYieldsQuestPrereqGap()
	{
		KnowledgeBase kb = new KbBuilder()
			.quest(COOKS_ASSISTANT_ID, "Cook's Assistant")
			.diary(DiaryTier.VARROCK_EASY).task(1, "Talk to the cook").quest("Cook's Assistant").completion(1176, 0)
			.build();
		Snapshot snapshot = new SnapshotBuilder().quest(Quest.COOKS_ASSISTANT, QuestState.IN_PROGRESS).build();

		GoalStatus status = goalFor(engine.evaluate(snapshot, kb), "diary:VARROCK_EASY");
		DiaryTaskGap task = (DiaryTaskGap) onlyGap(status);
		QuestPrereqGap gap = (QuestPrereqGap) onlyGapOf(task);

		assertEquals(Quest.COOKS_ASSISTANT, gap.getQuest());
		assertEquals(QuestState.IN_PROGRESS, gap.getState());
	}

	@Test
	void diaryTaskQuestReqFinishedYieldsNoQuestGap()
	{
		KnowledgeBase kb = new KbBuilder()
			.quest(COOKS_ASSISTANT_ID, "Cook's Assistant")
			.diary(DiaryTier.VARROCK_EASY).task(1, "Talk to the cook").quest("Cook's Assistant").completion(1176, 0)
			.build();
		Snapshot snapshot = new SnapshotBuilder().quest(Quest.COOKS_ASSISTANT, QuestState.FINISHED).build();

		GoalStatus status = goalFor(engine.evaluate(snapshot, kb), "diary:VARROCK_EASY");
		DiaryTaskGap task = (DiaryTaskGap) onlyGap(status);

		assertTrue(task.getGaps().isEmpty());
	}

	@Test
	void diaryTaskUnknownQuestNameDoesNotThrowAndAddsNoteNamingIt()
	{
		KnowledgeBase kb = new KbBuilder()
			.diary(DiaryTier.VARROCK_EASY).task(1, "Talk to someone").quest("Not A Real Quest").completion(1176, 0)
			.build();
		Snapshot snapshot = new SnapshotBuilder().build();

		GoalStatus status = onlyGoal(engine.evaluate(snapshot, kb));
		DiaryTaskGap task = (DiaryTaskGap) onlyGap(status);

		assertTrue(task.getGaps().isEmpty());
		assertTrue(task.getNotes().stream().anyMatch(n -> n.contains("Not A Real Quest")), task.getNotes().toString());
	}

	// --- Finished/completed exclusion, readiness, and determinism. ---

	@Test
	void finishedQuestsAreNotEmitted()
	{
		KnowledgeBase kb = new KbBuilder().quest(CLOCK_TOWER_ID, "Clock Tower").build();
		Snapshot snapshot = new SnapshotBuilder().quest(Quest.CLOCK_TOWER, QuestState.FINISHED).build();

		assertTrue(engine.evaluate(snapshot, kb).isEmpty());
	}

	@Test
	void completedDiaryTiersAreNotEmitted()
	{
		KnowledgeBase kb = new KbBuilder().diary(DiaryTier.VARROCK_EASY).task(1, "t").completion(1176, 0).build();
		Snapshot snapshot = new SnapshotBuilder().diaryTier(DiaryTier.VARROCK_EASY, true).build();

		assertTrue(engine.evaluate(snapshot, kb).isEmpty());
	}

	@Test
	void readyIsTrueForAQuestWithAllRequirementsMet()
	{
		KnowledgeBase kb = new KbBuilder().quest(CLOCK_TOWER_ID, "Clock Tower").skill(Skill.MINING, 10).build();
		Snapshot snapshot = new SnapshotBuilder().skill(Skill.MINING, 10).build();

		GoalStatus status = onlyGoal(engine.evaluate(snapshot, kb));

		assertTrue(status.isReady());
		assertTrue(status.getGaps().isEmpty());
	}

	@Test
	void outputIsSortedByGoalId()
	{
		KnowledgeBase kb = new KbBuilder()
			.quest(CLOCK_TOWER_ID, "Clock Tower")
			.quest(ANIMAL_MAGNETISM_ID, "Animal Magnetism")
			.build();
		Snapshot snapshot = new SnapshotBuilder().build();

		List<GoalStatus> statuses = engine.evaluate(snapshot, kb);
		List<String> ids = statuses.stream().map(s -> s.getGoal().getId()).collect(java.util.stream.Collectors.toList());

		assertEquals(ids.stream().sorted().collect(java.util.stream.Collectors.toList()), ids);
	}

	// --- helpers ---

	private static GoalStatus onlyGoal(List<GoalStatus> statuses)
	{
		assertEquals(1, statuses.size(), "expected exactly one goal: " + statuses);
		return statuses.get(0);
	}

	private static Gap onlyGap(GoalStatus status)
	{
		assertEquals(1, status.getGaps().size(), "expected exactly one gap: " + status.getGaps());
		return status.getGaps().get(0);
	}

	private static Gap onlyGapOf(DiaryTaskGap task)
	{
		assertEquals(1, task.getGaps().size(), "expected exactly one gap: " + task.getGaps());
		return task.getGaps().get(0);
	}

	private static GoalStatus goalFor(List<GoalStatus> statuses, String goalId)
	{
		return statuses.stream().filter(s -> s.getGoal().getId().equals(goalId)).findFirst()
			.orElseThrow(() -> new AssertionError("no goal " + goalId + " in " + statuses));
	}

	private static QuestPrereqGap prereqGap(GoalStatus status, Quest quest)
	{
		return status.getGaps().stream()
			.filter(g -> g instanceof QuestPrereqGap && ((QuestPrereqGap) g).getQuest() == quest)
			.map(g -> (QuestPrereqGap) g)
			.findFirst()
			.orElseGet(() -> fail("no QuestPrereqGap for " + quest + " in " + status.getGaps()));
	}

	private static DiaryTaskGap diaryTaskGap(GoalStatus status, int ordinal)
	{
		return status.getGaps().stream()
			.filter(g -> g instanceof DiaryTaskGap && ((DiaryTaskGap) g).getOrdinal() == ordinal)
			.map(g -> (DiaryTaskGap) g)
			.findFirst()
			.orElseGet(() -> fail("no DiaryTaskGap for ordinal " + ordinal + " in " + status.getGaps()));
	}
}
