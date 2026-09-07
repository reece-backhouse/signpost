package dev.reece.nta.engine;

import dev.reece.nta.engine.model.CombatLevelGap;
import dev.reece.nta.engine.model.DiaryTaskGap;
import dev.reece.nta.engine.model.DiaryTierGap;
import dev.reece.nta.engine.model.Gap;
import dev.reece.nta.engine.model.Goal;
import dev.reece.nta.engine.model.GoalCategory;
import dev.reece.nta.engine.model.GoalStatus;
import dev.reece.nta.engine.model.ItemGap;
import dev.reece.nta.engine.model.ItemSource;
import dev.reece.nta.engine.model.KudosGap;
import dev.reece.nta.engine.model.QuestPointsGap;
import dev.reece.nta.engine.model.QuestPrereqGap;
import dev.reece.nta.engine.model.SkillLevelGap;
import dev.reece.nta.kb.DiaryEntry;
import dev.reece.nta.kb.DiaryRef;
import dev.reece.nta.kb.DiaryTask;
import dev.reece.nta.kb.ItemReq;
import dev.reece.nta.kb.KnowledgeBase;
import dev.reece.nta.kb.MilestoneCategory;
import dev.reece.nta.kb.MilestoneEntry;
import dev.reece.nta.kb.OwnedItem;
import dev.reece.nta.kb.QuestEntry;
import dev.reece.nta.kb.SkillReq;
import dev.reece.nta.snapshot.AccountType;
import dev.reece.nta.snapshot.DiaryTier;
import dev.reece.nta.snapshot.SkillState;
import dev.reece.nta.snapshot.Snapshot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.runelite.api.Experience;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;

/**
 * Pure function of {@link Snapshot} and {@link KnowledgeBase}: for every unfinished quest and
 * incomplete diary tier, works out exactly what's missing. No {@link net.runelite.api.Client}, no
 * I/O (global constraint: engine code is pure).
 */
public final class GapEngine
{
	private static final int QUEST_PRIORITY = 5;
	private static final int DIARY_PRIORITY = 4;
	private static final String WIKI_BASE = "https://oldschool.runescape.wiki/w/";

	private static final Map<Integer, Quest> QUESTS_BY_ID = buildQuestsById();
	private static final Map<String, Quest> QUESTS_BY_NAME = buildQuestsByName();

	private final BoostTable boostTable;

	public GapEngine(BoostTable boostTable)
	{
		this.boostTable = boostTable;
	}

	public List<GoalStatus> evaluate(Snapshot snapshot, KnowledgeBase kb)
	{
		List<GoalStatus> result = new ArrayList<>();

		for (QuestEntry entry : kb.getQuests())
		{
			Quest quest = QUESTS_BY_ID.get(entry.getId());
			if (quest == null)
			{
				// A kb entry with no matching RuneLite Quest constant (e.g. a miniquest) - out of scope.
				continue;
			}
			QuestState state = snapshot.getQuests().getOrDefault(quest, QuestState.NOT_STARTED);
			if (state == QuestState.FINISHED)
			{
				continue;
			}
			result.add(questGoalStatus(quest, entry, snapshot, kb));
		}

		for (DiaryTier tier : DiaryTier.values())
		{
			DiaryEntry entry = kb.diary(tier);
			if (entry == null)
			{
				continue;
			}
			if (Boolean.TRUE.equals(snapshot.getDiaryTiers().get(tier)))
			{
				continue;
			}
			result.add(diaryGoalStatus(tier, entry, snapshot, kb));
		}

		for (MilestoneEntry entry : kb.getMilestones())
		{
			milestoneGoalStatus(entry, snapshot, kb).ifPresent(result::add);
		}

		result.sort(Comparator.comparing(gs -> gs.getGoal().getId()));
		return result;
	}

	/**
	 * Drops sources with type {@code "GE"} for iron account types (ruling 22: group irons have no
	 * GE either, so every iron type is filtered the same way). A normal account keeps every source.
	 */
	public static List<ItemSource> sourcesFor(List<ItemSource> sources, AccountType accountType)
	{
		if (!accountType.isIron())
		{
			return List.copyOf(sources);
		}
		return sources.stream().filter(s -> !"GE".equals(s.getType())).collect(Collectors.toUnmodifiableList());
	}

	/** True when {@code accountType} is an iron type, {@code sources} was non-empty, and filtering it left nothing. */
	public static boolean mustObtain(List<ItemSource> sources, AccountType accountType)
	{
		return accountType.isIron() && !sources.isEmpty() && sourcesFor(sources, accountType).isEmpty();
	}

