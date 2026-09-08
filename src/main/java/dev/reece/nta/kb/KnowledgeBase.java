package dev.reece.nta.kb;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import dev.reece.nta.engine.model.ItemSource;
import dev.reece.nta.snapshot.DiaryTier;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Quest;
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
	private static final String METHODS_RESOURCE = "/kb/methods.json";
	private static final String MATERIALS_RESOURCE = "/kb/materials.json";
	private static final String GATHERING_RESOURCE = "/kb/gathering.json";
	private static final String EMPTY_METHODS_JSON = "{\"version\":1,\"generatedAt\":\"x\",\"methods\":[]}";
	private static final String EMPTY_MATERIALS_JSON = "{\"version\":1,\"generatedAt\":\"x\",\"materials\":[]}";
	private static final String EMPTY_GATHERING_JSON = "{\"version\":1,\"plans\":[]}";
	/** Every RuneLite {@link Quest} by its exact name - what a gathering step's {@code requires.quests} must resolve to (task 62). */
	private static final Set<String> QUEST_NAMES = Arrays.stream(Quest.values()).map(Quest::getName).collect(Collectors.toSet());

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
	private final List<MethodEntry> methods;
	private final List<MaterialEntry> materials;
	private final List<GatheringPlan> gatheringPlans;
	private final Map<Integer, QuestEntry> questsById;
	private final Map<String, QuestEntry> questsByName;
	private final Map<DiaryTier, DiaryEntry> diariesByTier;
	private final Map<String, MilestoneEntry> milestonesById;
	private final Map<Skill, List<MethodEntry>> methodsBySkill;
	private final Map<String, MaterialEntry> materialsByName;
	private final Map<Integer, MaterialEntry> materialsById;
	private final Map<Integer, List<GatheringPlan>> gatheringByItemId;
	private final Map<String, List<GatheringPlan>> gatheringByItemName;
	private final Map<String, Integer> priorityOverrides;
	private final Set<Integer> diaryVarps;
	private final Set<Integer> diaryVarbits;

	private KnowledgeBase(
		int questsVersion, String questsGeneratedAt,
		int diariesVersion, String diariesGeneratedAt,
		List<QuestEntry> quests, List<DiaryEntry> diaries,
		List<MilestoneEntry> milestones, Map<String, Integer> priorityOverrides,
		List<MethodEntry> methods, List<MaterialEntry> materials, List<GatheringPlan> gatheringPlans)
	{
		this.questsVersion = questsVersion;
		this.questsGeneratedAt = questsGeneratedAt;
		this.diariesVersion = diariesVersion;
		this.diariesGeneratedAt = diariesGeneratedAt;
		this.quests = List.copyOf(quests);
		this.diaries = List.copyOf(diaries);
		this.milestones = List.copyOf(milestones);
		this.methods = List.copyOf(methods);
		this.materials = List.copyOf(materials);
		this.gatheringPlans = List.copyOf(gatheringPlans);
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

		Map<Skill, List<MethodEntry>> bySkill = new EnumMap<>(Skill.class);
		for (MethodEntry method : this.methods)
		{
			bySkill.computeIfAbsent(method.getSkill(), s -> new ArrayList<>()).add(method);
		}
		Map<Skill, List<MethodEntry>> immutableBySkill = new EnumMap<>(Skill.class);
		for (Map.Entry<Skill, List<MethodEntry>> e : bySkill.entrySet())
		{
			immutableBySkill.put(e.getKey(), List.copyOf(e.getValue()));
		}
		this.methodsBySkill = Map.copyOf(immutableBySkill);

		Map<String, MaterialEntry> byMaterialName = new LinkedHashMap<>();
		Map<Integer, MaterialEntry> byMaterialId = new LinkedHashMap<>();
		for (MaterialEntry material : this.materials)
		{
			byMaterialName.put(material.getName(), material);
			if (material.getId() != null)
			{
				byMaterialId.put(material.getId(), material);
			}
		}
		this.materialsByName = Map.copyOf(byMaterialName);
		this.materialsById = Map.copyOf(byMaterialId);

		Map<Integer, List<GatheringPlan>> byGatheringItemId = new LinkedHashMap<>();
		Map<String, List<GatheringPlan>> byGatheringItemName = new LinkedHashMap<>();
		for (GatheringPlan plan : this.gatheringPlans)
		{
			byGatheringItemId.computeIfAbsent(plan.getId(), id -> new ArrayList<>()).add(plan);
			byGatheringItemName.computeIfAbsent(plan.getItem(), name -> new ArrayList<>()).add(plan);
		}
		Map<Integer, List<GatheringPlan>> immutableByGatheringItemId = new LinkedHashMap<>();
		for (Map.Entry<Integer, List<GatheringPlan>> e : byGatheringItemId.entrySet())
		{
			immutableByGatheringItemId.put(e.getKey(), List.copyOf(e.getValue()));
		}
		Map<String, List<GatheringPlan>> immutableByGatheringItemName = new LinkedHashMap<>();
		for (Map.Entry<String, List<GatheringPlan>> e : byGatheringItemName.entrySet())
		{
			immutableByGatheringItemName.put(e.getKey(), List.copyOf(e.getValue()));
		}
		this.gatheringByItemId = Map.copyOf(immutableByGatheringItemId);
		this.gatheringByItemName = Map.copyOf(immutableByGatheringItemName);
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
		return of(questsVersion, questsGeneratedAt, diariesVersion, diariesGeneratedAt, quests, diaries, milestones, priorityOverrides,
			List.of(), List.of());
	}

	/** As {@link #of(int, String, int, String, List, List, List, Map)}, also seeding {@code methods}/{@code materials}. */
	public static KnowledgeBase of(
		int questsVersion, String questsGeneratedAt,
		int diariesVersion, String diariesGeneratedAt,
		List<QuestEntry> quests, List<DiaryEntry> diaries,
		List<MilestoneEntry> milestones, Map<String, Integer> priorityOverrides,
		List<MethodEntry> methods, List<MaterialEntry> materials)
	{
		return of(questsVersion, questsGeneratedAt, diariesVersion, diariesGeneratedAt, quests, diaries, milestones, priorityOverrides,
			methods, materials, List.of());
	}

	/** As {@link #of(int, String, int, String, List, List, List, Map, List, List)}, also seeding {@code gatheringPlans}. */
	public static KnowledgeBase of(
		int questsVersion, String questsGeneratedAt,
		int diariesVersion, String diariesGeneratedAt,
		List<QuestEntry> quests, List<DiaryEntry> diaries,
		List<MilestoneEntry> milestones, Map<String, Integer> priorityOverrides,
		List<MethodEntry> methods, List<MaterialEntry> materials, List<GatheringPlan> gatheringPlans)
	{
		return new KnowledgeBase(questsVersion, questsGeneratedAt, diariesVersion, diariesGeneratedAt, quests, diaries, milestones,
			priorityOverrides, methods, materials, gatheringPlans);
	}

	public static KnowledgeBase load(Gson gson)
	{
		QuestsFile questsFile = readResource(gson, QUESTS_RESOURCE, QuestsFile.class);
		DiariesFile diariesFile = readResource(gson, DIARIES_RESOURCE, DiariesFile.class);
		MilestonesFile milestonesFile = readResource(gson, MILESTONES_RESOURCE, MilestonesFile.class);
		PrioritiesFile prioritiesFile = readResource(gson, PRIORITIES_RESOURCE, PrioritiesFile.class);
		MethodsFile methodsFile = readResource(gson, METHODS_RESOURCE, MethodsFile.class);
		MaterialsFile materialsFile = readResource(gson, MATERIALS_RESOURCE, MaterialsFile.class);
		GatheringFile gatheringFile = readResource(gson, GATHERING_RESOURCE, GatheringFile.class);
		return build(questsFile, diariesFile, milestonesFile, prioritiesFile, methodsFile, materialsFile, gatheringFile);
	}

	/**
	 * Package-visible for tests: maps already-parsed JSON strings the same way {@link #load} maps
	 * the bundled classpath resources, without touching the classpath.
	 */
	static KnowledgeBase fromJson(Gson gson, String questsJson, String diariesJson, String milestonesJson, String prioritiesJson)
	{
		return fromJson(gson, questsJson, diariesJson, milestonesJson, prioritiesJson, EMPTY_METHODS_JSON, EMPTY_MATERIALS_JSON);
	}

	/** As above, also mapping {@code methodsJson}/{@code materialsJson}. */
	static KnowledgeBase fromJson(Gson gson, String questsJson, String diariesJson, String milestonesJson, String prioritiesJson,
		String methodsJson, String materialsJson)
	{
		return fromJson(gson, questsJson, diariesJson, milestonesJson, prioritiesJson, methodsJson, materialsJson, EMPTY_GATHERING_JSON);
	}

	/** As above, also mapping {@code gatheringJson}. */
	static KnowledgeBase fromJson(Gson gson, String questsJson, String diariesJson, String milestonesJson, String prioritiesJson,
		String methodsJson, String materialsJson, String gatheringJson)
	{
		return build(
			gson.fromJson(questsJson, QuestsFile.class),
			gson.fromJson(diariesJson, DiariesFile.class),
			gson.fromJson(milestonesJson, MilestonesFile.class),
			gson.fromJson(prioritiesJson, PrioritiesFile.class),
			gson.fromJson(methodsJson, MethodsFile.class),
			gson.fromJson(materialsJson, MaterialsFile.class),
			gson.fromJson(gatheringJson, GatheringFile.class));
	}

	private static KnowledgeBase build(QuestsFile questsFile, DiariesFile diariesFile, MilestonesFile milestonesFile,
		PrioritiesFile prioritiesFile, MethodsFile methodsFile, MaterialsFile materialsFile, GatheringFile gatheringFile)
	{
		requireField(questsFile.quests, QUESTS_RESOURCE, "quests");
		requireField(diariesFile.diaries, DIARIES_RESOURCE, "diaries");
		requireField(milestonesFile.milestones, MILESTONES_RESOURCE, "milestones");
		requireField(prioritiesFile.overrides, PRIORITIES_RESOURCE, "overrides");
		requireField(methodsFile.methods, METHODS_RESOURCE, "methods");
		requireField(materialsFile.materials, MATERIALS_RESOURCE, "materials");
		requireField(gatheringFile.plans, GATHERING_RESOURCE, "plans");

		List<QuestEntry> quests = questsFile.quests.stream().map(KnowledgeBase::toQuestEntry).collect(Collectors.toList());
		List<DiaryEntry> diaries = diariesFile.diaries.stream().map(KnowledgeBase::toDiaryEntry).collect(Collectors.toList());

		Set<String> questNames = quests.stream().map(QuestEntry::getName).collect(Collectors.toSet());
		validatePrereqsResolve(quests, questNames);

		Set<String> milestoneIds = new HashSet<>();
		boolean[] anyMissingStage = {false};
		List<MilestoneEntry> milestones = milestonesFile.milestones.stream()
			.map(dto -> toMilestoneEntry(dto, questNames, milestoneIds, anyMissingStage))
			.collect(Collectors.toList());
		if (anyMissingStage[0])
		{
			log.warn("milestones.json has entries with no 'stage' field (bundled data predates S4.1 stages); "
				+ "defaulting stage=2 and recommended=null for those entries");
		}
		validateObtainedFromResolves(milestones, milestoneIds);

		List<MaterialEntry> materials = materialsFile.materials.stream().map(KnowledgeBase::toMaterialEntry).collect(Collectors.toList());
		Map<String, Integer> materialIdsByName = new LinkedHashMap<>();
		for (MaterialEntry material : materials)
		{
			materialIdsByName.put(material.getName(), material.getId());
		}
		List<MethodEntry> methods = methodsFile.methods.stream().map(dto -> toMethodEntry(dto, materialIdsByName)).collect(Collectors.toList());
		List<GatheringPlan> gatheringPlans = gatheringFile.plans.stream().map(KnowledgeBase::toGatheringPlan).collect(Collectors.toList());

		return new KnowledgeBase(
			questsFile.version, questsFile.generatedAt,
			diariesFile.version, diariesFile.generatedAt,
			quests, diaries, milestones, prioritiesFile.overrides, methods, materials, gatheringPlans);
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
		requireField(dto.prereqsStarted, context, "prereqsStarted");
		requireField(dto.prereqNotes, context, "prereqNotes");
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
		return new QuestEntry(dto.id, dto.name, dto.wikiTitle, skills, List.copyOf(dto.prereqs), List.copyOf(dto.prereqsStarted),
			List.copyOf(dto.prereqNotes), items, dto.questPoints, dto.source, questPointsRequired, kudosRequired, combatLevelRequired);
	}

	/** Fails loudly, naming the quest and the unresolved prereq, if any {@code prereqs}/{@code prereqsStarted} name isn't another known quest. */
	private static void validatePrereqsResolve(List<QuestEntry> quests, Set<String> questNames)
	{
		for (QuestEntry quest : quests)
		{
			for (String prereq : quest.getPrereqs())
			{
				if (!questNames.contains(prereq))
				{
					throw new IllegalStateException(
						"Malformed knowledge base data: quest \"" + quest.getName() + "\" has unknown prereq \"" + prereq + "\"");
				}
			}
			for (String prereq : quest.getPrereqsStarted())
			{
				if (!questNames.contains(prereq))
				{
					throw new IllegalStateException(
						"Malformed knowledge base data: quest \"" + quest.getName() + "\" has unknown started prereq \"" + prereq + "\"");
				}
			}
		}
	}

	/** Fails loudly, naming the milestone and the unresolved id, if any {@code obtainedFrom} doesn't name another known milestone. */
	private static void validateObtainedFromResolves(List<MilestoneEntry> milestones, Set<String> milestoneIds)
	{
		for (MilestoneEntry milestone : milestones)
		{
			if (milestone.getObtainedFrom() != null && !milestoneIds.contains(milestone.getObtainedFrom()))
			{
				throw new IllegalStateException("Malformed knowledge base data: milestone \"" + milestone.getId()
					+ "\" has unknown obtainedFrom \"" + milestone.getObtainedFrom() + "\"");
			}
		}
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

	private static MilestoneEntry toMilestoneEntry(MilestoneDto dto, Set<String> questNames, Set<String> milestoneIds, boolean[] anyMissingStage)
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
		int ownedIfMin = dto.ownedIfMin == null ? 1 : dto.ownedIfMin;
		if (ownedIfMin < 1 || ownedIfMin > Math.max(1, ownedIf.size()))
		{
			throw new IllegalStateException("Malformed knowledge base data: " + context + " has ownedIfMin " + ownedIfMin
				+ " outside 1.." + ownedIf.size() + " (its ownedIf count)");
		}

		int stage;
		if (dto.stage == null)
		{
			anyMissingStage[0] = true;
			stage = 2;
		}
		else
		{
			stage = dto.stage;
			if (stage < 1 || stage > 4)
			{
				throw new IllegalStateException("Malformed knowledge base data: " + context + " has stage " + stage + " outside 1..4");
			}
		}
		RecommendedProfile recommended = toRecommendedProfile(dto.recommended, context);

		return new MilestoneEntry(dto.id, category, dto.subcategory, dto.name, dto.wikiTitle, dto.priority, dto.reason,
			List.copyOf(dto.unlocks), skills, List.copyOf(dto.requirements.quests), diaries, dto.requirements.combatLevel,
			dto.requirements.questPoints, items, ownedIf, dto.gearTier, List.copyOf(dto.sources), stage, recommended, dto.obtainedFrom,
			ownedIfMin, dto.speedsUp == null ? null : resolveSkill(dto.speedsUp, context));
	}

	/** {@code dto} is {@code null} for a milestone with no {@code recommended} profile (the common case). */
	private static RecommendedProfile toRecommendedProfile(RecommendedDto dto, String context)
	{
		if (dto == null)
		{
			return null;
		}
		requireField(dto.skills, context, "recommended.skills");
		requireField(dto.gearOwnedAny, context, "recommended.gearOwnedAny");

		List<RecommendedSkill> skills = dto.skills.stream()
			.map(s -> new RecommendedSkill(resolveSkill(s.skill, context), s.level))
			.collect(Collectors.toList());
		List<OwnedItem> gearOwnedAny = dto.gearOwnedAny.stream().map(o -> toOwnedItem(o, context)).collect(Collectors.toList());
		return new RecommendedProfile(skills, dto.combatLevel, gearOwnedAny, dto.gearOwnedMin);
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
		List<Integer> ids = dto.ids == null ? List.of(dto.id) : dto.ids;
		return new ItemReq(dto.name, dto.id, dto.quantity, sources, ids);
	}

	private static OwnedItem toOwnedItem(OwnedIfDto dto, String context)
	{
		requireField(dto.name, context, "ownedIf[].name");
		requireField(dto.id, context, "ownedIf[].id");
		requireField(dto.ids, context, "ownedIf[].ids");
		return new OwnedItem(dto.name, dto.id, dto.ids);
	}

	private static MaterialEntry toMaterialEntry(MaterialDto dto)
	{
		requireField(dto.name, "materials.json entry", "name");
		String context = "material \"" + dto.name + "\"";
		requireField(dto.sources, context, "sources");

		List<ItemSource> sources = dto.sources.stream()
			.map(s -> new ItemSource(s.type, s.where, s.detail))
			.collect(Collectors.toList());
		String wikiUrl = dto.wikiUrl != null ? dto.wikiUrl : WikiUrls.forTitle(dto.name);
		return new MaterialEntry(dto.name, dto.id, dto.generic, wikiUrl, List.copyOf(sources));
	}

	private static MethodEntry toMethodEntry(MethodDto dto, Map<String, Integer> materialIdsByName)
	{
		String context = "method \"" + dto.name + "\"";
		requireField(dto.skill, context, "skill");
		requireField(dto.name, context, "name");
		requireField(dto.materials, context, "materials");
		requireField(dto.outputs, context, "outputs");
		requireField(dto.types, context, "types");

		if (dto.levelReq < 1 || dto.levelReq > 99)
		{
			throw new IllegalStateException("Malformed knowledge base data: " + context + " has levelReq " + dto.levelReq + " outside 1..99");
		}
		if (dto.xpPerAction < 0)
		{
			throw new IllegalStateException("Malformed knowledge base data: " + context + " has negative xpPerAction " + dto.xpPerAction);
		}
		Skill skill = resolveSkill(dto.skill, context);

		// usable depends only on materials (inputs) resolving to an id: a method the route planner
		// can never afford (a required input has no known item) is unusable. An unresolved OUTPUT
		// (e.g. a generic-named byproduct) doesn't block the method - RoutePlanner just doesn't add
		// it to the simulated bank (see ItemQuantity#getId()). This matters a lot in practice: 281
		// of Magic's 286 methods have a generic output (e.g. rune/tablet-adjacent byproducts) and
		// would otherwise be wrongly excluded from every route.
		boolean[] usable = {true};
		List<ItemQuantity> materials = dto.materials.stream().map(i -> toItemQuantity(i, materialIdsByName, usable)).collect(Collectors.toList());
		// Final-review C1: an output that is also a material is the action's *subject* (bury Dragon
		// bones, burn Yew logs), not a product; keeping it would make RoutePlanner's simulated bank
		// self-replenishing. kb-build no longer emits these, but a stale JSON must not reintroduce it.
		Set<String> materialNames = dto.materials.stream().map(i -> i.name).collect(Collectors.toSet());
		List<ItemQuantity> outputs = dto.outputs.stream()
			.filter(i -> !materialNames.contains(i.name))
			.map(i -> toItemQuantity(i, materialIdsByName, null))
			.collect(Collectors.toList());

		return new MethodEntry(skill, dto.name, dto.title, dto.levelReq, dto.xpPerAction, List.copyOf(materials), List.copyOf(outputs),
			List.copyOf(dto.types), dto.members, Boolean.TRUE.equals(dto.boostable), dto.ticks, Boolean.TRUE.equals(dto.intermediate),
			usable[0]);
	}

	/** {@code usable} (when non-null) is flipped to {@code false} (in place) when {@code dto.name} has no {@code materials.json} entry. */
	private static ItemQuantity toItemQuantity(ItemQtyDto dto, Map<String, Integer> materialIdsByName, boolean[] usable)
	{
		Integer id = materialIdsByName.get(dto.name);
		if (id == null && usable != null)
		{
			usable[0] = false;
		}
		return new ItemQuantity(dto.name, id, dto.quantity);
	}

	private static GatheringPlan toGatheringPlan(GatheringPlanDto dto)
	{
		requireField(dto.item, "gathering.json entry", "item");
		String context = "gathering plan \"" + dto.item + "\"";
		requireField(dto.id, context, "id");
		requireField(dto.title, context, "title");
		requireField(dto.requires, context, "requires");
		requireField(dto.steps, context, "steps");
		requireField(dto.alternatives, context, "alternatives");
		requireField(dto.wikiUrl, context, "wikiUrl");
		validateStepCount(dto.steps, context);

		GatheringRequires requires = toGatheringRequires(dto.requires, context);
		List<GatheringAlternative> alternatives = dto.alternatives.stream()
			.map(a -> toGatheringAlternative(a, context)).collect(Collectors.toList());

		return new GatheringPlan(dto.item, dto.id, dto.title, requires, dto.ratePerHour, toGatheringSteps(dto.steps, context),
			List.copyOf(alternatives), dto.wikiUrl);
	}

	private static GatheringAlternative toGatheringAlternative(GatheringAlternativeDto dto, String context)
	{
		requireField(dto.title, context, "alternatives[].title");
		requireField(dto.steps, context, "alternatives[].steps");
		requireField(dto.requires, context, "alternatives[].requires");
		validateStepCount(dto.steps, context);
		return new GatheringAlternative(dto.title, toGatheringSteps(dto.steps, context), toGatheringRequires(dto.requires, context));
	}

	/** Task 62: each step's text must be non-blank and its requirements name real skills and RuneLite {@link Quest}s (exact name). */
	private static List<GatheringStep> toGatheringSteps(List<GatheringStepDto> dtos, String context)
	{
		List<GatheringStep> steps = new ArrayList<>();
		for (int i = 0; i < dtos.size(); i++)
		{
			GatheringStepDto dto = dtos.get(i);
			String stepContext = context + " step " + (i + 1);
			if (dto == null || dto.text == null || dto.text.isBlank())
			{
				throw new IllegalStateException("Malformed knowledge base data: " + stepContext + " has blank text");
			}
			requireField(dto.requires, stepContext, "requires");
			requireField(dto.requires.skills, stepContext, "requires.skills");
			requireField(dto.requires.quests, stepContext, "requires.quests");
			List<SkillReq> skills = dto.requires.skills.stream()
				.map(s -> new SkillReq(resolveSkill(s.skill, stepContext), s.level, false, false))
				.collect(Collectors.toList());
			for (String questName : dto.requires.quests)
			{
				if (!QUEST_NAMES.contains(questName))
				{
					throw new IllegalStateException("Malformed knowledge base data: " + stepContext + " requires unknown quest \"" + questName + "\"");
				}
			}
			steps.add(new GatheringStep(dto.text, new GatheringRequires(skills, null, List.copyOf(dto.requires.quests), List.of(), null)));
		}
		return List.copyOf(steps);
	}

	private static GatheringRequires toGatheringRequires(GatheringRequiresDto dto, String context)
	{
		requireField(dto.skills, context, "requires.skills");
		requireField(dto.quests, context, "requires.quests");
		requireField(dto.items, context, "requires.items");

		List<SkillReq> skills = new ArrayList<>();
		Integer combatLevel = null;
		for (SkillDto s : dto.skills)
		{
			if (s.skill.equals(COMBAT_SKILL))
			{
				combatLevel = s.level;
			}
			else
			{
				skills.add(new SkillReq(resolveSkill(s.skill, context), s.level, false, false));
			}
		}
		return new GatheringRequires(skills, combatLevel, List.copyOf(dto.quests), List.copyOf(dto.items), dto.notes);
	}

	/** Fails loudly, naming the plan/alternative, when {@code steps} is empty or implausibly long (the 12-step herb farm loops are the longest curated plans). */
	private static void validateStepCount(List<?> steps, String context)
	{
		if (steps.isEmpty() || steps.size() > 15)
		{
			throw new IllegalStateException("Malformed knowledge base data: " + context + " has " + steps.size() + " steps outside 1..15");
		}
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

	public List<MethodEntry> getMethods()
	{
		return methods;
	}

	public List<MaterialEntry> getMaterials()
	{
		return materials;
	}

	/** Every {@link MethodEntry} (real and {@code intermediate}) for {@code skill}, or empty if none. */
	public List<MethodEntry> methodsFor(Skill skill)
	{
		return methodsBySkill.getOrDefault(skill, List.of());
	}

	public MaterialEntry materialByName(String name)
	{
		return materialsByName.get(name);
	}

	public MaterialEntry materialById(int id)
	{
		return materialsById.get(id);
	}

	public List<GatheringPlan> getGatheringPlans()
	{
		return gatheringPlans;
	}

	/** Curated gathering plans (spec ruling 28) targeting {@code itemId}, or empty if none. */
	public List<GatheringPlan> gatheringFor(int itemId)
	{
		return gatheringByItemId.getOrDefault(itemId, List.of());
	}

	/** As {@link #gatheringFor(int)}, matched by item name instead - the fallback when an {@code id} isn't known. */
	public List<GatheringPlan> gatheringForName(String name)
	{
		return gatheringByItemName.getOrDefault(name, List.of());
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
		List<String> prereqsStarted = new ArrayList<>();
		List<String> prereqNotes = new ArrayList<>();
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
		Integer stage;
		RecommendedDto recommended;
		String obtainedFrom;
		Integer ownedIfMin;
		String speedsUp;
	}

	private static final class RecommendedDto
	{
		List<SkillDto> skills = new ArrayList<>();
		Integer combatLevel;
		List<OwnedIfDto> gearOwnedAny = new ArrayList<>();
		Integer gearOwnedMin;
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
		List<Integer> ids;
	}

	private static final class OwnedIfDto
	{
		String name;
		Integer id;
		List<Integer> ids;
	}

	private static final class MethodsFile
	{
		int version;
		String generatedAt;
		List<MethodDto> methods = new ArrayList<>();
	}

	private static final class MethodDto
	{
		String skill;
		String name;
		String title;
		int levelReq;
		double xpPerAction;
		List<ItemQtyDto> materials = new ArrayList<>();
		List<ItemQtyDto> outputs = new ArrayList<>();
		List<String> types = new ArrayList<>();
		boolean members;
		Boolean boostable;
		Integer ticks;
		Boolean intermediate;
	}

	private static final class ItemQtyDto
	{
		String name;
		double quantity;
	}

	private static final class MaterialsFile
	{
		int version;
		String generatedAt;
		List<MaterialDto> materials = new ArrayList<>();
	}

	private static final class MaterialDto
	{
		String name;
		Integer id;
		boolean generic;
		String wikiUrl;
		List<SourceDto> sources = new ArrayList<>();
	}

	private static final class SourceDto
	{
		String type;
		String where;
		String detail;
		List<String> accountTypes;
	}

	private static final class GatheringFile
	{
		int version;
		List<GatheringPlanDto> plans = new ArrayList<>();
	}

	private static final class GatheringPlanDto
	{
		String item;
		Integer id;
		String title;
		GatheringRequiresDto requires;
		Integer ratePerHour;
		List<GatheringStepDto> steps = new ArrayList<>();
		List<GatheringAlternativeDto> alternatives = new ArrayList<>();
		String wikiUrl;
	}

	private static final class GatheringAlternativeDto
	{
		String title;
		List<GatheringStepDto> steps = new ArrayList<>();
		GatheringRequiresDto requires;
	}

	private static final class GatheringStepDto
	{
		String text;
		GatheringStepRequiresDto requires;
	}

	private static final class GatheringStepRequiresDto
	{
		List<SkillDto> skills = new ArrayList<>();
		List<String> quests = new ArrayList<>();
	}

	private static final class GatheringRequiresDto
	{
		List<SkillDto> skills = new ArrayList<>();
		List<String> quests = new ArrayList<>();
		List<String> items = new ArrayList<>();
		String notes;
	}
}
