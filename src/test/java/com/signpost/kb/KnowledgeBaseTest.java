package com.signpost.kb;

import com.google.gson.Gson;
import com.signpost.snapshot.DiaryTier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Quest;
import net.runelite.api.Skill;
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
	private static final String EMPTY_METHODS_JSON = "{\"version\":1,\"generatedAt\":\"x\",\"methods\":[]}";
	private static final String EMPTY_MATERIALS_JSON = "{\"version\":1,\"generatedAt\":\"x\",\"materials\":[]}";

	@Test
	void bundledPrioritiesGiveReasonsForTheTopQuestsAndEveryDiaryOverride()
	{
		// at least the top 40 curated quests and all diary overrides explain themselves.
		KnowledgeBase kb = KnowledgeBase.load(new Gson());

		long questReasons = kb.getPriorityOverrides().keySet().stream().filter(id -> id.startsWith("quest:") && kb.priorityReason(id) != null).count();
		long diaryOverrides = kb.getPriorityOverrides().keySet().stream().filter(id -> id.startsWith("diary:")).count();
		long diaryReasons = kb.getPriorityOverrides().keySet().stream().filter(id -> id.startsWith("diary:") && kb.priorityReason(id) != null).count();
		assertTrue(questReasons >= 40, "quest reasons: " + questReasons);
		assertEquals(diaryOverrides, diaryReasons, "every diary override has a reason");
		for (String id : kb.getPriorityOverrides().keySet())
		{
			String reason = kb.priorityReason(id);
			if (reason != null)
			{
				assertTrue(reason.length() <= 90, id + ": " + reason);
				assertTrue(reason.chars().allMatch(c -> c < 128), id + " is not ASCII: " + reason);
				assertFalse(reason.endsWith("."), id + ": " + reason);
			}
		}
	}

	private static final String QUEST_HEAD = "{\"version\":1,\"generatedAt\":\"x\",\"quests\":[{\"id\":158,\"name\":\"Waterfall Quest\","
		+ "\"wikiTitle\":\"Waterfall Quest\",\"skills\":[],\"prereqs\":[],\"prereqsStarted\":[],\"prereqNotes\":[],\"items\":[],"
		+ "\"questPoints\":1,\"source\":\"test\"";

	@Test
	void questRewardsMapToSkillsAndOneLampPerEntry()
	{
		String questsJson = QUEST_HEAD + ",\"rewards\":{\"xp\":{\"Attack\":13750,\"Strength\":13750},"
			+ "\"lamps\":[{\"xp\":20000,\"skills\":[\"Attack\",\"Magic\"],\"minLevel\":50}]}}]}";

		QuestEntry quest = KnowledgeBase.fromJson(new Gson(), questsJson, EMPTY_DIARIES_JSON, EMPTY_MILESTONES_JSON, EMPTY_PRIORITIES_JSON)
			.questByName("Waterfall Quest");

		assertEquals(Map.of(Skill.ATTACK, 13750L, Skill.STRENGTH, 13750L), quest.getRewardXp());
		assertEquals(List.of(new QuestLamp(20000, Set.of(Skill.ATTACK, Skill.MAGIC), 50)), quest.getLamps());

		QuestEntry noRewards = KnowledgeBase.fromJson(new Gson(), QUEST_HEAD + "}]}", EMPTY_DIARIES_JSON, EMPTY_MILESTONES_JSON, EMPTY_PRIORITIES_JSON)
			.questByName("Waterfall Quest");
		assertEquals(Map.of(), noRewards.getRewardXp());
		assertEquals(List.of(), noRewards.getLamps());
	}

	@Test
	void malformedQuestRewardsFailLoudlyNamingTheQuestAndField()
	{
		for (String bad : List.of(
			"{\"xp\":{\"Combat\":300},\"lamps\":[]}",
			"{\"xp\":{\"Attack\":0},\"lamps\":[]}",
			"{\"xp\":{},\"lamps\":[{\"xp\":500,\"skills\":[],\"minLevel\":0}]}",
			"{\"xp\":{},\"lamps\":[{\"xp\":500,\"skills\":[\"Attack\"],\"minLevel\":-1}]}",
			"{\"xp\":{},\"lamps\":[{\"xp\":0,\"skills\":[\"Attack\"],\"minLevel\":0}]}",
			"{\"xp\":{}}"))
		{
			String questsJson = QUEST_HEAD + ",\"rewards\":" + bad + "}]}";
			IllegalStateException e = assertThrows(IllegalStateException.class,
				() -> KnowledgeBase.fromJson(new Gson(), questsJson, EMPTY_DIARIES_JSON, EMPTY_MILESTONES_JSON, EMPTY_PRIORITIES_JSON), bad);
			assertTrue(e.getMessage().contains("Waterfall Quest"), e.getMessage());
			assertTrue(e.getMessage().contains("rewards"), e.getMessage());
		}
	}

	@Test
	void bundledQuestsCarryRewardXpForAtLeast120Quests()
	{
		KnowledgeBase kb = KnowledgeBase.load(new Gson());

		assertEquals(13750L, kb.questByName("Waterfall Quest").getRewardXp().get(Skill.ATTACK));
		long withRewards = kb.getQuests().stream().filter(q -> !q.getRewardXp().isEmpty() || !q.getLamps().isEmpty()).count();
		assertTrue(withRewards >= 120, "quests with rewards: " + withRewards);
	}

	@Test
	void priorityOverridesAcceptBareNumbersAndScoreReasonObjects()
	{
		String prioritiesJson = "{\"version\":1,\"overrides\":{\"quest:1\":9,\"quest:2\":{\"score\":8,\"reason\":\"Unlocks Piety\"},"
			+ "\"quest:3\":{\"score\":4}}}";

		KnowledgeBase kb = KnowledgeBase.fromJson(new Gson(), EMPTY_QUESTS_JSON, EMPTY_DIARIES_JSON, EMPTY_MILESTONES_JSON, prioritiesJson);

		assertEquals(9, kb.getPriorityOverrides().get("quest:1"));
		assertEquals(8, kb.getPriorityOverrides().get("quest:2"));
		assertEquals(4, kb.getPriorityOverrides().get("quest:3"));
		assertEquals("Unlocks Piety", kb.priorityReason("quest:2"));
		assertNull(kb.priorityReason("quest:1"));
		assertNull(kb.priorityReason("quest:3"));
	}

	@Test
	void malformedPriorityOverrideFailsLoudlyNamingTheEntryAndField()
	{
		for (String bad : List.of("{\"reason\":\"no score\"}", "{\"score\":\"nine\"}", "{\"score\":11}", "{\"score\":5,\"reason\":\"\"}", "\"9\"", "9.5"))
		{
			String prioritiesJson = "{\"version\":1,\"overrides\":{\"quest:7\":" + bad + "}}";
			IllegalStateException e = assertThrows(IllegalStateException.class,
				() -> KnowledgeBase.fromJson(new Gson(), EMPTY_QUESTS_JSON, EMPTY_DIARIES_JSON, EMPTY_MILESTONES_JSON, prioritiesJson), bad);
			assertTrue(e.getMessage().contains("quest:7"), e.getMessage());
			assertTrue(e.getMessage().contains("score") || e.getMessage().contains("reason"), e.getMessage());
		}
	}

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

	// --- skilling untradeables and outfits. ---

	@Test
	void skillingUntradeablesKeepTheirCuratedPriorityAboveDefaultDiaries()
	{
		KnowledgeBase kb = KnowledgeBase.load(new Gson());
		for (String id : List.of("coal-bag", "gem-bag", "herb-sack", "seed-box", "rune-pouch", "bonecrusher",
			"ash-sanctifier", "plank-sack", "fish-barrel", "tackle-box", "prospector-outfit", "pyromancer-outfit",
			"angler-outfit", "farmers-outfit", "raiments-of-the-eye", "lumberjack-outfit", "rogue-equipment"))
		{
			MilestoneEntry entry = kb.milestoneById("milestone:" + id);
			assertNotNull(entry, id);
			assertTrue(entry.getPriority() >= 6 && entry.getPriority() <= 7, entry.getId() + " priority " + entry.getPriority());
		}
	}

	@Test
	void milestoneOwnedIfMinLargerThanItsOwnedIfListFailsLoudly()
	{
		String milestonesJson = "{\"version\":1,\"milestones\":[{\"id\":\"milestone:test\",\"category\":\"gear\","
			+ "\"name\":\"T\",\"wikiTitle\":\"T\",\"priority\":5,\"unlocks\":[],\"sources\":[],\"stage\":1,\"ownedIfMin\":2,"
			+ "\"requirements\":{\"skills\":[],\"quests\":[],\"diaries\":[],\"items\":[]},"
			+ "\"ownedIf\":[{\"name\":\"A\",\"id\":1,\"ids\":[1]}]}]}";

		IllegalStateException e = assertThrows(IllegalStateException.class,
			() -> KnowledgeBase.fromJson(new Gson(), EMPTY_QUESTS_JSON, EMPTY_DIARIES_JSON, milestonesJson, EMPTY_PRIORITIES_JSON));

		assertTrue(e.getMessage().contains("milestone:test"), e.getMessage());
		assertTrue(e.getMessage().contains("ownedIfMin"), e.getMessage());
	}

	/** Variants cannot count as two outfit pieces or two skilling-untradeable goals. */
	@Test
	void everyBundledOwnedIfItemResolvesInMaterialsWithoutCountingAVariantTwice()
	{
		KnowledgeBase kb = KnowledgeBase.load(new Gson());
		Map<Integer, String> skillingOwners = new HashMap<>();

		for (MilestoneEntry entry : kb.getMilestones())
		{
			Map<Integer, String> claimedBy = new HashMap<>();
			for (OwnedItem owned : entry.getOwnedIf())
			{
				MaterialEntry material = kb.materialByName(owned.getName());
				assertNotNull(material, entry.getId() + " ownedIf \"" + owned.getName() + "\" has no materials.json entry");
				assertTrue(owned.getIds().contains(material.getId()),
					entry.getId() + " ownedIf \"" + owned.getName() + "\" ids " + owned.getIds() + " lack materials id " + material.getId());
				for (int id : owned.getIds())
				{
					String owner = entry.getId() + "/" + owned.getName();
					String other = claimedBy.put(id, owner);
					assertNull(other, "item id " + id + " is ownedIf for both " + other + " and " + owner);
					if ("skilling".equals(entry.getSubcategory()))
					{
						assertNull(skillingOwners.put(id, owner), "skilling goals share item id " + id);
					}
				}
			}
		}
	}

	// --- milestone stage and recommended profile. ---

	private static final String MILESTONE_TEMPLATE = "{\"id\":\"milestone:test\",\"category\":\"boss\","
		+ "\"subcategory\":null,\"name\":\"Test\",\"wikiTitle\":\"Test\",\"priority\":5,\"reason\":\"r\",\"unlocks\":[],"
		+ "\"requirements\":{\"skills\":[],\"quests\":[],\"diaries\":[],\"combatLevel\":null,\"questPoints\":null,\"items\":[]},"
		+ "\"ownedIf\":[],\"gearTier\":null,\"sources\":[]%s}";


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

	// --- milestone obtainedFrom (the boss a gear entry drops from). ---

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

	// --- every milestone has a readiness bar, so none is "ready now" for free. ---

	/**
	 * Every milestone must have at least one of: a curated {@code recommended} profile, a quest or
	 * diary requirement, or a skill requirement of level 40+. Without one of these, the milestone
	 * scores its full priority as "ready now" for any account, regardless of how stage-appropriate
	 * it actually is (without readiness requirements, Fire cape, Graceful
	 * outfit, and Tempoross had none of the three, crowding out stage-appropriate goals like Moons
	 * of Peril on a mid-game profile; Fighter torso and Slayer helmet (i) already had a 40+ skill
	 * requirement and weren't offenders under this rule, but got a curated {@code recommended}
	 * profile anyway since their existing hard requirement alone still under-gated them).
	 */
	/** A gathering plan step may carry its own requirements; a quest name that isn't a RuneLite Quest fails loudly naming the plan. */
	@Test
	void gatheringStepNamingAnUnknownQuestFailsLoudlyNamingThePlan()
	{
		String gatheringJson = "{\"version\":1,\"plans\":[{\"item\":\"Kwuarm\",\"id\":263,\"title\":\"Run a herb farm loop for kwuarm\","
			+ "\"requires\":{\"skills\":[],\"quests\":[],\"items\":[],\"notes\":null},\"ratePerHour\":null,"
			+ "\"steps\":[{\"text\":\"Weiss patch.\",\"requires\":{\"skills\":[],\"quests\":[\"Not A Real Quest\"]}}],"
			+ "\"alternatives\":[],\"wikiUrl\":\"https://oldschool.runescape.wiki/w/Kwuarm\"}]}";

		IllegalStateException e = assertThrows(IllegalStateException.class,
			() -> KnowledgeBase.fromJson(new Gson(), EMPTY_QUESTS_JSON, EMPTY_DIARIES_JSON, EMPTY_MILESTONES_JSON, EMPTY_PRIORITIES_JSON,
				EMPTY_METHODS_JSON, EMPTY_MATERIALS_JSON, gatheringJson));

		assertTrue(e.getMessage().contains("Kwuarm"), e.getMessage());
		assertTrue(e.getMessage().contains("Not A Real Quest"), e.getMessage());
	}

	/** A well-formed step loads with its text, skill and quest requirements. */
	@Test
	void gatheringStepWithSkillAndQuestRequirementsLoads()
	{
		String gatheringJson = "{\"version\":1,\"plans\":[{\"item\":\"Kwuarm\",\"id\":263,\"title\":\"Run a herb farm loop for kwuarm\","
			+ "\"requires\":{\"skills\":[],\"quests\":[],\"items\":[],\"notes\":null},\"ratePerHour\":null,"
			+ "\"steps\":[{\"text\":\"Guild patch.\",\"requires\":{\"skills\":[{\"skill\":\"Farming\",\"level\":65}],"
			+ "\"quests\":[\"Making Friends with My Arm\"]}}],"
			+ "\"alternatives\":[],\"wikiUrl\":\"https://oldschool.runescape.wiki/w/Kwuarm\"}]}";

		KnowledgeBase kb = KnowledgeBase.fromJson(new Gson(), EMPTY_QUESTS_JSON, EMPTY_DIARIES_JSON, EMPTY_MILESTONES_JSON,
			EMPTY_PRIORITIES_JSON, EMPTY_METHODS_JSON, EMPTY_MATERIALS_JSON, gatheringJson);

		GatheringStep step = kb.getGatheringPlans().get(0).getSteps().get(0);
		assertEquals("Guild patch.", step.getText());
		assertEquals(Skill.FARMING, step.getRequires().getSkills().get(0).getSkill());
		assertEquals(65, step.getRequires().getSkills().get(0).getLevel());
		assertEquals(List.of("Making Friends with My Arm"), step.getRequires().getQuests());
	}

	// --- milestone prerequisite (another milestone that must be owned first). ---

	@Test
	void milestonePrerequisiteResolvingToAKnownMilestoneLoadsAndAnUnknownOneFailsLoudly()
	{
		String room = String.format(MILESTONE_TEMPLATE, "").replace("milestone:test", "poh:portal-chamber");
		String nexus = String.format(MILESTONE_TEMPLATE, ",\"prerequisite\":\"poh:portal-chamber\"");
		KnowledgeBase kb = KnowledgeBase.fromJson(new Gson(), EMPTY_QUESTS_JSON, EMPTY_DIARIES_JSON,
			"{\"version\":1,\"milestones\":[" + room + "," + nexus + "]}", EMPTY_PRIORITIES_JSON);
		assertEquals("poh:portal-chamber", kb.milestoneById("milestone:test").getPrerequisite());

		String orphan = String.format(MILESTONE_TEMPLATE, ",\"prerequisite\":\"poh:nope\"");
		IllegalStateException e = assertThrows(IllegalStateException.class,
			() -> KnowledgeBase.fromJson(new Gson(), EMPTY_QUESTS_JSON, EMPTY_DIARIES_JSON,
				"{\"version\":1,\"milestones\":[" + orphan + "]}", EMPTY_PRIORITIES_JSON));
		assertTrue(e.getMessage().contains("milestone:test"), e.getMessage());
		assertTrue(e.getMessage().contains("poh:nope"), e.getMessage());
	}

	@Test
	void milestonePrerequisiteCycleFailsLoudly()
	{
		String a = String.format(MILESTONE_TEMPLATE, ",\"prerequisite\":\"milestone:b\"").replace("milestone:test", "milestone:a");
		String b = String.format(MILESTONE_TEMPLATE, ",\"prerequisite\":\"milestone:a\"").replace("milestone:test", "milestone:b");

		IllegalStateException e = assertThrows(IllegalStateException.class,
			() -> KnowledgeBase.fromJson(new Gson(), EMPTY_QUESTS_JSON, EMPTY_DIARIES_JSON,
				"{\"version\":1,\"milestones\":[" + a + "," + b + "]}", EMPTY_PRIORITIES_JSON));
		assertTrue(e.getMessage().contains("prerequisite"), e.getMessage());
	}

	@Test
	void unknownRecommendedPrayerFailsWithItsMilestoneAndField()
	{
		String milestone = String.format(MILESTONE_TEMPLATE,
			",\"recommended\":{\"skills\":[],\"combatLevel\":null,\"gear\":[],\"prayers\":[\"Rigor\"]}");
		IllegalStateException error = assertThrows(IllegalStateException.class,
			() -> KnowledgeBase.fromJson(new Gson(), EMPTY_QUESTS_JSON, EMPTY_DIARIES_JSON,
				"{\"version\":1,\"milestones\":[" + milestone + "]}", EMPTY_PRIORITIES_JSON));
		assertTrue(error.getMessage().contains("milestone:test"));
		assertTrue(error.getMessage().contains("recommended.prayers"));
		assertTrue(error.getMessage().contains("Rigor"));
	}

	@Test
	void unsafeReadinessGroupsAndRewardValuesCannotLoad()
	{
		for (String fields : List.of(
			",\"recommended\":{\"skills\":[],\"gear\":[{\"role\":\"mystery\",\"alternatives\":[{\"name\":\"A\",\"id\":1,\"ids\":[1]}]}]}",
			",\"recommended\":{\"skills\":[],\"gear\":[{\"role\":\"melee-body\",\"alternatives\":[]}]}",
			",\"recommended\":{\"skills\":[],\"gear\":[{\"role\":\"melee-body\",\"alternatives\":[{\"name\":\"A\",\"id\":1,\"ids\":[0]}]}]}",
			",\"bossRewards\":[{\"name\":\"A\",\"ids\":[1],\"value\":\"PRICELESS\"}]",
			",\"bossRewards\":[{\"name\":\"A\",\"ids\":[1],\"situationalWhenAllOwned\":[[]]}]"))
		{
			String milestone = String.format(MILESTONE_TEMPLATE, fields);
			assertThrows(IllegalStateException.class, () -> KnowledgeBase.fromJson(new Gson(),
				EMPTY_QUESTS_JSON, EMPTY_DIARIES_JSON, "{\"version\":1,\"milestones\":[" + milestone + "]}", EMPTY_PRIORITIES_JSON));
		}
	}

	@Test
	void aSlayerRewardWithoutItsPointCostCannotLoad()
	{
		String milestone = String.format(MILESTONE_TEMPLATE, ",\"slayerReward\":\"BIGGER_AND_BADDER\"")
			.replace("\"category\":\"boss\"", "\"category\":\"unlock\"");
		IllegalStateException error = assertThrows(IllegalStateException.class,
			() -> KnowledgeBase.fromJson(new Gson(), EMPTY_QUESTS_JSON, EMPTY_DIARIES_JSON,
				"{\"version\":1,\"milestones\":[" + milestone + "]}", EMPTY_PRIORITIES_JSON));
		assertTrue(error.getMessage().contains("milestone:test"));
		assertTrue(error.getMessage().contains("slayerPoints"));
	}

	@Test
	void malformedBossRewardsNameTheBossAndField()
	{
		Map<String, String> malformed = Map.ofEntries(
			Map.entry("[null]", "bossRewards[]"),
			Map.entry("[{\"name\":\" \",\"ids\":[1]}]", "name"),
			Map.entry("[{\"ids\":[1]}]", "name"),
			Map.entry("[{\"name\":\"A\",\"ids\":[]}]", "ids"),
			Map.entry("[{\"name\":\"A\",\"ids\":[0]}]", "ids"),
			Map.entry("[{\"name\":\"A\",\"ids\":[null]}]", "ids"),
			Map.entry("[{\"name\":\"A\",\"ids\":[1,1]}]", "ids"),
			Map.entry("[{\"name\":\"A\",\"ids\":[1]},{\"name\":\"a\",\"ids\":[2]}]", "name"),
			Map.entry("[{\"name\":\"A\",\"ids\":[1]},{\"name\":\"B\",\"ids\":[1]}]", "ids"),
			Map.entry("[{\"name\":\"A\",\"ids\":[1],\"supersededBy\":[-2]}]", "supersededBy"),
			Map.entry("[{\"name\":\"A\",\"ids\":[1],\"supersededBy\":[1]}]", "supersededBy"),
			Map.entry("[{\"name\":\"A\",\"ids\":[1],\"prayer\":\"piety\"}]", "prayer"),
			Map.entry("[{\"name\":\"A\",\"ids\":[1],\"prayer\":\"rigour\"},{\"name\":\"B\",\"ids\":[2],\"prayer\":\"rigour\"}]", "prayer"));
		for (Map.Entry<String, String> invalid : malformed.entrySet())
		{
			String milestone = String.format(MILESTONE_TEMPLATE, ",\"bossRewards\":" + invalid.getKey());
			IllegalStateException error = assertThrows(IllegalStateException.class,
				() -> KnowledgeBase.fromJson(new Gson(), EMPTY_QUESTS_JSON, EMPTY_DIARIES_JSON,
					"{\"version\":1,\"milestones\":[" + milestone + "]}", EMPTY_PRIORITIES_JSON), invalid.getKey());
			assertTrue(error.getMessage().contains("milestone:test"), error.getMessage());
			assertTrue(error.getMessage().contains("bossRewards"), error.getMessage());
			assertTrue(error.getMessage().contains(invalid.getValue()), error.getMessage());
		}
	}

	@Test
	void bossRewardsCannotBeAssignedToNonBossMilestones()
	{
		String milestone = String.format(MILESTONE_TEMPLATE, ",\"bossRewards\":[]")
			.replace("\"category\":\"boss\"", "\"category\":\"unlock\"");
		IllegalStateException error = assertThrows(IllegalStateException.class,
			() -> KnowledgeBase.fromJson(new Gson(), EMPTY_QUESTS_JSON, EMPTY_DIARIES_JSON,
				"{\"version\":1,\"milestones\":[" + milestone + "]}", EMPTY_PRIORITIES_JSON));
		assertTrue(error.getMessage().contains("milestone:test"), error.getMessage());
		assertTrue(error.getMessage().contains("bossRewards"), error.getMessage());
	}

	@Test
	void everyBundledBossHasConcreteRewardsAndMoonsNamesEachPiece()
	{
		KnowledgeBase kb = KnowledgeBase.load(new Gson());
		for (MilestoneEntry entry : kb.getMilestones())
		{
			if (entry.getCategory() == MilestoneCategory.BOSS)
			{
				assertFalse(entry.getBossRewards().isEmpty(), entry.getId());
			}
		}
		Set<String> moons = kb.milestoneById("boss:moons-of-peril").getBossRewards().stream()
			.map(BossReward::getName).collect(java.util.stream.Collectors.toSet());
		assertEquals(Set.of("Blood moon helm", "Blood moon chestplate", "Blood moon tassets", "Dual macuahuitl",
			"Blue moon helm", "Blue moon chestplate", "Blue moon tassets", "Blue moon spear",
			"Eclipse moon helm", "Eclipse moon chestplate", "Eclipse moon tassets", "Eclipse atlatl"), moons);
	}

	@Test
	void everyBundledMilestoneHasAReadinessRequirement()
	{
		KnowledgeBase kb = KnowledgeBase.load(new Gson());

		// A skill requirement above the starting level constrains readiness, including early-game gear.
		// a bank-checked item requirement (the house's 1,000 coins) is a bar too.
		List<String> offenders = kb.getMilestones().stream()
			.filter(entry -> entry.getRecommended() == null)
			.filter(entry -> entry.getQuests().isEmpty())
			.filter(entry -> entry.getDiaries().isEmpty())
			.filter(entry -> entry.getItems().isEmpty())
			.filter(entry -> entry.getSlayerPoints() == null)
			.filter(entry -> entry.getSkills().stream().noneMatch(s -> s.getLevel() > 1))
			.map(MilestoneEntry::getId)
			.collect(java.util.stream.Collectors.toList());

		assertTrue(offenders.isEmpty(), "milestones with no readiness bar at all: " + offenders);
	}
}
