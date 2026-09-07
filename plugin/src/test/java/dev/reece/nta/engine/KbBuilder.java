package dev.reece.nta.engine;

import dev.reece.nta.engine.model.ItemSource;
import dev.reece.nta.kb.DiaryEntry;
import dev.reece.nta.kb.DiaryRef;
import dev.reece.nta.kb.DiaryTask;
import dev.reece.nta.kb.ItemQuantity;
import dev.reece.nta.kb.ItemReq;
import dev.reece.nta.kb.KnowledgeBase;
import dev.reece.nta.kb.MaterialEntry;
import dev.reece.nta.kb.MethodEntry;
import dev.reece.nta.kb.MilestoneCategory;
import dev.reece.nta.kb.MilestoneEntry;
import dev.reece.nta.kb.OwnedItem;
import dev.reece.nta.kb.QuestEntry;
import dev.reece.nta.kb.SkillReq;
import dev.reece.nta.kb.TaskCompletion;
import dev.reece.nta.snapshot.DiaryTier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
	private final List<MilestoneSpec> milestoneSpecs = new ArrayList<>();
	private final List<MethodSpec> methodSpecs = new ArrayList<>();
	private final List<MaterialSpec> materialSpecs = new ArrayList<>();
	private final Map<String, Integer> priorityOverrides = new LinkedHashMap<>();

	private QuestSpec currentQuest;
	private DiarySpec currentDiary;
	private TaskSpec currentTask;
	private MilestoneSpec currentMilestone;
	private MethodSpec currentMethod;
	private MaterialSpec currentMaterial;

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
		currentMilestone = null;
		return this;
	}

	/** Adds a quest-name requirement to the currently open milestone, or diary task if none is open. */
	KbBuilder quest(String name)
	{
		if (currentMilestone != null)
		{
			currentMilestone.quests.add(name);
		}
		else
		{
			currentTask.quests.add(name);
		}
		return this;
	}

	/**
	 * Starts a diary tier chain, or - if a milestone is currently open - adds {@code tier} as a
	 * diary requirement to that milestone instead.
	 */
	KbBuilder diary(DiaryTier tier)
	{
		if (currentMilestone != null)
		{
			currentMilestone.diaries.add(tier);
			return this;
		}
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

	/** Starts a milestone chain. */
	KbBuilder milestone(String id, MilestoneCategory category, String name, int priority)
	{
		currentMilestone = new MilestoneSpec(id, category, name, priority);
		milestoneSpecs.add(currentMilestone);
		currentQuest = null;
		currentDiary = null;
		currentTask = null;
		return this;
	}

	/** Adds an ownedIf item id to the currently open milestone. */
	KbBuilder ownedIf(String name, int id)
	{
		currentMilestone.ownedIf.add(new OwnedItem(name, id));
		return this;
	}

	/** Starts a {@code methods.json}-style training method chain (task 34/35/36 engine tests). */
	KbBuilder method(Skill skill, String name, int levelReq, double xpPerAction)
	{
		currentMethod = new MethodSpec(skill, name, levelReq, xpPerAction);
		methodSpecs.add(currentMethod);
		currentQuest = null;
		currentDiary = null;
		currentTask = null;
		currentMilestone = null;
		currentMaterial = null;
		return this;
	}

	/** Adds a material requirement (by id only; the name is synthesised for display) to the currently open method. */
	KbBuilder material(int id, double quantity)
	{
		currentMethod.materials.add(new ItemQtyRef(id, quantity));
		return this;
	}

	/** Adds an output (by id only) to the currently open method. */
	KbBuilder output(int id, double quantity)
	{
		currentMethod.outputs.add(new ItemQtyRef(id, quantity));
		return this;
	}

	/** Flags the currently open method {@code intermediate} (0-xp recipe, e.g. an unfinished potion). */
	KbBuilder intermediate()
	{
		currentMethod.intermediate = true;
		return this;
	}

	/** Adds a Bucket "type" tag (e.g. {@code "Barbarian Mix"}) to the currently open method. */
	KbBuilder type(String type)
	{
		currentMethod.types.add(type);
		return this;
	}

	/** Starts a {@code materials.json}-style material chain (task 34/35 engine tests). */
	KbBuilder material(String name, Integer id)
	{
		currentMaterial = new MaterialSpec(name, id);
		materialSpecs.add(currentMaterial);
		currentMethod = null;
		return this;
	}

	/** Adds a source to the currently open material. {@code accountTypes} is accepted for readability but unused: every
	 * bundled source's {@code type == "GE"} exactly when its {@code accountTypes} is {@code ["main"]}, so filtering
	 * (see {@link dev.reece.nta.engine.GapEngine#sourcesFor}) only ever looks at {@code type}. */
	KbBuilder source(String type, String where, String... accountTypes)
	{
		currentMaterial.sources.add(new ItemSource(type, where, ""));
		return this;
	}

	/** Sets a priority override for a goal id, applied instead of that goal's own priority. */
	KbBuilder priorityOverride(String id, int priority)
	{
		priorityOverrides.put(id, priority);
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
		if (currentMilestone != null)
		{
			currentMilestone.skills.add(req);
		}
		else if (currentTask != null)
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

	/** Adds a "must have started" prerequisite quest name to the currently open quest. */
	KbBuilder prereqStarted(String name)
	{
		currentQuest.prereqsStarted.add(name);
		return this;
	}

	/** Adds a display-only prereq note (an unresolved prereq name) to the currently open quest. */
	KbBuilder prereqNote(String note)
	{
		currentQuest.prereqNotes.add(note);
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

	/** Adds an item requirement (by id, with sources) to the currently open milestone. */
	KbBuilder item(String name, int id, int quantity, String... sources)
	{
		currentMilestone.items.add(new ItemReq(name, id, quantity, List.of(sources)));
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
			quests.add(new QuestEntry(s.id, s.name, s.name, List.copyOf(s.skills), List.copyOf(s.prereqs), List.copyOf(s.prereqsStarted),
				List.copyOf(s.prereqNotes), List.copyOf(s.items), 1, "test", s.questPointsRequired, s.kudosRequired, s.combatLevelRequired));
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

		List<MilestoneEntry> milestones = new ArrayList<>();
		for (MilestoneSpec m : milestoneSpecs)
		{
			List<DiaryRef> diaryRefs = new ArrayList<>();
			for (DiaryTier tier : m.diaries)
			{
				diaryRefs.add(new DiaryRef(tier));
			}
			milestones.add(new MilestoneEntry(m.id, m.category, null, m.name, m.name, m.priority, "test", List.of(),
				List.copyOf(m.skills), List.copyOf(m.quests), diaryRefs, null, null, List.copyOf(m.items), List.copyOf(m.ownedIf),
				null, List.of()));
		}

		Map<Integer, String> materialNames = new LinkedHashMap<>();
		for (MaterialSpec s : materialSpecs)
		{
			if (s.id != null)
			{
				materialNames.put(s.id, s.name);
			}
		}

		List<MethodEntry> methods = new ArrayList<>();
		for (MethodSpec m : methodSpecs)
		{
			List<ItemQuantity> materials = toItemQuantities(m.materials, materialNames);
			List<ItemQuantity> outputs = toItemQuantities(m.outputs, materialNames);
			methods.add(new MethodEntry(m.skill, m.name, m.name, m.levelReq, m.xpPerAction, materials, outputs, List.copyOf(m.types),
				true, false, null, m.intermediate, true));
		}

		List<MaterialEntry> materials = new ArrayList<>();
		for (MaterialSpec s : materialSpecs)
		{
			materials.add(new MaterialEntry(s.name, s.id, false, List.copyOf(s.sources)));
		}

		return KnowledgeBase.of(1, "test", 1, "test", quests, diaries, milestones, priorityOverrides, methods, materials);
	}

	private static List<ItemQuantity> toItemQuantities(List<ItemQtyRef> refs, Map<Integer, String> materialNames)
	{
		List<ItemQuantity> result = new ArrayList<>();
		for (ItemQtyRef r : refs)
		{
			result.add(new ItemQuantity(materialNames.getOrDefault(r.id, "item " + r.id), r.id, r.quantity));
		}
		return result;
	}

	private static final class QuestSpec
	{
		final int id;
		final String name;
		final List<SkillReq> skills = new ArrayList<>();
		final List<String> prereqs = new ArrayList<>();
		final List<String> prereqsStarted = new ArrayList<>();
		final List<String> prereqNotes = new ArrayList<>();
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

	private static final class MilestoneSpec
	{
		final String id;
		final MilestoneCategory category;
		final String name;
		final int priority;
		final List<SkillReq> skills = new ArrayList<>();
		final List<String> quests = new ArrayList<>();
		final List<DiaryTier> diaries = new ArrayList<>();
		final List<ItemReq> items = new ArrayList<>();
		final List<OwnedItem> ownedIf = new ArrayList<>();

		MilestoneSpec(String id, MilestoneCategory category, String name, int priority)
		{
			this.id = id;
			this.category = category;
			this.name = name;
			this.priority = priority;
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

	private static final class MethodSpec
	{
		final Skill skill;
		final String name;
		final int levelReq;
		final double xpPerAction;
		final List<ItemQtyRef> materials = new ArrayList<>();
		final List<ItemQtyRef> outputs = new ArrayList<>();
		final List<String> types = new ArrayList<>();
		boolean intermediate;

		MethodSpec(Skill skill, String name, int levelReq, double xpPerAction)
		{
			this.skill = skill;
			this.name = name;
			this.levelReq = levelReq;
			this.xpPerAction = xpPerAction;
		}
	}

	private static final class ItemQtyRef
	{
		final int id;
		final double quantity;

		ItemQtyRef(int id, double quantity)
		{
			this.id = id;
			this.quantity = quantity;
		}
	}

	private static final class MaterialSpec
	{
		final String name;
		final Integer id;
		final List<ItemSource> sources = new ArrayList<>();

		MaterialSpec(String name, Integer id)
		{
			this.name = name;
			this.id = id;
		}
	}
}