	private GoalStatus questGoalStatus(Quest quest, QuestEntry entry, Snapshot snapshot, KnowledgeBase kb)
	{
		List<Gap> gaps = new ArrayList<>();
		boolean isIron = snapshot.getAccountType().isIron();

		for (SkillReq req : entry.getSkills())
		{
			if (req.isIronmanOnly() && !isIron)
			{
				continue;
			}
			addSkillGapIfShort(gaps, snapshot, req);
		}

		Map<String, QuestPrereqGap> prereqGaps = new LinkedHashMap<>();
		// Seed the recursion path with the quest's own name so a prerequisite cycle that loops back
		// to this quest is treated as "already visiting" rather than adding the quest as its own gap.
		Set<String> path = new LinkedHashSet<>();
		path.add(entry.getName());
		resolvePrereqs(entry.getPrereqs(), snapshot, kb, entry.getName(), true, prereqGaps, new ArrayList<>(), path);
		resolveStartedPrereqs(entry.getPrereqsStarted(), snapshot, entry.getName(), prereqGaps);
		gaps.addAll(prereqGaps.values());

		for (ItemReq req : entry.getItems())
		{
			addItemGapIfShort(gaps, snapshot, req.getName(), req.getQuantity());
		}

		if (entry.getQuestPointsRequired() != null && entry.getQuestPointsRequired() > snapshot.getQuestPoints())
		{
			gaps.add(new QuestPointsGap(snapshot.getQuestPoints(), entry.getQuestPointsRequired()));
		}
		if (entry.getKudosRequired() != null && entry.getKudosRequired() > snapshot.getKudos())
		{
			gaps.add(new KudosGap(snapshot.getKudos(), entry.getKudosRequired()));
		}
		if (entry.getCombatLevelRequired() != null && entry.getCombatLevelRequired() > snapshot.combatLevel())
		{
			gaps.add(new CombatLevelGap(snapshot.combatLevel(), entry.getCombatLevelRequired()));
		}

		Goal goal = new Goal("quest:" + entry.getId(), GoalCategory.QUEST, entry.getName(), WIKI_BASE + spacesToUnderscores(entry.getWikiTitle()),
			QUEST_PRIORITY);
		return toGoalStatus(goal, gaps, entry.getPrereqNotes());
	}

	private GoalStatus diaryGoalStatus(DiaryTier tier, DiaryEntry entry, Snapshot snapshot, KnowledgeBase kb)
	{
		List<Gap> gaps = new ArrayList<>();
		for (DiaryTask task : entry.getTasks())
		{
			if (task.getCompletion().isComplete(snapshot))
			{
				continue;
			}
			gaps.add(diaryTaskGap(task, snapshot, kb));
		}

		String area = diaryArea(tier);
		String tierName = diaryTierName(tier);
		Goal goal = new Goal("diary:" + tier.name(), GoalCategory.DIARY, area + " " + tierName + " Diary",
			WIKI_BASE + spacesToUnderscores(area) + "_Diary", DIARY_PRIORITY);
		return toGoalStatus(goal, gaps);
	}

	private DiaryTaskGap diaryTaskGap(DiaryTask task, Snapshot snapshot, KnowledgeBase kb)
	{
		List<Gap> inner = new ArrayList<>();
		boolean isIron = snapshot.getAccountType().isIron();

		for (SkillReq req : task.getSkills())
		{
			if (req.isIronmanOnly() && !isIron)
			{
				continue;
			}
			addSkillGapIfShort(inner, snapshot, req);
		}

		Map<String, QuestPrereqGap> questGaps = new LinkedHashMap<>();
		List<String> extraNotes = new ArrayList<>();
		resolvePrereqs(task.getQuests(), snapshot, kb, "diary task " + task.getOrdinal(), false, questGaps, extraNotes, new LinkedHashSet<>());
		inner.addAll(questGaps.values());

		for (String itemName : task.getItems())
		{
			addItemGapIfShort(inner, snapshot, itemName, 1);
		}

		if (task.getCombatLevelRequired() != null && task.getCombatLevelRequired() > snapshot.combatLevel())
		{
			inner.add(new CombatLevelGap(snapshot.combatLevel(), task.getCombatLevelRequired()));
		}

		List<String> notes = new ArrayList<>(task.getNotes());
		notes.addAll(extraNotes);
		return new DiaryTaskGap(task.getOrdinal(), task.getText(), List.copyOf(inner), List.copyOf(notes));
	}

