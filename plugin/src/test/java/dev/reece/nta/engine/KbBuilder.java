package dev.reece.nta.engine;

import dev.reece.nta.kb.DiaryEntry;
import dev.reece.nta.kb.DiaryTask;
import dev.reece.nta.kb.KnowledgeBase;
import dev.reece.nta.kb.TaskCompletion;
import dev.reece.nta.snapshot.DiaryTier;
import java.util.ArrayList;
import java.util.List;

/**
 * Hand-builds a small {@link KnowledgeBase} for tests that need one diary tier's worth of tasks
 * without depending on the bundled JSON.
 */
final class KbBuilder
{
	private final List<DiaryEntry> diaries = new ArrayList<>();

	/**
	 * Adds a tier with {@code taskCount} tasks, each completed via bit {@code ordinal - 1} of
	 * {@code varp}.
	 */
	KbBuilder diaryTier(DiaryTier tier, int varp, int taskCount)
	{
		List<DiaryTask> tasks = new ArrayList<>();
		for (int ordinal = 1; ordinal <= taskCount; ordinal++)
		{
			TaskCompletion completion = new TaskCompletion(varp, ordinal - 1, null, null);
			tasks.add(new DiaryTask(ordinal, "task " + ordinal, List.of(), List.of(), List.of(), List.of(), completion));
		}
		diaries.add(new DiaryEntry(tier, 0, tasks));
		return this;
	}

	KnowledgeBase build()
	{
		return KnowledgeBase.of(1, "test", 1, "test", List.of(), diaries);
	}
}
