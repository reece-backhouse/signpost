package dev.reece.nta.engine;

import com.google.gson.Gson;
import dev.reece.nta.engine.model.CombatLevelGap;
import dev.reece.nta.engine.model.DiaryTaskGap;
import dev.reece.nta.engine.model.Gap;
import dev.reece.nta.engine.model.GearGap;
import dev.reece.nta.engine.model.GoalStatus;
import dev.reece.nta.engine.model.ItemGap;
import dev.reece.nta.engine.model.ItemSource;
import dev.reece.nta.engine.model.KudosGap;
import dev.reece.nta.engine.model.QuestPointsGap;
import dev.reece.nta.engine.model.QuestPrereqGap;
import dev.reece.nta.engine.model.SkillLevelGap;
import dev.reece.nta.kb.KnowledgeBase;
import dev.reece.nta.kb.MilestoneCategory;
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

	/** Fix round 1: {@code wikiUrl} must come from the kb's {@code wikiTitle}, not {@code Quest.getName()} - a subquest like a Recipe for Disaster one has a wiki title the RuneLite quest name doesn't (e.g. a "Recipe for Disaster/" prefix), so building it from the name 404s. */
	@Test
	void c1QuestPrereqGapWikiUrlUsesTheKbWikiTitleNotTheQuestName()
	{
		KnowledgeBase kb = new KbBuilder()
			.quest(CLOCK_TOWER_ID, "Clock Tower").prereq("Biohazard")
			.quest(BIOHAZARD_ID, "Biohazard").wikiTitle("Recipe for Disaster/Freeing the Mountain Dwarf")
			.build();
		Snapshot snapshot = new SnapshotBuilder().quest(Quest.BIOHAZARD, QuestState.NOT_STARTED).build();

		GoalStatus status = goalFor(engine.evaluate(snapshot, kb), "quest:" + CLOCK_TOWER_ID);
		QuestPrereqGap gap = (QuestPrereqGap) onlyGap(status);

		assertEquals("https://oldschool.runescape.wiki/w/Recipe_for_Disaster/Freeing_the_Mountain_Dwarf", gap.getWikiUrl());
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

	// --- Pre-review fix: "Started:" prereqs (wiki Module:Questreq/data) and unresolved prereq notes. ---

	@Test
	void startedPrereqSatisfiedWhenQuestInProgress()
	{
		KnowledgeBase kb = new KbBuilder().quest(CLOCK_TOWER_ID, "Clock Tower").prereqStarted("Biohazard").build();
		Snapshot snapshot = new SnapshotBuilder().quest(Quest.BIOHAZARD, QuestState.IN_PROGRESS).build();

		GoalStatus status = goalFor(engine.evaluate(snapshot, kb), "quest:" + CLOCK_TOWER_ID);

		assertTrue(status.getGaps().isEmpty());
		assertTrue(status.isReady());
	}

	@Test
	void startedPrereqSatisfiedWhenQuestFinished()
	{
		KnowledgeBase kb = new KbBuilder().quest(CLOCK_TOWER_ID, "Clock Tower").prereqStarted("Biohazard").build();
		Snapshot snapshot = new SnapshotBuilder().quest(Quest.BIOHAZARD, QuestState.FINISHED).build();

		GoalStatus status = goalFor(engine.evaluate(snapshot, kb), "quest:" + CLOCK_TOWER_ID);

		assertTrue(status.getGaps().isEmpty());
	}

	@Test
	void startedPrereqNotSatisfiedWhenQuestNotStarted()
	{
		KnowledgeBase kb = new KbBuilder()
			.quest(CLOCK_TOWER_ID, "Clock Tower").prereqStarted("Biohazard")
			.quest(BIOHAZARD_ID, "Biohazard")
			.build();
		Snapshot snapshot = new SnapshotBuilder().build();

		GoalStatus status = goalFor(engine.evaluate(snapshot, kb), "quest:" + CLOCK_TOWER_ID);
		QuestPrereqGap gap = (QuestPrereqGap) onlyGap(status);

		assertEquals(Quest.BIOHAZARD, gap.getQuest());
		assertTrue(gap.isStartOnly());
		assertTrue(gap.isStartHere());
	}

	@Test
	void startedPrereqDoesNotRecurseIntoItsOwnPrereqs()
	{
		// Biohazard itself needs Cook's Assistant (a normal, finished-required prereq), but Clock
		// Tower only needs Biohazard to have been STARTED - Cook's Assistant should not appear.
		KnowledgeBase kb = new KbBuilder()
			.quest(CLOCK_TOWER_ID, "Clock Tower").prereqStarted("Biohazard")
			.quest(BIOHAZARD_ID, "Biohazard").prereq("Cook's Assistant")
			.quest(COOKS_ASSISTANT_ID, "Cook's Assistant")
			.build();
		Snapshot snapshot = new SnapshotBuilder().quest(Quest.COOKS_ASSISTANT, QuestState.NOT_STARTED).build();

		GoalStatus status = goalFor(engine.evaluate(snapshot, kb), "quest:" + CLOCK_TOWER_ID);

		assertEquals(1, status.getGaps().size());
		QuestPrereqGap gap = (QuestPrereqGap) onlyGap(status);
		assertEquals(Quest.BIOHAZARD, gap.getQuest());
		assertTrue(gap.isStartOnly());
	}

	@Test
	void prereqNotesSurfaceOnGoalStatusNotes()
	{
		KnowledgeBase kb = new KbBuilder().quest(CLOCK_TOWER_ID, "Clock Tower").prereqNote("Barbarian Firemaking").build();
		Snapshot snapshot = new SnapshotBuilder().build();

		GoalStatus status = goalFor(engine.evaluate(snapshot, kb), "quest:" + CLOCK_TOWER_ID);

		assertEquals(List.of("Barbarian Firemaking"), status.getNotes());
	}

	@Test
	void diaryAndMilestoneGoalsHaveEmptyNotes()
	{
		KnowledgeBase kb = new KbBuilder().diary(DiaryTier.VARROCK_EASY).task(1, "Mine some tin").skill(Skill.MINING, 10).build();
		Snapshot snapshot = new SnapshotBuilder().build();

		GoalStatus status = goalFor(engine.evaluate(snapshot, kb), "diary:VARROCK_EASY");

		assertTrue(status.getNotes().isEmpty());
	}

	@Test
	void evaluatingTheRealBundledKbDoesNotThrowAndReturnsManyGoals()
	{
		KnowledgeBase kb = KnowledgeBase.load(new Gson());
		Snapshot snapshot = new SnapshotBuilder().build();

		List<GoalStatus> statuses = engine.evaluate(snapshot, kb);

		assertTrue(statuses.size() > 200, "expected more than 200 goals, got " + statuses.size());
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

	// --- Task 41/42: quest/diary priority overrides and stage mapping (spec ruling 27). ---

	@Test
	void questPriorityOverrideAppliedAndDrivesStageMapping()
	{
		KnowledgeBase kb = new KbBuilder()
			.quest(CLOCK_TOWER_ID, "Clock Tower")
			.priorityOverride("quest:" + CLOCK_TOWER_ID, 9)
			.build();
		Snapshot snapshot = new SnapshotBuilder().build();

		GoalStatus status = onlyGoal(engine.evaluate(snapshot, kb));

		assertEquals(9, status.getGoal().getPriority());
		assertEquals(3, status.getGoal().getStage(), "priority >= 8 should map to stage 3");
	}

	@Test
	void questDefaultPriorityMapsToStageTwo()
	{
		KnowledgeBase kb = new KbBuilder().quest(CLOCK_TOWER_ID, "Clock Tower").build();
		Snapshot snapshot = new SnapshotBuilder().build();

		GoalStatus status = onlyGoal(engine.evaluate(snapshot, kb));

		assertEquals(5, status.getGoal().getPriority());
		assertEquals(2, status.getGoal().getStage(), "default quest priority 5 should map to stage 2");
	}

	@Test
	void questPriorityOverrideBelowFiveMapsToStageOne()
	{
		KnowledgeBase kb = new KbBuilder()
			.quest(CLOCK_TOWER_ID, "Clock Tower")
			.priorityOverride("quest:" + CLOCK_TOWER_ID, 3)
			.build();
		Snapshot snapshot = new SnapshotBuilder().build();

		GoalStatus status = onlyGoal(engine.evaluate(snapshot, kb));

		assertEquals(1, status.getGoal().getStage());
	}

	@Test
	void diaryPriorityOverrideApplied()
	{
		KnowledgeBase kb = new KbBuilder()
			.diary(DiaryTier.VARROCK_EASY).task(1, "t").completion(1176, 0)
			.priorityOverride("diary:VARROCK_EASY", 9)
			.build();
		Snapshot snapshot = new SnapshotBuilder().build();

		GoalStatus status = goalFor(engine.evaluate(snapshot, kb), "diary:VARROCK_EASY");

		assertEquals(9, status.getGoal().getPriority());
	}

	@Test
	void diaryStageDerivedFromTier()
	{
		KnowledgeBase kb = new KbBuilder()
			.diary(DiaryTier.VARROCK_EASY).task(1, "t").completion(1176, 0)
			.diary(DiaryTier.VARROCK_MEDIUM).task(1, "t").completion(1177, 0)
			.diary(DiaryTier.VARROCK_HARD).task(1, "t").completion(1178, 0)
			.diary(DiaryTier.VARROCK_ELITE).task(1, "t").completion(1179, 0)
			.build();
		Snapshot snapshot = new SnapshotBuilder().build();

		List<GoalStatus> statuses = engine.evaluate(snapshot, kb);

		assertEquals(1, goalFor(statuses, "diary:VARROCK_EASY").getGoal().getStage());
		assertEquals(2, goalFor(statuses, "diary:VARROCK_MEDIUM").getGoal().getStage());
		assertEquals(3, goalFor(statuses, "diary:VARROCK_HARD").getGoal().getStage());
		assertEquals(4, goalFor(statuses, "diary:VARROCK_ELITE").getGoal().getStage());
	}

	@Test
	void milestoneStageComesFromTheKbEntry()
	{
		KnowledgeBase kb = new KbBuilder()
			.milestone("milestone:test", MilestoneCategory.UNLOCK, "Test", 5).stage(3)
			.skill(Skill.AGILITY, 40)
			.build();
		Snapshot snapshot = new SnapshotBuilder().build();

		GoalStatus status = goalFor(engine.evaluate(snapshot, kb), "milestone:test");

		assertEquals(3, status.getGoal().getStage());
	}

	// --- Task 41: recommended-profile gaps (spec ruling 27). ---

	@Test
	void recommendedSkillGapAddedWhenBelowRecommendedLevel()
	{
		KnowledgeBase kb = new KbBuilder()
			.milestone("boss:test", MilestoneCategory.BOSS, "Test Boss", 5)
			.recommendedSkill(Skill.RANGED, 80)
			.build();
		Snapshot snapshot = new SnapshotBuilder().skill(Skill.RANGED, 70).build();

		GoalStatus status = goalFor(engine.evaluate(snapshot, kb), "boss:test");
		SkillLevelGap gap = (SkillLevelGap) onlyGap(status);

		assertEquals(Skill.RANGED, gap.getSkill());
		assertEquals(70, gap.getHave());
		assertEquals(80, gap.getNeed());
		assertTrue(gap.isRecommended());
		assertFalse(status.isReady());
	}

	@Test
	void recommendedCombatGapAddedWhenBelowRecommendedCombatLevel()
	{
		KnowledgeBase kb = new KbBuilder()
			.milestone("boss:test", MilestoneCategory.BOSS, "Test Boss", 5)
			.recommendedCombat(100)
			.build();
		Snapshot snapshot = new SnapshotBuilder().build();

		GoalStatus status = goalFor(engine.evaluate(snapshot, kb), "boss:test");
		CombatLevelGap gap = (CombatLevelGap) onlyGap(status);

		assertEquals(100, gap.getNeed());
		assertTrue(gap.isRecommended());
	}

	@Test
	void recommendedGearGapAddedWhenNoneOfTheAcceptableItemsOwned()
	{
		KnowledgeBase kb = new KbBuilder()
			.milestone("boss:test", MilestoneCategory.BOSS, "Test Boss", 5)
			.recommendedGear("Bandos chestplate", 11832)
			.build();
		Snapshot snapshot = new SnapshotBuilder().build();

		GoalStatus status = goalFor(engine.evaluate(snapshot, kb), "boss:test");
		GearGap gap = (GearGap) onlyGap(status);

		assertEquals(1, gap.getAcceptable().size());
		assertEquals("Bandos chestplate", gap.getAcceptable().get(0).getName());
		assertEquals(0, gap.getOwned());
		assertEquals(1, gap.getRequired(), "single-item list: default gearOwnedMin is ceil(1/2) = 1");
		assertFalse(gap.isBankUnknown());
	}

	@Test
	void recommendedGearGapAbsentWhenAnAcceptableItemIsOwned()
	{
		KnowledgeBase kb = new KbBuilder()
			.milestone("boss:test", MilestoneCategory.BOSS, "Test Boss", 5)
			.recommendedGear("Bandos chestplate", 11832)
			.build();
		Snapshot snapshot = new SnapshotBuilder().inventoryItem(11832, "Bandos chestplate", 1).build();

		GoalStatus status = goalFor(engine.evaluate(snapshot, kb), "boss:test");

		assertTrue(status.getGaps().isEmpty());
		assertTrue(status.isReady());
	}

	@Test
	void recommendedGearGapFlagsBankUnknownWhenBankUnseen()
	{
		KnowledgeBase kb = new KbBuilder()
			.milestone("boss:test", MilestoneCategory.BOSS, "Test Boss", 5)
			.recommendedGear("Bandos chestplate", 11832)
			.build();
		Snapshot snapshot = new SnapshotBuilder().bankUnknown().build();

		GoalStatus status = goalFor(engine.evaluate(snapshot, kb), "boss:test");
		GearGap gap = (GearGap) onlyGap(status);

		assertTrue(gap.isBankUnknown(), "the unseen bank could still hold the 1 item needed to satisfy this list");
		assertTrue(status.isBankUnknown());
	}

	// --- Task 46: gearOwnedAny is satisfied only once effectiveGearOwnedMin distinct items are owned. ---

	@Test
	void gearOwnedMinDefaultsToHalfTheListRoundedUp()
	{
		KnowledgeBase kb = new KbBuilder()
			.milestone("boss:test", MilestoneCategory.BOSS, "Test Boss", 5)
			.recommendedGear("Item A", 1).recommendedGear("Item B", 2).recommendedGear("Item C", 3)
			.build();
		Snapshot snapshot = new SnapshotBuilder().inventoryItem(1, "Item A", 1).build();

		GoalStatus status = goalFor(engine.evaluate(snapshot, kb), "boss:test");
		GearGap gap = (GearGap) onlyGap(status);

		assertEquals(1, gap.getOwned());
		assertEquals(2, gap.getRequired(), "3-item list with no explicit gearOwnedMin: ceil(3/2) = 2");
	}

	@Test
	void gearOwnedMinExplicitOverridesTheDefault()
	{
		KnowledgeBase kb = new KbBuilder()
			.milestone("boss:test", MilestoneCategory.BOSS, "Test Boss", 5)
			.recommendedGear("Item A", 1).recommendedGear("Item B", 2).recommendedGear("Item C", 3).recommendedGear("Item D", 4)
			.recommendedGearOwnedMin(1)
			.build();
		Snapshot snapshot = new SnapshotBuilder().inventoryItem(1, "Item A", 1).build();

		GoalStatus status = goalFor(engine.evaluate(snapshot, kb), "boss:test");

		assertTrue(status.getGaps().isEmpty(),
			"1 of 4 owned meets an explicit gearOwnedMin of 1 (default would have been ceil(4/2) = 2): " + status.getGaps());
	}

	@Test
	void gearOwnedMinSatisfiedOnceEnoughDistinctItemsAreOwned()
	{
		KnowledgeBase kb = new KbBuilder()
			.milestone("boss:test", MilestoneCategory.BOSS, "Test Boss", 5)
			.recommendedGear("Item A", 1).recommendedGear("Item B", 2).recommendedGear("Item C", 3)
			.build();
		Snapshot snapshot = new SnapshotBuilder().inventoryItem(1, "Item A", 1).inventoryItem(2, "Item B", 1).build();

		GoalStatus status = goalFor(engine.evaluate(snapshot, kb), "boss:test");

		assertTrue(status.getGaps().isEmpty(), "2 of 3 owned meets the default gearOwnedMin of ceil(3/2) = 2: " + status.getGaps());
	}

	@Test
	void gearOwnedGapBankUnknownTrueWhenTheUnseenBankCouldStillReachTheMinimum()
	{
		KnowledgeBase kb = new KbBuilder()
			.milestone("boss:test", MilestoneCategory.BOSS, "Test Boss", 5)
			.recommendedGear("Item A", 1).recommendedGear("Item B", 2)
			.recommendedGearOwnedMin(2)
			.build();
		Snapshot snapshot = new SnapshotBuilder().bankUnknown().build();

		GoalStatus status = goalFor(engine.evaluate(snapshot, kb), "boss:test");
		GearGap gap = (GearGap) onlyGap(status);

		assertEquals(0, gap.getOwned());
		assertEquals(2, gap.getRequired());
		assertTrue(gap.isBankUnknown(), "both items are unseen-bank-uncertain, and 0 + 2 uncertain reaches the required 2");
	}

	@Test
	void gearOwnedGapBankUnknownFalseWhenEvenTheUnseenBankCouldNotReachTheMinimum()
	{
		// required (3) exceeds the 2-item list's size, so even crediting every unresolved item to the
		// unseen bank (max possible = 2) can never reach it - a confirmed shortfall despite the bank
		// being unseen.
		KnowledgeBase kb = new KbBuilder()
			.milestone("boss:test", MilestoneCategory.BOSS, "Test Boss", 5)
			.recommendedGear("Item A", 1).recommendedGear("Item B", 2)
			.recommendedGearOwnedMin(3)
			.build();
		Snapshot snapshot = new SnapshotBuilder().bankUnknown().build();

		GoalStatus status = goalFor(engine.evaluate(snapshot, kb), "boss:test");
		GearGap gap = (GearGap) onlyGap(status);

		assertEquals(0, gap.getOwned());
		assertEquals(3, gap.getRequired());
		assertFalse(gap.isBankUnknown(), "0 + 2 uncertain can never reach the required 3");
	}

	@Test
	void bossReadyOnlyWhenEntryAndRecommendedAreBothMet()
	{
		KnowledgeBase kb = new KbBuilder()
			.milestone("boss:test", MilestoneCategory.BOSS, "Test Boss", 5)
			.skill(Skill.STRENGTH, 60)
			.recommendedSkill(Skill.RANGED, 80)
			.build();

		Snapshot entryOnlyMet = new SnapshotBuilder().skill(Skill.STRENGTH, 60).skill(Skill.RANGED, 50).build();
		GoalStatus entryOnlyStatus = goalFor(engine.evaluate(entryOnlyMet, kb), "boss:test");
		assertFalse(entryOnlyStatus.isReady(), "entry met but recommended unmet should not be ready");

		Snapshot bothMet = new SnapshotBuilder().skill(Skill.STRENGTH, 60).skill(Skill.RANGED, 80).build();
		GoalStatus bothMetStatus = goalFor(engine.evaluate(bothMet, kb), "boss:test");
		assertTrue(bothMetStatus.isReady());
	}

	// --- Task 41: diary game-count trust (live self-check: DESERT_MEDIUM kb=11 game=12). ---

	@Test
	void diaryGameCountEqualToTotalSuppressesAllTaskGapsAndTierIsReady()
	{
		KnowledgeBase kb = new KbBuilder()
			.diary(DiaryTier.VARROCK_EASY)
			.task(1, "t1").completion(1176, 0)
			.task(2, "t2").completion(1176, 1)
			.build();
		Snapshot snapshot = new SnapshotBuilder()
			.diaryVarp(1176, 0) // both tasks look incomplete by bit map...
			.diaryCountVarbit(DiaryProgress.COUNT_VARBITS.get(DiaryTier.VARROCK_EASY), 2) // ...but the game says both are done.
			.build();

		GoalStatus status = goalFor(engine.evaluate(snapshot, kb), "diary:VARROCK_EASY");

		assertTrue(status.getGaps().isEmpty(), "game-reported completion should suppress every task gap: " + status.getGaps());
		assertTrue(status.isReady());
	}

	@Test
	void diaryGameCountGreaterThanKbCompletedKeepsTaskGapsAndAddsANote()
	{
		KnowledgeBase kb = new KbBuilder()
			.diary(DiaryTier.VARROCK_EASY)
			.task(1, "t1").completion(1176, 0)
			.task(2, "t2").completion(1176, 1)
			.task(3, "t3").completion(1176, 2)
			.build();
		Snapshot snapshot = new SnapshotBuilder()
			.diaryVarp(1176, 0b001) // kb sees only task 1 complete.
			.diaryCountVarbit(DiaryProgress.COUNT_VARBITS.get(DiaryTier.VARROCK_EASY), 2) // game says 2 done.
			.build();

		GoalStatus status = goalFor(engine.evaluate(snapshot, kb), "diary:VARROCK_EASY");

		assertEquals(2, status.getGaps().size(), "kb-derived task gaps for 2 and 3 must be kept, not shrunk: " + status.getGaps());
		assertTrue(status.getNotes().stream().anyMatch(n -> n.contains("2/3")), status.getNotes().toString());
	}

	@Test
	void diaryGameCountMatchingKbCompletedBehavesNormallyWithNoNote()
	{
		KnowledgeBase kb = new KbBuilder()
			.diary(DiaryTier.VARROCK_EASY)
			.task(1, "t1").completion(1176, 0)
			.task(2, "t2").completion(1176, 1)
			.build();
		Snapshot snapshot = new SnapshotBuilder()
			.diaryVarp(1176, 0b01) // task 1 complete.
			.diaryCountVarbit(DiaryProgress.COUNT_VARBITS.get(DiaryTier.VARROCK_EASY), 1) // game agrees: 1 done.
			.build();

		GoalStatus status = goalFor(engine.evaluate(snapshot, kb), "diary:VARROCK_EASY");

		assertEquals(1, status.getGaps().size());
		assertTrue(status.getNotes().isEmpty());
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