	/**
	 * Builds the goal status for one milestone, or empty when the milestone is already done.
	 * Completion is category-specific (ruling: see task-25 brief): {@code gear} - any
	 * {@code ownedIf} id held (bank ∪ inventory ∪ equipment, by id); {@code slayer_target} - the
	 * entry's Slayer skill requirement is met; {@code unlock}/{@code prayer}/{@code spellbook} -
	 * every requirement is met (no gaps); {@code boss} - never done.
	 */
	private Optional<GoalStatus> milestoneGoalStatus(MilestoneEntry entry, Snapshot snapshot, KnowledgeBase kb)
	{
		List<Gap> gaps = new ArrayList<>();
		boolean isIron = snapshot.getAccountType().isIron();

		for (SkillReq req : entry.getSkills())
		{
			if (req.isIronmanOnly() && !isIron)
			{
				continue;
			}
			addSkillGapIfShort(gaps, snapshot, req);
		}

		Map<String, QuestPrereqGap> prereqGaps = new LinkedHashMap<>();
		resolvePrereqs(entry.getQuests(), snapshot, kb, entry.getName(), true, prereqGaps, new ArrayList<>(), new LinkedHashSet<>());
		gaps.addAll(prereqGaps.values());

		for (DiaryRef diaryRef : entry.getDiaries())
		{
			if (!Boolean.TRUE.equals(snapshot.getDiaryTiers().get(diaryRef.getTier())))
			{
				gaps.add(new DiaryTierGap(diaryRef.getTier()));
			}
		}

		if (entry.getCombatLevel() != null && entry.getCombatLevel() > snapshot.combatLevel())
		{
			gaps.add(new CombatLevelGap(snapshot.combatLevel(), entry.getCombatLevel()));
		}
		if (entry.getQuestPoints() != null && entry.getQuestPoints() > snapshot.getQuestPoints())
		{
			gaps.add(new QuestPointsGap(snapshot.getQuestPoints(), entry.getQuestPoints()));
		}

		for (ItemReq req : entry.getItems())
		{
			addMilestoneItemGapIfShort(gaps, snapshot, req);
		}

		OwnedState ownedState = entry.getCategory() == MilestoneCategory.GEAR
			? gearOwnedState(entry, snapshot)
			: null;

		boolean done;
		switch (entry.getCategory())
		{
			case GEAR:
				done = ownedState == OwnedState.OWNED;
				break;
			case SLAYER_TARGET:
				done = slayerLevelMet(entry, snapshot);
				break;
			case BOSS:
				done = false;
				break;
			default: // UNLOCK, PRAYER, SPELLBOOK
				done = gaps.isEmpty();
				break;
		}
		if (done)
		{
			return Optional.empty();
		}

		int priority = kb.getPriorityOverrides().getOrDefault(entry.getId(), entry.getPriority());
		Goal goal = new Goal(entry.getId(), mapMilestoneCategory(entry.getCategory()), entry.getName(),
			WIKI_BASE + spacesToUnderscores(entry.getWikiTitle()), priority);
		boolean bankUnknown = anyBankUnknown(gaps) || ownedState == OwnedState.UNKNOWN;
		return Optional.of(new GoalStatus(goal, List.copyOf(gaps), gaps.isEmpty(), bankUnknown, List.of()));
	}

	private static void addMilestoneItemGapIfShort(List<Gap> gaps, Snapshot snapshot, ItemReq req)
	{
		Integer have = sumHaveById(snapshot, req.getId());
		if (have != null && have >= req.getQuantity())
		{
			return;
		}
		List<ItemSource> rawSources = req.getSources().stream()
			.map(s -> new ItemSource(s, "", ""))
			.collect(Collectors.toList());
		List<ItemSource> sources = sourcesFor(rawSources, snapshot.getAccountType());
		boolean mustObtain = mustObtain(rawSources, snapshot.getAccountType());
		gaps.add(new ItemGap(req.getName(), have, req.getQuantity(), sources, mustObtain));
	}

	private static Integer sumHaveById(Snapshot snapshot, int itemId)
	{
		if (!snapshot.isBankKnown())
		{
			return null;
		}
		return snapshot.getBank().getOrDefault(itemId, 0)
			+ snapshot.getInventory().getOrDefault(itemId, 0)
			+ snapshot.getEquipment().getOrDefault(itemId, 0);
	}

	private enum OwnedState
	{
		OWNED, NOT_OWNED, UNKNOWN
	}

