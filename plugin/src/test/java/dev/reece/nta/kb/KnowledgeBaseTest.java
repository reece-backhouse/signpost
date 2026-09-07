package dev.reece.nta.kb;

import com.google.gson.Gson;
import dev.reece.nta.snapshot.DiaryTier;
import java.util.List;
import net.runelite.api.Quest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeBaseTest
{
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
}
