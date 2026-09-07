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
 * The bundled quest and achievement diary requirements, loaded once from the classpath resources
 * {@code /kb/quests.json} and {@code /kb/diaries.json}. Pure data: no {@link net.runelite.api.Client},
 * no I/O beyond the initial classpath read in {@link #load(Gson)}.
 */
@Slf4j
public final class KnowledgeBase
{
	private static final String QUESTS_RESOURCE = "/kb/quests.json";
	private static final String DIARIES_RESOURCE = "/kb/diaries.json";

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
	private final Map<Integer, QuestEntry> questsById;
	private final Map<String, QuestEntry> questsByName;
	private final Map<DiaryTier, DiaryEntry> diariesByTier;
	private final Set<Integer> diaryVarps;
	private final Set<Integer> diaryVarbits;

	private KnowledgeBase(
		int questsVersion, String questsGeneratedAt,
		int diariesVersion, String diariesGeneratedAt,
		List<QuestEntry> quests, List<DiaryEntry> diaries)
	{
		this.questsVersion = questsVersion;
		this.questsGeneratedAt = questsGeneratedAt;
		this.diariesVersion = diariesVersion;
		this.diariesGeneratedAt = diariesGeneratedAt;
		this.quests = List.copyOf(quests);
		this.diaries = List.copyOf(diaries);

		Map<Integer, QuestEntry> byId = new LinkedHashMap<>();
		Map<String, QuestEntry> byName = new LinkedHashMap<>();
		for (QuestEntry quest : quests)
		{
			byId.put(quest.getId(), quest);
			byName.put(quest.getName(), quest);
		}
		this.questsById = Map.copyOf(byId);
		this.questsByName = Map.copyOf(byName);

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
		List<QuestEntry> quests, List<DiaryEntry> diaries)
	{
		return new KnowledgeBase(questsVersion, questsGeneratedAt, diariesVersion, diariesGeneratedAt, quests, diaries);
	}

	public static KnowledgeBase load(Gson gson)
	{
		QuestsFile questsFile = readResource(gson, QUESTS_RESOURCE, QuestsFile.class);
		DiariesFile diariesFile = readResource(gson, DIARIES_RESOURCE, DiariesFile.class);
		return build(questsFile, diariesFile);
	}

	/**
	 * Package-visible for tests: maps already-parsed JSON strings the same way {@link #load} maps
	 * the bundled classpath resources, without touching the classpath.
	 */
	static KnowledgeBase fromJson(Gson gson, String questsJson, String diariesJson)
	{
		return build(gson.fromJson(questsJson, QuestsFile.class), gson.fromJson(diariesJson, DiariesFile.class));
	}

	private static KnowledgeBase build(QuestsFile questsFile, DiariesFile diariesFile)
	{
		requireField(questsFile.quests, QUESTS_RESOURCE, "quests");
		requireField(diariesFile.diaries, DIARIES_RESOURCE, "diaries");

		List<QuestEntry> quests = questsFile.quests.stream().map(KnowledgeBase::toQuestEntry).collect(Collectors.toList());
		List<DiaryEntry> diaries = diariesFile.diaries.stream().map(KnowledgeBase::toDiaryEntry).collect(Collectors.toList());

		return new KnowledgeBase(
			questsFile.version, questsFile.generatedAt,
			diariesFile.version, diariesFile.generatedAt,
			quests, diaries);
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
		return new SkillReq(resolveSkill(dto.skill), dto.level, dto.boostable, dto.ironmanOnly);
	}

	private static Skill resolveSkill(String name)
	{
		for (Skill skill : Skill.values())
		{
			if (skill.getName().equals(name))
			{
				return skill;
			}
		}
		throw new IllegalStateException("Unknown skill name in knowledge base: " + name);
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
}
