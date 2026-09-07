package dev.reece.nta.engine;

import dev.reece.nta.kb.DiaryEntry;
import dev.reece.nta.kb.DiaryTask;
import dev.reece.nta.kb.ItemReq;
import dev.reece.nta.kb.KnowledgeBase;
import dev.reece.nta.kb.QuestEntry;
import dev.reece.nta.kb.SkillReq;
import dev.reece.nta.kb.TaskCompletion;
import dev.reece.nta.snapshot.DiaryTier;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Skill;

/**
 * Fluent builder for a small hand-built {@link KnowledgeBase}, so engine tests don't depend on
 * the bundled JSON. Two independent chains, distinguished by the {@code quest(...)}/{@code diary(...)}
 * call that starts them:
 * <pre>
 *   new KbBuilder().quest(1, "Cook's Assistant").skill(Skill.COOKING, 1).item("Egg", 1).build();
 *   new KbBuilder().diary(DiaryTier.VARROCK_EASY).task(1, "...").skill(Skill.MINING, 10).completion(1176, 0).build();
 * </pre>
 * Every setter (skill/item/combat) applies to whichever quest or diary task is currently open.
 */
final class KbBuilder
{
	private final List<QuestSpec> questSpecs = new ArrayList<>();
	private final List<DiarySpec> diarySpecs = new ArrayList<>();
	private final List<DiaryEntry> legacyDiaries = new ArrayList<>();

	private QuestSpec currentQuest;
	private DiarySpec currentDiary;
	private TaskSpec currentTask;

	/**
	 * Adds a tier with {@code taskCount} tasks, each completed via bit {@code ordinal - 1} of
	 * {@code varp}. Predates the fluent {@link #diary} chain; kept for existing callers.
	 */
	KbBuilder diaryTier(DiaryTier tier, int varp, int taskCount)
	{
		List<DiaryTask> tasks = new ArrayList<>();
		for (int ordinal = 1; ordinal <= taskCount; ordinal++)
		{
			TaskCompletion completion = new TaskCompletion(varp, ordinal - 1, null, null);
			tasks.add(new DiaryTask(ordinal, "task " + ordinal, List.of(), List.of(), List.of(), List.of(), completion, null));
		}
		legacyDiaries.add(new DiaryEntry(tier, 0, tasks));
		return this;
	}

	KbBuilder quest(int id, String name)
	{
		currentQuest = new QuestSpec(id, name);
		questSpecs.add(currentQuest);
		currentDiary = null;
		currentTask = null;
		return this;
	}

	/** Adds a quest-name requirement to the currently open diary task. */
	KbBuilder quest(String name)
	{
		currentTask.quests.add(name);
		return this;
	}

	KbBuilder diary(DiaryTier tier)
	{
		currentDiary = new DiarySpec(tier);
		diarySpecs.add(currentDiary);
		currentQuest = null;
		currentTask = null;
		return this;
	}

	KbBuilder task(int ordinal, String text)
	{
		currentTask = new TaskSpec(ordinal, text);
		currentDiary.tasks.add(currentTask);
		return this;
	}

	KbBuilder skill(Skill skill, int level)
	{
		return skill(skill, level, false, false);
	}

	KbBuilder skill(Skill skill, int level, boolean boostable)
	{
		return skill(skill, level, boostable, false);
	}

	KbBuilder skill(Skill skill, int level, boolean boostable, boolean ironmanOnly)
	{
		SkillReq req = new SkillReq(skill, level, boostable, ironmanOnly);
		if (currentTask != null)
		{
			currentTask.skills.add(req);
		}
		else
		{
			currentQuest.skills.add(req);
		}
		return this;
	}

	/** Adds a prerequisite quest name to the currently open quest. */
	KbBuilder prereq(String name)
	{
		currentQuest.prereqs.add(name);
		return this;
	}

	/** Adds an item requirement to the currently open quest. */
	KbBuilder item(String name, int quantity)
	{
		currentQuest.items.add(new ItemReq(name, quantity));
		return this;
	}

	/** Adds an item requirement to the currently open diary task. */
	KbBuilder item(String name)
	{
		currentTask.items.add(name);
		return this;
	}

	KbBuilder note(String note)
	{
		currentTask.notes.add(note);
		return this;
	}

	KbBuilder questPoints(int n)
	{
		currentQuest.questPointsRequired = n;
		return this;
	}

	KbBuilder kudos(int n)
	{
		currentQuest.kudosRequired = n;
		return this;
	}

	/** Sets the combat level requirement on the currently open quest or diary task. */
	KbBuilder combat(int n)
	{
		if (currentTask != null)
		{
			currentTask.combatLevelRequired = n;
		}
		else
		{
			currentQuest.combatLevelRequired = n;
		}
		return this;
	}

	/** Overrides the currently open diary task's completion (default: bit {@code ordinal - 1} of varp 0). */
	KbBuilder completion(int varp, int bit)
	{
		currentTask.varp = varp;
		currentTask.bit = bit;
		return this;
	}

	KnowledgeBase build()
	{
		List<QuestEntry> quests = new ArrayList<>();
		for (QuestSpec s : questSpecs)
		{
			quests.add(new QuestEntry(s.id, s.name, s.name, List.copyOf(s.skills), List.copyOf(s.prereqs), List.copyOf(s.items),
				1, "test", s.questPointsRequired, s.kudosRequired, s.combatLevelRequired));
		}

		List<DiaryEntry> diaries = new ArrayList<>(legacyDiaries);
		for (DiarySpec d : diarySpecs)
		{
			List<DiaryTask> tasks = new ArrayList<>();
			for (TaskSpec t : d.tasks)
			{
				TaskCompletion completion = new TaskCompletion(t.varp, t.bit, null, null);
				tasks.add(new DiaryTask(t.ordinal, t.text, List.copyOf(t.skills), List.copyOf(t.quests), List.copyOf(t.items),
					List.copyOf(t.notes), completion, t.combatLevelRequired));
			}
			diaries.add(new DiaryEntry(d.tier, 0, tasks));
		}

		return KnowledgeBase.of(1, "test", 1, "test", quests, diaries);
	}

	private static final class QuestSpec
	{
		final int id;
		final String name;
		final List<SkillReq> skills = new ArrayList<>();
		final List<String> prereqs = new ArrayList<>();
		final List<ItemReq> items = new ArrayList<>();
		Integer questPointsRequired;
		Integer kudosRequired;
		Integer combatLevelRequired;

		QuestSpec(int id, String name)
		{
			this.id = id;
			this.name = name;
		}
	}

	private static final class DiarySpec
	{
		final DiaryTier tier;
		final List<TaskSpec> tasks = new ArrayList<>();

		DiarySpec(DiaryTier tier)
		{
			this.tier = tier;
		}
	}

	private static final class TaskSpec
	{
		final int ordinal;
		final String text;
		final List<SkillReq> skills = new ArrayList<>();
		final List<String> quests = new ArrayList<>();
		final List<String> items = new ArrayList<>();
		final List<String> notes = new ArrayList<>();
		Integer combatLevelRequired;
		int varp = 0;
		int bit;

		TaskSpec(int ordinal, String text)
		{
			this.ordinal = ordinal;
			this.text = text;
			this.bit = ordinal - 1;
		}
	}
}
