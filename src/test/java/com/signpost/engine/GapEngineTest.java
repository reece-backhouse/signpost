package com.signpost.engine;

import com.google.gson.Gson;
import com.signpost.engine.model.CombatLevelGap;
import com.signpost.engine.model.DiaryTaskGap;
import com.signpost.engine.model.Gap;
import com.signpost.engine.model.GearGap;
import com.signpost.engine.model.GoalStatus;
import com.signpost.engine.model.ItemGap;
import com.signpost.engine.model.ItemSource;
import com.signpost.engine.model.KudosGap;
import com.signpost.engine.model.QuestPointsGap;
import com.signpost.engine.model.QuestPrereqGap;
import com.signpost.engine.model.SkillLevelGap;
import com.signpost.kb.KnowledgeBase;
import com.signpost.kb.MilestoneCategory;
import com.signpost.kb.OwnedItem;
import com.signpost.snapshot.AccountType;
import com.signpost.snapshot.DiaryTier;
import com.signpost.snapshot.Snapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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

	// --- each gap type is produced once by a fitting scenario. ---

	@Test
	void skillLevelGapProducedWhenSkillBelowRequirement()
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
	void questPrereqGapProducedForUnfinishedPrereq()
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

	/** {@code wikiUrl} must come from the kb's {@code wikiTitle}, not {@code Quest.getName()} - a subquest like a Recipe for Disaster one has a wiki title the RuneLite quest name doesn't (e.g. a "Recipe for Disaster/" prefix), so building it from the name 404s. */
	@Test
	void questPrereqGapWikiUrlUsesTheKbWikiTitleNotTheQuestName()
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
	void itemGapProducedWhenBankShort()
	{
		KnowledgeBase kb = new KbBuilder().quest(CLOCK_TOWER_ID, "Clock Tower").item("Bucket", 2).build();
		Snapshot snapshot = new SnapshotBuilder().bankItem(1, "Bucket", 1).build();

		GoalStatus status = onlyGoal(engine.evaluate(snapshot, kb));
		ItemGap gap = (ItemGap) onlyGap(status);

		assertEquals("Bucket", gap.getName());
		assertEquals(1, gap.getHave());
		assertEquals(2, gap.getNeed());
	}

	/** A generic requirement name ("Pickaxe") is not an in-game item; it must be a note, never a blocking gap. */
	@Test
	void genericQuestItemNameBecomesANoteNotAGap()
	{
		KnowledgeBase kb = new KbBuilder().quest(CLOCK_TOWER_ID, "Clock Tower").item("Pickaxe", 1).material("Pickaxe", null).build();
		Snapshot snapshot = new SnapshotBuilder().build();

		GoalStatus status = onlyGoal(engine.evaluate(snapshot, kb));

		assertTrue(status.isReady(), "a generic item must not block readiness: " + status.getGaps());
		assertEquals(List.of("Bring: Pickaxe (see wiki)"), status.getNotes());
	}

	@Test
	void genericDiaryItemNameBecomesATaskNoteNotAGap()
	{
		KnowledgeBase kb = new KbBuilder().diary(DiaryTier.VARROCK_EASY).task(1, "Mine some tin").item("pickaxe").material("pickaxe", null).build();
		Snapshot snapshot = new SnapshotBuilder().build();

		DiaryTaskGap task = (DiaryTaskGap) onlyGap(onlyGoal(engine.evaluate(snapshot, kb)));

		assertEquals(List.of(), task.getGaps());
		assertTrue(task.getNotes().contains("Bring: pickaxe (see wiki)"), task.getNotes().toString());
	}

	@Test
	void itemGapCarriesTheMaterialsWikiUrl()
	{
		KnowledgeBase kb = new KbBuilder().quest(CLOCK_TOWER_ID, "Clock Tower").item("Bucket", 2).material("Bucket", 1925).build();
		Snapshot snapshot = new SnapshotBuilder().build();

		ItemGap gap = (ItemGap) onlyGap(onlyGoal(engine.evaluate(snapshot, kb)));

		assertEquals("https://oldschool.runescape.wiki/w/Bucket", gap.getWikiUrl());
	}

	/** A name-only quest item matched against a materials entry gets that entry's item id, so the UI can show its icon. */
	@Test
	void itemGapCarriesTheMaterialsItemId()
	{
		KnowledgeBase kb = new KbBuilder().quest(CLOCK_TOWER_ID, "Clock Tower").item("Steel full helm", 1).material("Steel full helm", 1157).build();
		Snapshot snapshot = new SnapshotBuilder().build();

		ItemGap gap = (ItemGap) onlyGap(onlyGoal(engine.evaluate(snapshot, kb)));

		assertEquals(1157, gap.getItemId());
	}

	/** A quest item name with no materials entry at all keeps a null item id, no icon. */
	@Test
	void itemGapItemIdIsNullWhenNoMaterialEntryMatchesTheName()
	{
		KnowledgeBase kb = new KbBuilder().quest(CLOCK_TOWER_ID, "Clock Tower").item("Mystery trinket", 1).build();
		Snapshot snapshot = new SnapshotBuilder().build();

		ItemGap gap = (ItemGap) onlyGap(onlyGoal(engine.evaluate(snapshot, kb)));

		assertNull(gap.getItemId());
	}

	/** On the bundled data, no quest or diary item requirement resolves to a generic material as an ItemGap. */
	@Test
	void noBundledGoalHasAnItemGapForAGenericName()
	{
		KnowledgeBase kb = KnowledgeBase.load(new Gson());
		Snapshot snapshot = new SnapshotBuilder().build();

		List<String> offenders = new ArrayList<>();
		for (GoalStatus status : engine.evaluate(snapshot, kb))
		{
			collectGenericItemGaps(status.getGoal().getName(), status.getGaps(), kb, offenders);
		}

		assertEquals(List.of(), offenders);
	}

	private static void collectGenericItemGaps(String goal, List<Gap> gaps, KnowledgeBase kb, List<String> offenders)
	{
		for (Gap gap : gaps)
		{
			if (gap instanceof DiaryTaskGap)
			{
				collectGenericItemGaps(goal, ((DiaryTaskGap) gap).getGaps(), kb, offenders);
			}
			else if (gap instanceof ItemGap)
			{
				com.signpost.kb.MaterialEntry material = kb.materialByName(((ItemGap) gap).getName());
				if (material == null || material.isGeneric())
				{
					offenders.add(goal + ": " + ((ItemGap) gap).getName() + (material == null ? " (no material entry)" : " (generic)"));
				}
			}
		}
	}

	@Test
	void diaryTaskGapProducedForIncompleteTask()
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
	void questPointsGapProducedWhenShort()
	{
		KnowledgeBase kb = new KbBuilder().quest(CLOCK_TOWER_ID, "Clock Tower").questPoints(10).build();
		Snapshot snapshot = new SnapshotBuilder().questPoints(3).build();

		GoalStatus status = onlyGoal(engine.evaluate(snapshot, kb));
		QuestPointsGap gap = (QuestPointsGap) onlyGap(status);

		assertEquals(3, gap.getHave());
		assertEquals(10, gap.getNeed());
	}

	@Test
	void kudosGapProducedWhenShort()
	{
		KnowledgeBase kb = new KbBuilder().quest(CLOCK_TOWER_ID, "Clock Tower").kudos(50).build();
		Snapshot snapshot = new SnapshotBuilder().kudos(10).build();

		GoalStatus status = onlyGoal(engine.evaluate(snapshot, kb));
		KudosGap gap = (KudosGap) onlyGap(status);

		assertEquals(10, gap.getHave());
		assertEquals(50, gap.getNeed());
	}

	@Test
	void combatLevelGapProducedWhenShort()
	{
		KnowledgeBase kb = new KbBuilder().quest(CLOCK_TOWER_ID, "Clock Tower").combat(40).build();
		Snapshot snapshot = new SnapshotBuilder().build();

		GoalStatus status = onlyGoal(engine.evaluate(snapshot, kb));
		CombatLevelGap gap = (CombatLevelGap) onlyGap(status);

		assertEquals(snapshot.combatLevel(), gap.getHave());
		assertEquals(40, gap.getNeed());
	}

	// --- transitive chain, deepest incomplete flagged. ---

	@Test
	void transitiveChainAllUnfinishedFlagsOnlyTheDeepest()
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
	void transitiveChainFinishedRootLeavesOnlyTheParentFlagged()
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
	void unknownPrereqNameThrowsNamingQuestAndPrereq()
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

	@Test
	void completionPrerequisiteSupersedesEarlierStartedOnlyPathAndKeepsItsAncestors()
	{
		KnowledgeBase kb = new KbBuilder()
			.quest(CLOCK_TOWER_ID, "Clock Tower").prereq("Biohazard").prereq("Cook's Assistant")
			.quest(BIOHAZARD_ID, "Biohazard").prereqStarted("Cook's Assistant")
			.quest(COOKS_ASSISTANT_ID, "Cook's Assistant").prereq("Waterfall Quest")
			.quest(158, "Waterfall Quest")
			.build();
		GoalStatus status = goalFor(engine.evaluate(new SnapshotBuilder().build(), kb), "quest:" + CLOCK_TOWER_ID);
		assertFalse(prereqGap(status, Quest.COOKS_ASSISTANT).isStartOnly());
		assertFalse(prereqGap(status, Quest.COOKS_ASSISTANT).isStartHere());
		assertFalse(prereqGap(status, Quest.BIOHAZARD).isStartHere());
		assertTrue(prereqGap(status, Quest.WATERFALL_QUEST).isStartHere());
	}

	// --- "Started:" prereqs (wiki Module:Questreq/data) and unresolved prereq notes. ---

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

	// --- have sums bank + inventory + equipment across ids sharing the name; unknown bank. ---

	@Test
	void haveSumsAcrossBankInventoryAndEquipmentByName()
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
	void unknownBankGivesNullHaveAndGoalNeverReady()
	{
		KnowledgeBase kb = new KbBuilder().quest(CLOCK_TOWER_ID, "Clock Tower").item("Bucket", 1).build();
		Snapshot snapshot = new SnapshotBuilder().bankUnknown().build();

		GoalStatus status = onlyGoal(engine.evaluate(snapshot, kb));
		ItemGap gap = (ItemGap) onlyGap(status);

		assertNull(gap.getHave());
		assertTrue(status.isBankUnknown());
		assertFalse(status.isReady());
	}

	// --- sourcesFor / mustObtain. ---

	@Test
	void sourcesForDropsGeForEveryIronTypeAndKeepsItForNormal()
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
	void mustObtainTrueWhenOnlyGeSourceExistedForAnIron()
	{
		List<ItemSource> geOnly = List.of(new ItemSource("GE", "Grand Exchange", null));

		assertTrue(GapEngine.mustObtain(geOnly, AccountType.IRONMAN));
		assertFalse(GapEngine.mustObtain(geOnly, AccountType.NORMAL));
		assertFalse(GapEngine.mustObtain(List.of(), AccountType.IRONMAN), "no sources at all is not \"must obtain\"");
	}

	// --- Boostable skill requirements. ---

	@Test
	void boostableWithinMaxBoostSetsBoostableFrom()
	{
		// AGILITY max boost is 5; requirement 70, have 66 -> boostable from 65.
		KnowledgeBase kb = new KbBuilder().quest(CLOCK_TOWER_ID, "Clock Tower").skill(Skill.AGILITY, 70, true).build();
		Snapshot snapshot = new SnapshotBuilder().skill(Skill.AGILITY, 66).build();

		SkillLevelGap gap = (SkillLevelGap) onlyGap(onlyGoal(engine.evaluate(snapshot, kb)));

		assertEquals(65, gap.getBoostableFrom());
	}

	@Test
	void boostableOutsideMaxBoostLeavesBoostableFromNull()
	{
		KnowledgeBase kb = new KbBuilder().quest(CLOCK_TOWER_ID, "Clock Tower").skill(Skill.AGILITY, 70, true).build();
		Snapshot snapshot = new SnapshotBuilder().skill(Skill.AGILITY, 60).build();

		SkillLevelGap gap = (SkillLevelGap) onlyGap(onlyGoal(engine.evaluate(snapshot, kb)));

		assertNull(gap.getBoostableFrom());
	}

	@Test
	void nonBoostableLeavesBoostableFromNull()
	{
		KnowledgeBase kb = new KbBuilder().quest(CLOCK_TOWER_ID, "Clock Tower").skill(Skill.AGILITY, 70, false).build();
		Snapshot snapshot = new SnapshotBuilder().skill(Skill.AGILITY, 66).build();

		SkillLevelGap gap = (SkillLevelGap) onlyGap(onlyGoal(engine.evaluate(snapshot, kb)));

		assertFalse(gap.isBoostable());
		assertNull(gap.getBoostableFrom());
	}

	// --- Ironman-only requirement. ---

	@Test
	void ironmanOnlyReqIgnoredForNormalAccount()
	{
		KnowledgeBase kb = new KbBuilder().quest(CLOCK_TOWER_ID, "Clock Tower").skill(Skill.FISHING, 40, false, true).build();
		Snapshot snapshot = new SnapshotBuilder().accountType(AccountType.NORMAL).build();

		GoalStatus status = onlyGoal(engine.evaluate(snapshot, kb));

		assertTrue(status.getGaps().isEmpty());
		assertTrue(status.isReady());
	}

	@Test
	void ironmanOnlyReqAppliedForGroupIronman()
	{
		KnowledgeBase kb = new KbBuilder().quest(CLOCK_TOWER_ID, "Clock Tower").skill(Skill.FISHING, 40, false, true).build();
		Snapshot snapshot = new SnapshotBuilder().accountType(AccountType.GROUP).build();

		SkillLevelGap gap = (SkillLevelGap) onlyGap(onlyGoal(engine.evaluate(snapshot, kb)));

		assertEquals(Skill.FISHING, gap.getSkill());
	}

	// --- Diary tier goal with DiaryTask gaps. ---

	@Test
	void diaryTierWithSomeTasksCompleteYieldsOneDiaryTaskGapPerIncompleteTask()
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

	// --- Quest/diary priority overrides and stage mapping. ---

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

	// --- Recommended-profile gaps. ---

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
			.recommendedGearGroup("melee-body", new OwnedItem("Bandos chestplate", 11832))
			.build();
		Snapshot snapshot = new SnapshotBuilder().build();

		GoalStatus status = goalFor(engine.evaluate(snapshot, kb), "boss:test");
		GearGap gap = (GearGap) onlyGap(status);

		assertEquals(1, gap.getAcceptable().size());
		assertEquals("Bandos chestplate", gap.getAcceptable().get(0).getName());
		assertEquals("melee-body", gap.getRole());
		assertFalse(gap.isBankUnknown());
	}

	@Test
	void recommendedGearGapAbsentWhenAnAcceptableItemIsOwned()
	{
		KnowledgeBase kb = new KbBuilder()
			.milestone("boss:test", MilestoneCategory.BOSS, "Test Boss", 5)
			.recommendedGearGroup("melee-body", new OwnedItem("Bandos chestplate", 11832))
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
			.recommendedGearGroup("melee-body", new OwnedItem("Bandos chestplate", 11832))
			.build();
		Snapshot snapshot = new SnapshotBuilder().bankUnknown().build();

		GoalStatus status = goalFor(engine.evaluate(snapshot, kb), "boss:test");
		GearGap gap = (GearGap) onlyGap(status);

		assertTrue(gap.isBankUnknown(), "the unseen bank could still hold the 1 item needed to satisfy this list");
		assertTrue(status.isBankUnknown());
	}

	@Test
	void spareCopiesCannotFillOtherCombatRoles()
	{
		KnowledgeBase kb = new KbBuilder()
			.milestone("boss:test", MilestoneCategory.BOSS, "Test Boss", 5)
			.recommendedGearGroup("melee-weapon", new OwnedItem("Melee weapon", 1))
			.recommendedGearGroup("ranged-weapon", new OwnedItem("Ranged weapon", 2))
			.recommendedGearGroup("magic-weapon", new OwnedItem("Magic weapon", 3))
			.build();
		Snapshot snapshot = new SnapshotBuilder().inventoryItem(1, "Melee weapon", 20).build();

		GoalStatus status = goalFor(engine.evaluate(snapshot, kb), "boss:test");
		assertFalse(status.isReady());
		assertEquals(Set.of("ranged-weapon", "magic-weapon"), status.getGaps().stream()
			.map(GearGap.class::cast).map(GearGap::getRole).collect(java.util.stream.Collectors.toSet()));
	}

	@Test
	void eachRoleAcceptsAnyOneAlternative()
	{
		KnowledgeBase kb = new KbBuilder()
			.milestone("boss:test", MilestoneCategory.BOSS, "Test Boss", 5)
			.recommendedGearGroup("melee-weapon", new OwnedItem("Weapon A", 1), new OwnedItem("Weapon B", 2))
			.recommendedGearGroup("ranged-weapon", new OwnedItem("Bow", 3))
			.build();
		Snapshot snapshot = new SnapshotBuilder().inventoryItem(2, "Weapon B", 1).inventoryItem(3, "Bow", 1).build();
		assertTrue(goalFor(engine.evaluate(snapshot, kb), "boss:test").isReady());
	}

	@Test
	void partialBankSeparatesConfirmedMissingRolesFromUnknownAlternatives()
	{
		KnowledgeBase kb = new KbBuilder()
			.milestone("boss:test", MilestoneCategory.BOSS, "Test Boss", 5)
			.recommendedGearGroup("melee-weapon", new OwnedItem("Sword", 1))
			.recommendedGearGroup("ranged-weapon", new OwnedItem("Bow A", 2), new OwnedItem("Bow B", 3))
			.recommendedGearGroup("magic-weapon", new OwnedItem("Staff", 4))
			.build();
		Snapshot snapshot = new SnapshotBuilder().bankUnknown().bankItem(1, "Sword", 1).build()
			.toBuilder().bankKnown(false).confirmedAbsentItems(Set.of(2, 4)).build();
		GoalStatus status = goalFor(engine.evaluate(snapshot, kb), "boss:test");
		List<GearGap> missing = status.getGaps().stream().map(GearGap.class::cast).collect(java.util.stream.Collectors.toList());
		assertEquals(2, missing.size());
		assertTrue(missing.stream().filter(g -> g.getRole().equals("ranged-weapon")).findFirst().orElseThrow().isBankUnknown());
		assertFalse(missing.stream().filter(g -> g.getRole().equals("magic-weapon")).findFirst().orElseThrow().isBankUnknown());
		assertFalse(status.isReady());
		GoalStatus allAbsent = goalFor(engine.evaluate(snapshot.toBuilder().confirmedAbsentItems(Set.of(2, 3, 4)).build(), kb), "boss:test");
		assertFalse(allAbsent.isBankUnknown());
	}

	@Test
	void replacementLoadoutIsReadyAndNamesActualEquipmentEvenWithUnknownRewardOwnership()
	{
		KnowledgeBase kb = new KbBuilder().milestone("boss:test", MilestoneCategory.BOSS, "Boss", 5)
			.recommendedGearGroup("melee-body", new OwnedItem("Fighter torso", 10551))
			.recommendedGearGroup("melee-feet", new OwnedItem("Dragon boots", 11840))
			.recommendedGearGroup("magic-feet", new OwnedItem("Infinity boots", 6920))
			.bossReward("Reward", 999).build();
		Snapshot snapshot = new SnapshotBuilder().bankItem(11832, "Bandos chestplate", 1)
			.equipmentItem(13239, "Primordial boots", 1).bankItem(13235, "Eternal boots", 1)
			.build().toBuilder().bankKnown(false).build();
		GearComparison comparison = new GearComparison(snapshot, KnowledgeBase.load(new Gson()), Set.of());
		GoalStatus boss = goalFor(engine.evaluate(snapshot, kb, DiaryProgress.compute(snapshot, kb), Set.of(), comparison), "boss:test");
		assertTrue(boss.isReady());
		assertFalse(boss.isBankUnknown());
		assertTrue(boss.getObjective().isBankUnknown());
		assertTrue(boss.getMet().stream().anyMatch(m -> m.getLabel().contains("Primordial boots")));
		assertFalse(boss.getMet().stream().anyMatch(m -> m.getLabel().contains("Dragon boots")));
	}

	@Test
	void meleeSpecialistGlovesCannotFillRangedOrMagicRoles()
	{
		KnowledgeBase kb = new KbBuilder().milestone("boss:test", MilestoneCategory.BOSS, "Boss", 5)
			.recommendedGearGroup("melee-hands", new OwnedItem("Barrows gloves", 7462))
			.recommendedGearGroup("ranged-hands", new OwnedItem("Barrows gloves", 7462))
			.recommendedGearGroup("magic-hands", new OwnedItem("Barrows gloves", 7462)).build();
		KnowledgeBase catalog = KnowledgeBase.load(new Gson());
		Snapshot specialist = new SnapshotBuilder().bankItem(22981, "Ferocious gloves", 1).build();
		GoalStatus missing = goalFor(engine.evaluate(specialist, kb, DiaryProgress.compute(specialist, kb), Set.of(),
			new GearComparison(specialist, catalog, Set.of())), "boss:test");
		assertFalse(missing.isReady());
		assertEquals(Set.of("ranged-hands", "magic-hands"), missing.getGaps().stream().map(GearGap.class::cast)
			.map(GearGap::getRole).collect(java.util.stream.Collectors.toSet()));
		Snapshot hybrid = specialist.toBuilder().inventory(java.util.Map.of(7462, 1)).build();
		assertTrue(goalFor(engine.evaluate(hybrid, kb, DiaryProgress.compute(hybrid, kb), Set.of(),
			new GearComparison(hybrid, catalog, Set.of())), "boss:test").isReady());
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

	// --- Diary game-count trust when the KB and game counts disagree. ---

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
