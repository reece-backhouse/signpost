package dev.reece.nta.kb;

import com.google.gson.Gson;
import dev.reece.nta.snapshot.DiaryTier;
import java.util.List;
import net.runelite.api.Quest;
import net.runelite.api.Skill;
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
	void everySkillNameEitherResolvesToASkillOrIsAKnownPseudoSkill()
	{
		KnowledgeBase kb = KnowledgeBase.load(new Gson());
		List<String> pseudoSkills = List.of("Quest point", "Quest", "Kudos", "Combat");

		for (QuestEntry quest : kb.getQuests())
		{
			assertResolvedOrPseudo(quest.getSkills(), pseudoSkills);
		}
		for (DiaryEntry diary : kb.getDiaries())
		{
			for (DiaryTask task : diary.getTasks())
			{
				assertResolvedOrPseudo(task.getSkills(), pseudoSkills);
			}
		}
	}

	private static void assertResolvedOrPseudo(List<SkillReq> reqs, List<String> pseudoSkills)
	{
		for (SkillReq req : reqs)
		{
			if (req.getSkill() == null)
			{
				assertTrue(pseudoSkills.contains(req.getSkillName()), "unresolved skill name: " + req.getSkillName());
			}
			else
			{
				assertEquals(req.getSkillName(), req.getSkill().getName());
			}
		}
	}

	@Test
	void diaryVarpsContainsKnownVarps()
	{
		KnowledgeBase kb = KnowledgeBase.load(new Gson());

		assertTrue(kb.diaryVarps().contains(1176));
		assertTrue(kb.diaryVarps().contains(2085));
	}
}
