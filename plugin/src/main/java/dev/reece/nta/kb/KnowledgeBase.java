package dev.reece.nta.kb;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import dev.reece.nta.snapshot.DiaryTier;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;

/**
 * The bundled quest, achievement diary, and milestone data, loaded once from the classpath
 * resources {@code /kb/quests.json}, {@code /kb/diaries.json}, {@code /kb/milestones.json}, and
 * {@code /kb/priorities.json}. Pure data: no {@link net.runelite.api.Client}, no I/O beyond the
 * initial classpath read in {@link #load(Gson)}.
 */
@Slf4j
public final class KnowledgeBase
{
	private static final String QUESTS_RESOURCE = "/kb/quests.json";
	private static final String DIARIES_RESOURCE = "/kb/diaries.json";
	private static final String MILESTONES_RESOURCE = "/kb/milestones.json";
	private static final String PRIORITIES_RESOURCE = "/kb/priorities.json";

	private static final String QUEST_POINT_SKILL = "Quest point";
	private static final String KUDOS_SKILL = "Kudos";
	private static final String COMBAT_SKILL = "Combat";
	/** kb-build parser artefact (see VARROCK MEDIUM task 2): a mislabelled quest-point requirement. */
	private static final String QUEST_ARTEFACT_SKILL = "Quest";

	private final int questsVersion;
	private final String questsGeneratedAt;
	private final int diariesVersion;
	private final String diariesGeneratedAt;
	private final List<QuestEntry> quests;
	private final List<DiaryEntry> diaries;
	private final List<MilestoneEntry> milestones;
	private final Map<Integer, QuestEntry> questsById;
	private final Map<String, QuestEntry> questsByName;
	private final Map<DiaryTier, DiaryEntry> diariesByTier;
	private final Map<String, MilestoneEntry> milestonesById;
	private final Map<String, Integer> priorityOverrides;
	private final Set<Integer> diaryVarps;
	private final Set<Integer> diaryVarbits;

	private KnowledgeBase(
		int questsVersion, String questsGeneratedAt,
		int diariesVersion, String diariesGeneratedAt,
		List<QuestEntry> quests, List<DiaryEntry> diaries,
		List<MilestoneEntry> milestones, Map<String, Integer> priorityOverrides)
	{
		this.questsVersion = questsVersion;
		this.questsGeneratedAt = questsGeneratedAt;
		this.diariesVersion = diariesVersion;
		this.diariesGeneratedAt = diariesGeneratedAt;
		this.quests = List.copyOf(quests);
		this.diaries = List.copyOf(diaries);
		this.milestones = List.copyOf(milestones);
		this.priorityOverrides = Map.copyOf(priorityOverrides);

		Map<Integer, QuestEntry> byId = new LinkedHashMap<>();
		Map<String, QuestEntry> byName = new LinkedHashMap<>();
		for (QuestEntry quest : quests)
		{
			byId.put(quest.getId(), quest);
			byName.put(quest.getName(), quest);
		}
		this.questsById = Map.copyOf(byId);
		this.questsByName = Map.copyOf(byName);

		Map<String, MilestoneEntry> byMilestoneId = new LinkedHashMap<>();
		for (MilestoneEntry milestone : milestones)
		{
			byMilestoneId.put(milestone.getId(), milestone);
		}
		this.milestonesById = Map.copyOf(byMilestoneId);

		Map<DiaryTier, DiaryEntry> byTier = new EnumMap<>(DiaryTier.class);
		Set<Integer> varps = new HashSet<>();
		Set<Integer> varbits = new HashSet<>();
		for (DiaryEntry diary : diaries)
		{
			byTier.put(diary.getTier(), diary);
			for (DiaryTask task : diary.getTasks())
			{
				TaskCompletion completion = task.getCompletion();
				if (completion.getVarp() != null)
				{
					varps.add(completion.getVarp());
				}
				else
				{
					varbits.add(completion.getVarbit());
				}
			}
		}
		this.diariesByTier = Map.copyOf(byTier);
		this.diaryVarps = Set.copyOf(varps);
		this.diaryVarbits = Set.copyOf(varbits);
	}

