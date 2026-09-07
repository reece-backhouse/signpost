package dev.reece.nta.kb;

import com.google.gson.Gson;
import dev.reece.nta.snapshot.DiaryTier;
import java.util.List;
import net.runelite.api.Quest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeBaseTest
{
	private static final String EMPTY_QUESTS_JSON = "{\"version\":1,\"generatedAt\":\"x\",\"quests\":[]}";
	private static final String EMPTY_DIARIES_JSON = "{\"version\":1,\"generatedAt\":\"x\",\"diaries\":[]}";
	private static final String EMPTY_MILESTONES_JSON = "{\"version\":1,\"milestones\":[]}";
	private static final String EMPTY_PRIORITIES_JSON = "{\"version\":1,\"overrides\":{}}";

	@Test
	void nullSkillsListOnAQuestFailsLoudlyNamingTheQuestAndField()
	{
		String questsJson = "{\"version\":1,\"generatedAt\":\"x\",\"quests\":[{\"id\":0,\"name\":\"Test Quest\","
			+ "\"wikiTitle\":\"Test Quest\",\"skills\":null,\"prereqs\":[],\"items\":[],\"questPoints\":1,\"source\":\"test\"}]}";

		IllegalStateException e = assertThrows(IllegalStateException.class,
			() -> KnowledgeBase.fromJson(new Gson(), questsJson, EMPTY_DIARIES_JSON, EMPTY_MILESTONES_JSON, EMPTY_PRIORITIES_JSON));

		assertTrue(e.getMessage().contains("Test Quest"), e.getMessage());
		assertTrue(e.getMessage().contains("skills"), e.getMessage());
	}

	@Test
	void malformedCompletionOnADiaryTaskFailsLoudlyNamingAreaTierAndOrdinal()
	{
		String diariesJson = "{\"version\":1,\"generatedAt\":\"x\",\"diaries\":[{\"area\":\"VARROCK\",\"tier\":\"EASY\","
			+ "\"tierVarbit\":0,\"tasks\":[{\"ordinal\":1,\"text\":\"t\",\"skills\":[],\"quests\":[],\"items\":[],"
			+ "\"notes\":[],\"completion\":{}}]}]}";

		IllegalStateException e = assertThrows(IllegalStateException.class,
			() -> KnowledgeBase.fromJson(new Gson(), EMPTY_QUESTS_JSON, diariesJson, EMPTY_MILESTONES_JSON, EMPTY_PRIORITIES_JSON));

		assertTrue(e.getMessage().contains("VARROCK"), e.getMessage());
		assertTrue(e.getMessage().contains("EASY"), e.getMessage());
		assertTrue(e.getMessage().contains("task 1"), e.getMessage());
	}

	@Test
	void milestoneWithUnknownQuestNameFailsLoudlyNamingTheMilestoneId()
	{
		String milestonesJson = "{\"version\":1,\"milestones\":[{\"id\":\"milestone:test\",\"category\":\"unlock\","
			+ "\"subcategory\":null,\"name\":\"Test\",\"wikiTitle\":\"Test\",\"priority\":5,\"reason\":\"r\",\"unlocks\":[],"
			+ "\"requirements\":{\"skills\":[],\"quests\":[\"Not A Real Quest\"],\"diaries\":[],\"combatLevel\":null,"
			+ "\"questPoints\":null,\"items\":[]},\"ownedIf\":[],\"gearTier\":null,\"sources\":[]}]}";

		IllegalStateException e = assertThrows(IllegalStateException.class,
			() -> KnowledgeBase.fromJson(new Gson(), EMPTY_QUESTS_JSON, EMPTY_DIARIES_JSON, milestonesJson, EMPTY_PRIORITIES_JSON));

		assertTrue(e.getMessage().contains("milestone:test"), e.getMessage());
		assertTrue(e.getMessage().contains("Not A Real Quest"), e.getMessage());
	}

	@Test
	void unresolvedQuestPrereqFailsLoudlyNamingTheQuestAndThePrereq()
	{
		String questsJson = "{\"version\":1,\"generatedAt\":\"x\",\"quests\":[{\"id\":0,\"name\":\"Test Quest\","
			+ "\"wikiTitle\":\"Test Quest\",\"skills\":[],\"prereqs\":[\"Not A Real Quest\"],\"items\":[],\"questPoints\":1,\"source\":\"test\"}]}";

		IllegalStateException e = assertThrows(IllegalStateException.class,
			() -> KnowledgeBase.fromJson(new Gson(), questsJson, EMPTY_DIARIES_JSON, EMPTY_MILESTONES_JSON, EMPTY_PRIORITIES_JSON));

		assertTrue(e.getMessage().contains("Test Quest"), e.getMessage());
		assertTrue(e.getMessage().contains("Not A Real Quest"), e.getMessage());
	}

	@Test
	void unresolvedStartedQuestPrereqFailsLoudlyNamingTheQuestAndThePrereq()
	{
		String questsJson = "{\"version\":1,\"generatedAt\":\"x\",\"quests\":[{\"id\":0,\"name\":\"Test Quest\","
			+ "\"wikiTitle\":\"Test Quest\",\"skills\":[],\"prereqs\":[],\"prereqsStarted\":[\"Not A Real Quest\"],\"items\":[],"
			+ "\"questPoints\":1,\"source\":\"test\"}]}";

		IllegalStateException e = assertThrows(IllegalStateException.class,
			() -> KnowledgeBase.fromJson(new Gson(), questsJson, EMPTY_DIARIES_JSON, EMPTY_MILESTONES_JSON, EMPTY_PRIORITIES_JSON));

		assertTrue(e.getMessage().contains("Test Quest"), e.getMessage());
		assertTrue(e.getMessage().contains("Not A Real Quest"), e.getMessage());
	}

	@Test
	void everyBundledQuestPrereqAndStartedPrereqResolvesToAKnownQuest()
	{
		KnowledgeBase kb = KnowledgeBase.load(new Gson());

		for (QuestEntry quest : kb.getQuests())
		{
			for (String prereq : quest.getPrereqs())
			{
				assertNotNull(kb.questByName(prereq), quest.getName() + " has unresolved prereq \"" + prereq + "\"");
			}
			for (String prereq : quest.getPrereqsStarted())
			{
				assertNotNull(kb.questByName(prereq), quest.getName() + " has unresolved started prereq \"" + prereq + "\"");
			}
		}
	}

	@Test
	void bundledMilestonesJsonLoadsFiftyFiveEntriesAndPassesValidation()
	{
		KnowledgeBase kb = KnowledgeBase.load(new Gson());

		assertEquals(55, kb.getMilestones().size());
		for (MilestoneEntry entry : kb.getMilestones())
		{
			assertNotNull(kb.milestoneById(entry.getId()), entry.getId());
		}
	}

	@Test
	void bundledJsonLoads()
	{
		KnowledgeBase kb = KnowledgeBase.load(new Gson());

		assertFalse(kb.getQuests().isEmpty());
		assertFalse(kb.getDiaries().isEmpty());
		assertNotNull(kb.getQuestsGeneratedAt());
		assertNotNull(kb.getDiariesGeneratedAt());
	}

	@Test
	void everyQuestConstantHasAnEntryById()
	{
		KnowledgeBase kb = KnowledgeBase.load(new Gson());

		for (Quest quest : Quest.values())
		{
			QuestEntry entry = kb.questById(quest.getId());
			assertNotNull(entry, "missing kb entry for " + quest);
			assertEquals(quest.getName(), entry.getName());
		}
	}

	@Test
	void everyDiaryTierHasContiguousOrdinalsStartingAtOne()
	{
		KnowledgeBase kb = KnowledgeBase.load(new Gson());

		for (DiaryTier tier : DiaryTier.values())
		{
			DiaryEntry entry = kb.diary(tier);
			assertNotNull(entry, "missing kb entry for " + tier);

			List<Integer> ordinals = entry.getTasks().stream().map(DiaryTask::getOrdinal).collect(java.util.stream.Collectors.toList());
			for (int i = 0; i < ordinals.size(); i++)
			{
				assertEquals(i + 1, ordinals.get(i), tier + " ordinals must be contiguous starting at 1");
			}
		}
	}

	@Test
	void noSkillReqInTheLoadedKbHasANullSkill()
	{
		KnowledgeBase kb = KnowledgeBase.load(new Gson());

		for (QuestEntry quest : kb.getQuests())
		{
			for (SkillReq req : quest.getSkills())
			{
				assertNotNull(req.getSkill(), quest.getName() + " has a SkillReq with a null skill");
			}
		}
		for (DiaryEntry diary : kb.getDiaries())
		{
			for (DiaryTask task : diary.getTasks())
			{
				for (SkillReq req : task.getSkills())
				{
					assertNotNull(req.getSkill(), diary.getTier() + " task " + task.getOrdinal() + " has a SkillReq with a null skill");
				}
			}
		}
	}

	@Test
	void questPointRequirementIsNormalisedOntoQuestPointsRequired()
	{
		KnowledgeBase kb = KnowledgeBase.load(new Gson());

		// The quest with the highest "Quest point" requirement in the bundled data.
		QuestEntry whileGuthixSleeps = kb.questByName("While Guthix Sleeps");
		assertNotNull(whileGuthixSleeps);
		assertEquals(180, whileGuthixSleeps.getQuestPointsRequired());
		assertTrue(whileGuthixSleeps.getSkills().stream().noneMatch(s -> s.getSkill() == null));
	}

	@Test
	void kudosRequirementIsNormalisedOntoKudosRequired()
	{
		KnowledgeBase kb = KnowledgeBase.load(new Gson());

		QuestEntry boneVoyage = kb.questByName("Bone Voyage");
		assertNotNull(boneVoyage);
		assertEquals(100, boneVoyage.getKudosRequired());
	}

	@Test
	void combatRequirementIsNormalisedOntoCombatLevelRequired()
	{
		KnowledgeBase kb = KnowledgeBase.load(new Gson());

		DiaryEntry varrockMedium = kb.diary(DiaryTier.VARROCK_MEDIUM);
		DiaryTask vannaka = varrockMedium.getTasks().stream().filter(t -> t.getOrdinal() == 9).findFirst().orElseThrow();
		assertEquals(40, vannaka.getCombatLevelRequired());
		assertTrue(vannaka.getSkills().stream().noneMatch(s -> s.getSkill() == null));
	}

	@Test
	void questArtefactSkillIsDroppedAndNotedOnChampionsGuildTask()
	{
		// VARROCK MEDIUM task 2 ("Enter the Champions' Guild.") has a kb-build parser artefact: a
		// mislabelled 32 "Quest" skill entry that should have been a 32 quest-points requirement.
		KnowledgeBase kb = KnowledgeBase.load(new Gson());

		DiaryEntry varrockMedium = kb.diary(DiaryTier.VARROCK_MEDIUM);
		DiaryTask championsGuild = varrockMedium.getTasks().stream().filter(t -> t.getOrdinal() == 2).findFirst().orElseThrow();

		assertTrue(championsGuild.getSkills().isEmpty());
		assertTrue(championsGuild.getNotes().contains("Quest requirement (see wiki)"));
	}

	@Test
	void diaryVarpsContainsKnownVarps()
	{
		KnowledgeBase kb = KnowledgeBase.load(new Gson());

		assertTrue(kb.diaryVarps().contains(1176));
		assertTrue(kb.diaryVarps().contains(2085));
	}

	// --- Task 41: milestone stage and recommended profile (spec ruling 27). ---

	private static final String MILESTONE_TEMPLATE = "{\"id\":\"milestone:test\",\"category\":\"boss\","
		+ "\"subcategory\":null,\"name\":\"Test\",\"wikiTitle\":\"Test\",\"priority\":5,\"reason\":\"r\",\"unlocks\":[],"
		+ "\"requirements\":{\"skills\":[],\"quests\":[],\"diaries\":[],\"combatLevel\":null,\"questPoints\":null,\"items\":[]},"
		+ "\"ownedIf\":[],\"gearTier\":null,\"sources\":[]%s}";

	@Test
	void milestoneWithStageAndRecommendedProfileLoadsBoth()
	{
		String recommended = ",\"stage\":3,\"recommended\":{\"skills\":[{\"skill\":\"Ranged\",\"level\":85}],"
			+ "\"combatLevel\":100,\"gearOwnedAny\":[{\"name\":\"Bandos chestplate\",\"id\":11832,\"ids\":[11832]}]}";
		String milestonesJson = "{\"version\":1,\"milestones\":[" + String.format(MILESTONE_TEMPLATE, recommended) + "]}";

		KnowledgeBase kb = KnowledgeBase.fromJson(new Gson(), EMPTY_QUESTS_JSON, EMPTY_DIARIES_JSON, milestonesJson, EMPTY_PRIORITIES_JSON);
		MilestoneEntry entry = kb.milestoneById("milestone:test");

		assertEquals(3, entry.getStage());
		assertNotNull(entry.getRecommended());
		assertEquals(1, entry.getRecommended().getSkills().size());
		assertEquals(85, entry.getRecommended().getSkills().get(0).getLevel());
		assertEquals(100, entry.getRecommended().getCombatLevel());
		assertEquals(1, entry.getRecommended().getGearOwnedAny().size());
		assertEquals(11832, entry.getRecommended().getGearOwnedAny().get(0).getId());
		assertEquals(1, entry.getRecommended().effectiveGearOwnedMin(), "no gearOwnedMin in the JSON: defaults to ceil(1/2) = 1");
	}

	@Test
	void milestoneWithExplicitGearOwnedMinLoadsIt()
	{
		String recommended = ",\"recommended\":{\"skills\":[],\"combatLevel\":null,"
			+ "\"gearOwnedAny\":[{\"name\":\"A\",\"id\":1,\"ids\":[1]},{\"name\":\"B\",\"id\":2,\"ids\":[2]}],\"gearOwnedMin\":1}";
		String milestonesJson = "{\"version\":1,\"milestones\":[" + String.format(MILESTONE_TEMPLATE, recommended) + "]}";

		KnowledgeBase kb = KnowledgeBase.fromJson(new Gson(), EMPTY_QUESTS_JSON, EMPTY_DIARIES_JSON, milestonesJson, EMPTY_PRIORITIES_JSON);

		assertEquals(1, kb.milestoneById("milestone:test").getRecommended().effectiveGearOwnedMin(),
			"explicit gearOwnedMin overrides the default ceil(2/2) = 2");
	}

	@Test
	void milestoneWithNoStageFieldDefaultsToStageTwoAndNullRecommended()
	{
		String milestonesJson = "{\"version\":1,\"milestones\":[" + String.format(MILESTONE_TEMPLATE, "") + "]}";

		KnowledgeBase kb = KnowledgeBase.fromJson(new Gson(), EMPTY_QUESTS_JSON, EMPTY_DIARIES_JSON, milestonesJson, EMPTY_PRIORITIES_JSON);
		MilestoneEntry entry = kb.milestoneById("milestone:test");

		assertEquals(2, entry.getStage());
		assertNull(entry.getRecommended());
	}

	@Test
	void milestoneStageOutsideOneToFourFailsLoudly()
	{
		String milestonesJson = "{\"version\":1,\"milestones\":[" + String.format(MILESTONE_TEMPLATE, ",\"stage\":5") + "]}";

		IllegalStateException e = assertThrows(IllegalStateException.class,
			() -> KnowledgeBase.fromJson(new Gson(), EMPTY_QUESTS_JSON, EMPTY_DIARIES_JSON, milestonesJson, EMPTY_PRIORITIES_JSON));

		assertTrue(e.getMessage().contains("milestone:test"), e.getMessage());
		assertTrue(e.getMessage().contains("stage"), e.getMessage());
	}

	@Test
	void everyBundledMilestoneHasAStageBetweenOneAndFour()
	{
		KnowledgeBase kb = KnowledgeBase.load(new Gson());

		for (MilestoneEntry entry : kb.getMilestones())
		{
			assertTrue(entry.getStage() >= 1 && entry.getStage() <= 4, entry.getId() + " has stage " + entry.getStage());
		}
	}

	// --- Task 46: milestone obtainedFrom (the boss a gear entry drops from). ---

	@Test
	void milestoneObtainedFromResolvingToAKnownMilestoneLoadsFine()
	{
		String boss = String.format(MILESTONE_TEMPLATE, "").replace("milestone:test", "boss:test");
		String gear = String.format(MILESTONE_TEMPLATE, ",\"obtainedFrom\":\"boss:test\"");
		String milestonesJson = "{\"version\":1,\"milestones\":[" + boss + "," + gear + "]}";

		KnowledgeBase kb = KnowledgeBase.fromJson(new Gson(), EMPTY_QUESTS_JSON, EMPTY_DIARIES_JSON, milestonesJson, EMPTY_PRIORITIES_JSON);

		assertEquals("boss:test", kb.milestoneById("milestone:test").getObtainedFrom());
	}

	@Test
	void milestoneWithNoObtainedFromFieldLeavesItNull()
	{
		String milestonesJson = "{\"version\":1,\"milestones\":[" + String.format(MILESTONE_TEMPLATE, "") + "]}";

		KnowledgeBase kb = KnowledgeBase.fromJson(new Gson(), EMPTY_QUESTS_JSON, EMPTY_DIARIES_JSON, milestonesJson, EMPTY_PRIORITIES_JSON);

		assertNull(kb.milestoneById("milestone:test").getObtainedFrom());
	}

	@Test
	void milestoneObtainedFromNamingAnUnknownMilestoneFailsLoudly()
	{
		String milestonesJson = "{\"version\":1,\"milestones\":["
			+ String.format(MILESTONE_TEMPLATE, ",\"obtainedFrom\":\"boss:does-not-exist\"") + "]}";

		IllegalStateException e = assertThrows(IllegalStateException.class,
			() -> KnowledgeBase.fromJson(new Gson(), EMPTY_QUESTS_JSON, EMPTY_DIARIES_JSON, milestonesJson, EMPTY_PRIORITIES_JSON));

		assertTrue(e.getMessage().contains("milestone:test"), e.getMessage());
		assertTrue(e.getMessage().contains("boss:does-not-exist"), e.getMessage());
	}

	@Test
	void everyBundledMilestoneObtainedFromResolvesToAKnownMilestoneId()
	{
		KnowledgeBase kb = KnowledgeBase.load(new Gson());

		for (MilestoneEntry entry : kb.getMilestones())
		{
			if (entry.getObtainedFrom() != null)
			{
				assertNotNull(kb.milestoneById(entry.getObtainedFrom()),
					entry.getId() + " has obtainedFrom \"" + entry.getObtainedFrom() + "\", which doesn't resolve");
			}
		}
	}

	// --- Task 48: every milestone has a readiness bar, so none is "ready now" for free. ---

	/**
	 * Every milestone must have at least one of: a curated {@code recommended} profile, a quest or
	 * diary requirement, or a skill requirement of level 40+. Without one of these, the milestone
	 * scores its full priority as "ready now" for any account, regardless of how stage-appropriate
	 * it actually is (task 48's motivating finding: Fire cape, Fighter torso, and Slayer helmet (i)
	 * had none, crowding out stage-appropriate goals like Moons of Peril on a mid-game profile).
	 */
	@Test
	void everyBundledMilestoneHasARecommendedProfileOrAQuestDiaryOrFortyPlusSkillRequirement()
	{
		KnowledgeBase kb = KnowledgeBase.load(new Gson());

		List<String> offenders = kb.getMilestones().stream()
			.filter(entry -> entry.getRecommended() == null)
			.filter(entry -> entry.getQuests().isEmpty())
			.filter(entry -> entry.getDiaries().isEmpty())
			.filter(entry -> entry.getSkills().stream().noneMatch(s -> s.getLevel() >= 40))
			.map(MilestoneEntry::getId)
			.collect(java.util.stream.Collectors.toList());

		assertTrue(offenders.isEmpty(), "milestones with no readiness bar at all: " + offenders);
	}
}