	/**
	 * Whether any of the milestone's {@code ownedIf} item ids is held. Inventory and equipment are
	 * always known; the bank is only checked when {@link Snapshot#isBankKnown()}, so an unseen bank
	 * with nothing found elsewhere is {@link OwnedState#UNKNOWN} rather than {@link OwnedState#NOT_OWNED}.
	 */
	private static OwnedState gearOwnedState(MilestoneEntry entry, Snapshot snapshot)
	{
		for (OwnedItem owned : entry.getOwnedIf())
		{
			if (snapshot.getInventory().getOrDefault(owned.getId(), 0) > 0
				|| snapshot.getEquipment().getOrDefault(owned.getId(), 0) > 0)
			{
				return OwnedState.OWNED;
			}
			if (snapshot.isBankKnown() && snapshot.getBank().getOrDefault(owned.getId(), 0) > 0)
			{
				return OwnedState.OWNED;
			}
		}
		return snapshot.isBankKnown() ? OwnedState.NOT_OWNED : OwnedState.UNKNOWN;
	}

	private static boolean slayerLevelMet(MilestoneEntry entry, Snapshot snapshot)
	{
		return entry.getSkills().stream()
			.filter(s -> s.getSkill() == Skill.SLAYER)
			.findFirst()
			.map(s -> skillLevel(snapshot, Skill.SLAYER) >= s.getLevel())
			.orElse(false);
	}

	private static int skillLevel(Snapshot snapshot, Skill skill)
	{
		SkillState state = snapshot.getSkills().get(skill);
		return state == null ? 1 : state.getLevel();
	}

	private static GoalCategory mapMilestoneCategory(MilestoneCategory category)
	{
		switch (category)
		{
			case SLAYER_TARGET:
				return GoalCategory.SLAYER_TARGET;
			case BOSS:
				return GoalCategory.BOSS;
			default: // GEAR, UNLOCK, PRAYER, SPELLBOOK
				return GoalCategory.MILESTONE;
		}
	}

	private void addSkillGapIfShort(List<Gap> gaps, Snapshot snapshot, SkillReq req)
	{
		SkillState state = snapshot.getSkills().get(req.getSkill());
		int have = state == null ? 1 : state.getLevel();
		if (have >= req.getLevel())
		{
			return;
		}

		long currentXp = state == null ? 0 : state.getXp();
		long xpDelta = Experience.getXpForLevel(req.getLevel()) - currentXp;

		Integer boostableFrom = null;
		if (req.isBoostable())
		{
			int floor = req.getLevel() - boostTable.maxBoost(req.getSkill());
			if (have >= floor)
			{
				boostableFrom = floor;
			}
		}
		gaps.add(new SkillLevelGap(req.getSkill(), have, req.getLevel(), xpDelta, req.isBoostable(), boostableFrom));
	}

	private static void addItemGapIfShort(List<Gap> gaps, Snapshot snapshot, String name, int need)
	{
		Integer have = sumHave(snapshot, name);
		if (have == null || have < need)
		{
			gaps.add(new ItemGap(name, have, need, List.of(), false));
		}
	}

	/**
	 * Depth-first, cycle-safe resolution of prerequisite quest names into {@link QuestPrereqGap}s,
	 * deduped by name via {@code gapsByName} (each quest at most once). A name already on
	 * {@code path} (an ancestor in the current recursion) is not re-descended into, so a
	 * prerequisite cycle can't loop forever. When {@code throwOnUnknown} is false, a name absent
	 * from the knowledge base becomes a note in {@code extraNotes} instead of a failure (diary task
	 * quest text is looser than quest prerequisite data). Returns true if any of {@code names}
	 * resolved to an unfinished quest, so the caller knows whether it has an unfinished prerequisite
	 * of its own (and so is not itself {@code startHere}).
	 */
	private static boolean resolvePrereqs(List<String> names, Snapshot snapshot, KnowledgeBase kb, String forName, boolean throwOnUnknown,
		Map<String, QuestPrereqGap> gapsByName, List<String> extraNotes, Set<String> path)
	{
		boolean anyUnfinished = false;
		for (String name : names)
		{
			QuestEntry entry = kb.questByName(name);
			Quest quest = entry == null ? null : QUESTS_BY_NAME.get(name);
			if (entry == null || quest == null)
			{
				if (throwOnUnknown)
				{
					throw new IllegalStateException("Unknown prerequisite quest \"" + name + "\" for quest \"" + forName + "\"");
				}
				extraNotes.add("Quest requirement (see wiki): " + name);
				continue;
			}

			QuestState state = snapshot.getQuests().getOrDefault(quest, QuestState.NOT_STARTED);
			if (state == QuestState.FINISHED)
			{
				continue;
			}
			anyUnfinished = true;

			if (gapsByName.containsKey(name) || !path.add(name))
			{
				continue;
			}
			boolean hasUnfinishedPrereq = resolvePrereqs(entry.getPrereqs(), snapshot, kb, name, throwOnUnknown, gapsByName, extraNotes, path);
			resolveStartedPrereqs(entry.getPrereqsStarted(), snapshot, name, gapsByName);
			path.remove(name);
			gapsByName.put(name, new QuestPrereqGap(quest, state, !hasUnfinishedPrereq, false));
		}
		return anyUnfinished;
	}