	/**
	 * Builds a {@link KnowledgeBase} directly from already-constructed entries, bypassing the
	 * classpath JSON load. Exists for tests that need a small hand-built knowledge base.
	 */
	public static KnowledgeBase of(
		int questsVersion, String questsGeneratedAt,
		int diariesVersion, String diariesGeneratedAt,
		List<QuestEntry> quests, List<DiaryEntry> diaries,
		List<MilestoneEntry> milestones, Map<String, Integer> priorityOverrides)
	{
		return new KnowledgeBase(questsVersion, questsGeneratedAt, diariesVersion, diariesGeneratedAt, quests, diaries, milestones,
			priorityOverrides);
	}

	public static KnowledgeBase load(Gson gson)
	{
		QuestsFile questsFile = readResource(gson, QUESTS_RESOURCE, QuestsFile.class);
		DiariesFile diariesFile = readResource(gson, DIARIES_RESOURCE, DiariesFile.class);
		MilestonesFile milestonesFile = readResource(gson, MILESTONES_RESOURCE, MilestonesFile.class);
		PrioritiesFile prioritiesFile = readResource(gson, PRIORITIES_RESOURCE, PrioritiesFile.class);
		return build(questsFile, diariesFile, milestonesFile, prioritiesFile);
	}

	/**
	 * Package-visible for tests: maps already-parsed JSON strings the same way {@link #load} maps
	 * the bundled classpath resources, without touching the classpath.
	 */
	static KnowledgeBase fromJson(Gson gson, String questsJson, String diariesJson, String milestonesJson, String prioritiesJson)
	{
		return build(
			gson.fromJson(questsJson, QuestsFile.class),
			gson.fromJson(diariesJson, DiariesFile.class),
			gson.fromJson(milestonesJson, MilestonesFile.class),
			gson.fromJson(prioritiesJson, PrioritiesFile.class));
	}

	private static KnowledgeBase build(QuestsFile questsFile, DiariesFile diariesFile, MilestonesFile milestonesFile,
		PrioritiesFile prioritiesFile)
	{
		requireField(questsFile.quests, QUESTS_RESOURCE, "quests");
		requireField(diariesFile.diaries, DIARIES_RESOURCE, "diaries");
		requireField(milestonesFile.milestones, MILESTONES_RESOURCE, "milestones");
		requireField(prioritiesFile.overrides, PRIORITIES_RESOURCE, "overrides");

		List<QuestEntry> quests = questsFile.quests.stream().map(KnowledgeBase::toQuestEntry).collect(Collectors.toList());
		List<DiaryEntry> diaries = diariesFile.diaries.stream().map(KnowledgeBase::toDiaryEntry).collect(Collectors.toList());

		Set<String> questNames = quests.stream().map(QuestEntry::getName).collect(Collectors.toSet());
		Set<String> milestoneIds = new HashSet<>();
		List<MilestoneEntry> milestones = milestonesFile.milestones.stream()
			.map(dto -> toMilestoneEntry(dto, questNames, milestoneIds))
			.collect(Collectors.toList());

		return new KnowledgeBase(
			questsFile.version, questsFile.generatedAt,
			diariesFile.version, diariesFile.generatedAt,
			quests, diaries, milestones, prioritiesFile.overrides);
	}

	/** Fails loudly (rather than a bare NPE downstream) when a required JSON field is missing or explicitly null. */
	private static void requireField(Object value, String context, String field)
	{
		if (value == null)
		{
			throw new IllegalStateException("Malformed knowledge base data: " + context + " is missing '" + field + "'");
		}
	}

	private static boolean isValidCompletion(CompletionDto c)
	{
		boolean varpForm = c.varp != null && c.bit != null;
		boolean varbitForm = c.varbit != null && c.doneMin != null;
		return varpForm || varbitForm;
	}

