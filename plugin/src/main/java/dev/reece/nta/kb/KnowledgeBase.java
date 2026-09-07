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
import net.runelite.api.Skill;

/**
 * The bundled quest and achievement diary requirements, loaded once from the classpath resources
 * {@code /kb/quests.json} and {@code /kb/diaries.json}. Pure data: no {@link net.runelite.api.Client},
 * no I/O beyond the initial classpath read in {@link #load(Gson)}.
 */
public final class KnowledgeBase
{
	private static final String QUESTS_RESOURCE = "/kb/quests.json";
	private static final String DIARIES_RESOURCE = "/kb/diaries.json";

	/** Skill names the data source uses that have no {@link Skill} constant. */
	private static final Set<String> PSEUDO_SKILLS = Set.of("Quest point", "Quest", "Kudos", "Combat");

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

		List<QuestEntry> quests = questsFile.quests.stream().map(KnowledgeBase::toQuestEntry).collect(Collectors.toList());
		List<DiaryEntry> diaries = diariesFile.diaries.stream().map(KnowledgeBase::toDiaryEntry).collect(Collectors.toList());

		return new KnowledgeBase(
			questsFile.version, questsFile.generatedAt,
			diariesFile.version, diariesFile.generatedAt,
			quests, diaries);
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
		List<SkillReq> skills = dto.skills.stream().map(KnowledgeBase::toSkillReq).collect(Collectors.toList());
		List<ItemReq> items = dto.items.stream().map(i -> new ItemReq(i.name, i.quantity)).collect(Collectors.toList());
		return new QuestEntry(dto.id, dto.name, dto.wikiTitle, skills, List.copyOf(dto.prereqs), items, dto.questPoints, dto.source);
	}

	private static DiaryEntry toDiaryEntry(DiaryDto dto)
	{
		DiaryTier tier = DiaryTier.valueOf(dto.area + "_" + dto.tier);
		List<DiaryTask> tasks = dto.tasks.stream().map(KnowledgeBase::toDiaryTask).collect(Collectors.toList());
		return new DiaryEntry(tier, dto.tierVarbit, tasks);
	}

	private static DiaryTask toDiaryTask(TaskDto dto)
	{
		List<SkillReq> skills = dto.skills.stream().map(KnowledgeBase::toSkillReq).collect(Collectors.toList());
		TaskCompletion completion = new TaskCompletion(dto.completion.varp, dto.completion.bit, dto.completion.varbit, dto.completion.doneMin);
		return new DiaryTask(dto.ordinal, dto.text, skills, List.copyOf(dto.quests), List.copyOf(dto.items), List.copyOf(dto.notes), completion);
	}

	private static SkillReq toSkillReq(SkillDto dto)
	{
		Skill skill = resolveSkill(dto.skill);
		return new SkillReq(skill, dto.skill, dto.level, dto.boostable, dto.ironmanOnly);
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
		if (PSEUDO_SKILLS.contains(name))
		{
			return null;
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