	/**
	 * Resolves "must have started" prerequisite quest names (the wiki's {@code Started:} prefix)
	 * into {@link QuestPrereqGap}s, satisfied by any state other than {@code NOT_STARTED}. Never
	 * recurses into the started quest's own prerequisites - starting it is the whole requirement -
	 * so every gap produced here is unconditionally {@code startHere}. Every name has already been
	 * validated at {@link KnowledgeBase} load time to be a known quest name.
	 */
	private static void resolveStartedPrereqs(List<String> names, Snapshot snapshot, String forName, Map<String, QuestPrereqGap> gapsByName)
	{
		for (String name : names)
		{
			Quest quest = QUESTS_BY_NAME.get(name);
			if (quest == null)
			{
				throw new IllegalStateException("Unknown started prerequisite quest \"" + name + "\" for quest \"" + forName + "\"");
			}
			if (gapsByName.containsKey(name))
			{
				continue;
			}
			QuestState state = snapshot.getQuests().getOrDefault(quest, QuestState.NOT_STARTED);
			if (state != QuestState.NOT_STARTED)
			{
				continue;
			}
			gapsByName.put(name, new QuestPrereqGap(quest, state, true, true));
		}
	}

	private static Integer sumHave(Snapshot snapshot, String itemName)
	{
		if (!snapshot.isBankKnown())
		{
			return null;
		}
		return sumByName(snapshot.getBank(), snapshot.getItemNames(), itemName)
			+ sumByName(snapshot.getInventory(), snapshot.getItemNames(), itemName)
			+ sumByName(snapshot.getEquipment(), snapshot.getItemNames(), itemName);
	}

	private static int sumByName(Map<Integer, Integer> container, Map<Integer, String> itemNames, String name)
	{
		int sum = 0;
		for (Map.Entry<Integer, Integer> e : container.entrySet())
		{
			String itemName = itemNames.get(e.getKey());
			if (itemName != null && itemName.equalsIgnoreCase(name))
			{
				sum += e.getValue();
			}
		}
		return sum;
	}

	private static GoalStatus toGoalStatus(Goal goal, List<Gap> gaps)
	{
		return toGoalStatus(goal, gaps, List.of());
	}

	private static GoalStatus toGoalStatus(Goal goal, List<Gap> gaps, List<String> notes)
	{
		return new GoalStatus(goal, List.copyOf(gaps), gaps.isEmpty(), anyBankUnknown(gaps), List.copyOf(notes));
	}

	private static boolean anyBankUnknown(List<Gap> gaps)
	{
		for (Gap gap : gaps)
		{
			if (gap instanceof ItemGap && ((ItemGap) gap).getHave() == null)
			{
				return true;
			}
			if (gap instanceof DiaryTaskGap && anyBankUnknown(((DiaryTaskGap) gap).getGaps()))
			{
				return true;
			}
		}
		return false;
	}

	private static String diaryArea(DiaryTier tier)
	{
		return titleCase(tier.name().substring(0, tier.name().lastIndexOf('_')));
	}

	private static String diaryTierName(DiaryTier tier)
	{
		return titleCase(tier.name().substring(tier.name().lastIndexOf('_') + 1));
	}

	private static String titleCase(String s)
	{
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
	}

	private static String spacesToUnderscores(String s)
	{
		return s.replace(' ', '_');
	}

	private static Map<Integer, Quest> buildQuestsById()
	{
		Map<Integer, Quest> m = new LinkedHashMap<>();
		for (Quest quest : Quest.values())
		{
			m.put(quest.getId(), quest);
		}
		return Map.copyOf(m);
	}

	private static Map<String, Quest> buildQuestsByName()
	{
		Map<String, Quest> m = new LinkedHashMap<>();
		for (Quest quest : Quest.values())
		{
			m.put(quest.getName(), quest);
		}
		return Map.copyOf(m);
	}
}