	private static <T> T readResource(Gson gson, String resource, Class<T> type)
	{
		try (InputStream in = KnowledgeBase.class.getResourceAsStream(resource))
		{
			if (in == null)
			{
				throw new IllegalStateException("Missing knowledge base resource: " + resource);
			}
			T value = gson.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), type);
			if (value == null)
			{
				throw new IllegalStateException("Empty knowledge base resource: " + resource);
			}
			return value;
		}
		catch (IOException e)
		{
			throw new UncheckedIOException("Failed to read knowledge base resource: " + resource, e);
		}
		catch (JsonParseException e)
		{
			throw new IllegalStateException("Malformed knowledge base resource: " + resource, e);
		}
	}

	private static QuestEntry toQuestEntry(QuestDto dto)
	{
		String context = "quest \"" + dto.name + "\"";
		requireField(dto.skills, context, "skills");
		requireField(dto.prereqs, context, "prereqs");
		requireField(dto.items, context, "items");

		List<SkillReq> skills = new ArrayList<>();
		Integer questPointsRequired = null;
		Integer kudosRequired = null;
		Integer combatLevelRequired = null;
		for (SkillDto s : dto.skills)
		{
			if (s.skill.equals(QUEST_POINT_SKILL))
			{
				questPointsRequired = s.level;
			}
			else if (s.skill.equals(KUDOS_SKILL))
			{
				kudosRequired = s.level;
			}
			else if (s.skill.equals(COMBAT_SKILL))
			{
				combatLevelRequired = s.level;
			}
			else
			{
				skills.add(toSkillReq(s));
			}
		}

		List<ItemReq> items = dto.items.stream().map(i -> new ItemReq(i.name, i.quantity)).collect(Collectors.toList());
		return new QuestEntry(dto.id, dto.name, dto.wikiTitle, skills, List.copyOf(dto.prereqs), items, dto.questPoints, dto.source,
			questPointsRequired, kudosRequired, combatLevelRequired);
	}

	private static DiaryEntry toDiaryEntry(DiaryDto dto)
	{
		requireField(dto.tasks, dto.area + " " + dto.tier, "tasks");
		DiaryTier tier = DiaryTier.valueOf(dto.area + "_" + dto.tier);
		List<DiaryTask> tasks = dto.tasks.stream().map(t -> toDiaryTask(t, dto.area, dto.tier)).collect(Collectors.toList());
		return new DiaryEntry(tier, dto.tierVarbit, tasks);
	}

	private static DiaryTask toDiaryTask(TaskDto dto, String area, String tier)
	{
		String context = area + " " + tier + " task " + dto.ordinal;
		requireField(dto.skills, context, "skills");
		requireField(dto.quests, context, "quests");
		requireField(dto.items, context, "items");
		requireField(dto.notes, context, "notes");
		requireField(dto.completion, context, "completion");
		if (!isValidCompletion(dto.completion))
		{
			throw new IllegalStateException("Malformed knowledge base data: " + context
				+ " has an invalid completion (needs varp+bit or varbit+doneMin)");
		}

		List<SkillReq> skills = new ArrayList<>();
		Integer combatLevelRequired = null;
		List<String> notes = new ArrayList<>(dto.notes);
		for (SkillDto s : dto.skills)
		{
			if (s.skill.equals(COMBAT_SKILL))
			{
				combatLevelRequired = s.level;
			}
			else if (s.skill.equals(QUEST_ARTEFACT_SKILL))
			{
				notes.add("Quest requirement (see wiki)");
				log.warn("kb-build parser artefact: dropping non-skill 'Quest' requirement from {} {} task {}", area, tier, dto.ordinal);
			}
			else
			{
				skills.add(toSkillReq(s));
			}
		}

		TaskCompletion completion = new TaskCompletion(dto.completion.varp, dto.completion.bit, dto.completion.varbit, dto.completion.doneMin);
		return new DiaryTask(dto.ordinal, dto.text, skills, List.copyOf(dto.quests), List.copyOf(dto.items), List.copyOf(notes), completion,
			combatLevelRequired);
	}

	private static SkillReq toSkillReq(SkillDto dto)
	{
		return new SkillReq(resolveSkill(dto.skill, null), dto.level, dto.boostable, dto.ironmanOnly);
	}

	private static MilestoneEntry toMilestoneEntry(MilestoneDto dto, Set<String> questNames, Set<String> milestoneIds)
	{
		requireField(dto.id, "milestones.json entry", "id");
		String context = "milestone \"" + dto.id + "\"";
		requireField(dto.category, context, "category");
		requireField(dto.name, context, "name");
		requireField(dto.wikiTitle, context, "wikiTitle");
		requireField(dto.unlocks, context, "unlocks");
		requireField(dto.sources, context, "sources");
		requireField(dto.requirements, context, "requirements");
		requireField(dto.requirements.skills, context, "requirements.skills");
		requireField(dto.requirements.quests, context, "requirements.quests");
		requireField(dto.requirements.diaries, context, "requirements.diaries");
		requireField(dto.requirements.items, context, "requirements.items");
		requireField(dto.ownedIf, context, "ownedIf");

		if (!milestoneIds.add(dto.id))
		{
			throw new IllegalStateException("Malformed knowledge base data: duplicate milestone id \"" + dto.id + "\"");
		}
		if (dto.priority < 1 || dto.priority > 10)
		{
			throw new IllegalStateException("Malformed knowledge base data: " + context + " has priority " + dto.priority + " outside 1..10");
		}

		MilestoneCategory category = resolveMilestoneCategory(dto.category, context);

		List<SkillReq> skills = dto.requirements.skills.stream()
			.map(s -> new SkillReq(resolveSkill(s.skill, context), s.level, s.boostable, s.ironmanOnly))
			.collect(Collectors.toList());

		for (String questName : dto.requirements.quests)
		{
			if (!questNames.contains(questName))
			{
				throw new IllegalStateException("Malformed knowledge base data: " + context + " requires unknown quest \"" + questName + "\"");
			}
		}

		List<DiaryRef> diaries = dto.requirements.diaries.stream().map(d -> toDiaryRef(d, context)).collect(Collectors.toList());
		List<ItemReq> items = dto.requirements.items.stream().map(i -> toMilestoneItemReq(i, context)).collect(Collectors.toList());
		List<OwnedItem> ownedIf = dto.ownedIf.stream().map(o -> toOwnedItem(o, context)).collect(Collectors.toList());

		if (category == MilestoneCategory.GEAR && ownedIf.isEmpty())
		{
			throw new IllegalStateException("Malformed knowledge base data: " + context + " is category gear but has no ownedIf entries");
		}

		return new MilestoneEntry(dto.id, category, dto.subcategory, dto.name, dto.wikiTitle, dto.priority, dto.reason,
			List.copyOf(dto.unlocks), skills, List.copyOf(dto.requirements.quests), diaries, dto.requirements.combatLevel,
			dto.requirements.questPoints, items, ownedIf, dto.gearTier, List.copyOf(dto.sources));
	}

	private static DiaryRef toDiaryRef(DiaryRefDto dto, String context)
	{
		requireField(dto.area, context, "requirements.diaries[].area");
		requireField(dto.tier, context, "requirements.diaries[].tier");
		try
		{
			return new DiaryRef(DiaryTier.valueOf(dto.area + "_" + dto.tier));
		}
		catch (IllegalArgumentException e)
		{
			throw new IllegalStateException(
				"Malformed knowledge base data: " + context + " has unknown diary \"" + dto.area + " " + dto.tier + "\"");
		}
	}

	private static ItemReq toMilestoneItemReq(MilestoneItemDto dto, String context)
	{
		requireField(dto.name, context, "requirements.items[].name");
		requireField(dto.id, context, "requirements.items[].id");
		List<String> sources = dto.sources == null ? List.of() : dto.sources;
		return new ItemReq(dto.name, dto.id, dto.quantity, sources);
	}

	private static OwnedItem toOwnedItem(OwnedIfDto dto, String context)
	{
		requireField(dto.name, context, "ownedIf[].name");
		requireField(dto.id, context, "ownedIf[].id");
		return new OwnedItem(dto.name, dto.id);
	}

	private static MilestoneCategory resolveMilestoneCategory(String raw, String context)
	{
		switch (raw)
		{
			case "gear":
				return MilestoneCategory.GEAR;
			case "unlock":
				return MilestoneCategory.UNLOCK;
			case "prayer":
				return MilestoneCategory.PRAYER;
			case "spellbook":
				return MilestoneCategory.SPELLBOOK;
			case "slayer_target":
				return MilestoneCategory.SLAYER_TARGET;
			case "boss":
				return MilestoneCategory.BOSS;
			default:
				throw new IllegalStateException("Malformed knowledge base data: " + context + " has unknown category \"" + raw + "\"");
		}
	}

	/** {@code context} is appended to the error when the name doesn't resolve; {@code null} omits it (quest/diary use). */
	private static Skill resolveSkill(String name, String context)
	{
		for (Skill skill : Skill.values())
		{
			if (skill.getName().equals(name))
			{
				return skill;
			}
		}
		String suffix = context == null ? "" : " (" + context + ")";
		throw new IllegalStateException("Unknown skill name in knowledge base: " + name + suffix);
	}

	public int getQuestsVersion()
	{
		return questsVersion;
	}

	public String getQuestsGeneratedAt()
	{
		return questsGeneratedAt;
	}

	public int getDiariesVersion()
	{
		return diariesVersion;
	}

	public String getDiariesGeneratedAt()
	{
		return diariesGeneratedAt;
	}

	public List<QuestEntry> getQuests()
	{
		return quests;
	}

	public List<DiaryEntry> getDiaries()
	{
		return diaries;
	}

	public List<MilestoneEntry> getMilestones()
	{
		return milestones;
	}

	public QuestEntry questById(int id)
	{
		return questsById.get(id);
	}

	public QuestEntry questByName(String name)
	{
		return questsByName.get(name);
	}

	public DiaryEntry diary(DiaryTier tier)
	{
		return diariesByTier.get(tier);
	}

	public MilestoneEntry milestoneById(String id)
	{
		return milestonesById.get(id);
	}

	public Map<String, Integer> getPriorityOverrides()
	{
		return priorityOverrides;
	}

	public Set<Integer> diaryVarps()
	{
		return diaryVarps;
	}

	public Set<Integer> diaryVarbits()
	{
		return diaryVarbits;
	}

	// --- Gson DTOs: plain mutable fields mirroring the JSON shape exactly, mapped above into the
	// immutable public model. Never exposed outside this class. ---

	private static final class QuestsFile
	{
		int version;
		String generatedAt;
		List<QuestDto> quests = new ArrayList<>();
	}

	private static final class DiariesFile
	{
		int version;
		String generatedAt;
		List<DiaryDto> diaries = new ArrayList<>();
	}

	private static final class MilestonesFile
	{
		int version;
		List<MilestoneDto> milestones = new ArrayList<>();
	}

	private static final class PrioritiesFile
	{
		int version;
		Map<String, Integer> overrides = new LinkedHashMap<>();
	}

	private static final class QuestDto
	{
		int id;
		String name;
		String wikiTitle;
		List<SkillDto> skills = new ArrayList<>();
		List<String> prereqs = new ArrayList<>();
		List<ItemDto> items = new ArrayList<>();
		int questPoints;
		String source;
	}

	private static final class SkillDto
	{
		String skill;
		int level;
		boolean boostable;
		boolean ironmanOnly;
	}

	private static final class ItemDto
	{
		String name;
		int quantity;
	}

	private static final class DiaryDto
	{
		String area;
		String tier;
		int tierVarbit;
		List<TaskDto> tasks = new ArrayList<>();
	}

	private static final class TaskDto
	{
		int ordinal;
		String text;
		List<SkillDto> skills = new ArrayList<>();
		List<String> quests = new ArrayList<>();
		List<String> items = new ArrayList<>();
		List<String> notes = new ArrayList<>();
		CompletionDto completion;
	}

	private static final class CompletionDto
	{
		Integer varp;
		Integer bit;
		Integer varbit;
		Integer doneMin;
	}

	private static final class MilestoneDto
	{
		String id;
		String category;
		String subcategory;
		String name;
		String wikiTitle;
		int priority;
		String reason;
		List<String> unlocks = new ArrayList<>();
		RequirementsDto requirements;
		List<OwnedIfDto> ownedIf = new ArrayList<>();
		Integer gearTier;
		List<String> sources = new ArrayList<>();
	}

	private static final class RequirementsDto
	{
		List<SkillDto> skills = new ArrayList<>();
		List<String> quests = new ArrayList<>();
		List<DiaryRefDto> diaries = new ArrayList<>();
		Integer combatLevel;
		Integer questPoints;
		List<MilestoneItemDto> items = new ArrayList<>();
	}

	private static final class DiaryRefDto
	{
		String area;
		String tier;
	}

	private static final class MilestoneItemDto
	{
		String name;
		Integer id;
		int quantity;
		List<String> sources = new ArrayList<>();
	}

	private static final class OwnedIfDto
	{
		String name;
		Integer id;
	}
}
