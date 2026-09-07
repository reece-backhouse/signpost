package dev.reece.nta.engine;

import dev.reece.nta.engine.model.CombatLevelGap;
import dev.reece.nta.engine.model.DiaryTaskGap;
import dev.reece.nta.engine.model.DiaryTierGap;
import dev.reece.nta.engine.model.Gap;
import dev.reece.nta.engine.model.GearGap;
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
import dev.reece.nta.kb.RecommendedProfile;
import dev.reece.nta.kb.RecommendedSkill;
import dev.reece.nta.kb.SkillReq;
import dev.reece.nta.snapshot.AccountType;
import dev.reece.nta.snapshot.DiaryTier;
import dev.reece.nta.snapshot.SkillState;
import dev.reece.nta.snapshot.Snapshot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
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

	/** Quest goal stage from its effective (post-override) priority: &ge;8 &rarr; 3, 5..7 &rarr; 2, else 1 (spec ruling 27). */
	private static int questStage(int priority)
	{
		if (priority >= 8)
		{
			return 3;
		}
		return priority >= 5 ? 2 : 1;
	}

	/** Diary goal stage by tier (spec ruling 27): Easy 1, Medium 2, Hard 3, Elite 4. */
	private static final Map<DiaryTier, Integer> DIARY_STAGE = buildDiaryStages();

	private static Map<DiaryTier, Integer> buildDiaryStages()
	{
		Map<DiaryTier, Integer> m = new EnumMap<>(DiaryTier.class);
		for (DiaryTier tier : DiaryTier.values())
		{
			String tierName = tier.name();
			if (tierName.endsWith("_EASY"))
			{
				m.put(tier, 1);
			}
			else if (tierName.endsWith("_MEDIUM"))
			{
				m.put(tier, 2);
			}
			else if (tierName.endsWith("_HARD"))
			{
				m.put(tier, 3);
			}
			else // _ELITE
			{
				m.put(tier, 4);
			}
		}
		return Map.copyOf(m);
	}

	private static final Map<Integer, Quest> QUESTS_BY_ID = buildQuestsById();
	private static final Map<String, Quest> QUESTS_BY_NAME = buildQuestsByName();

	private final BoostTable boostTable;

	public GapEngine(BoostTable boostTable)
	{
		this.boostTable = boostTable;
	}

	/** As {@link #evaluate(Snapshot, KnowledgeBase, Map)}, computing {@link DiaryProgress} internally - for callers with no other need for it. */
	public List<GoalStatus> evaluate(Snapshot snapshot, KnowledgeBase kb)
	{
		return evaluate(snapshot, kb, DiaryProgress.compute(snapshot, kb));
	}

	/**
	 * As {@link #evaluate(Snapshot, KnowledgeBase)}, but takes an already-computed
	 * {@link DiaryProgress#compute} result (spec ruling: "diary game-count trust") instead of
	 * computing it again - {@link Engine#run} shares one computation between this and
	 * {@link dev.reece.nta.engine.model.Advice#getDiaryProgress()}.
	 */
	public List<GoalStatus> evaluate(Snapshot snapshot, KnowledgeBase kb, Map<DiaryTier, DiaryTierProgress> diaryProgress)
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
			result.add(diaryGoalStatus(tier, entry, snapshot, kb, diaryProgress.get(tier)));
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
		resolveStartedPrereqs(entry.getPrereqsStarted(), snapshot, kb, entry.getName(), prereqGaps);
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
			gaps.add(new CombatLevelGap(snapshot.combatLevel(), entry.getCombatLevelRequired(), false));
		}

		String goalId = "quest:" + entry.getId();
		int priority = kb.getPriorityOverrides().getOrDefault(goalId, QUEST_PRIORITY);
		Goal goal = new Goal(goalId, GoalCategory.QUEST, entry.getName(), WIKI_BASE + spacesToUnderscores(entry.getWikiTitle()),
			priority, questStage(priority));
		return toGoalStatus(goal, gaps, entry.getPrereqNotes());
	}

	/**
	 * Ruling ("diary game-count trust"): the game's own per-tier completed-task counter varbit
	 * ({@code progress.getGameCount()}) is trusted over the bundled task-&gt;bit map when they
	 * disagree - the live self-check found a kb/game mismatch caused by an unmapped variable, not a
	 * genuinely incomplete task. If the game reports every task done, no {@link DiaryTaskGap}s are
	 * emitted at all (the goal is "ready" - claim the reward - unless the tier varbit is already set,
	 * in which case {@link #evaluate} never calls this method for it). If the game reports more done
	 * than the kb's bit-derived count (but not all), the kb's task gaps are kept as-is (never
	 * shrunk - the specific completed task isn't known) with a note that one of them is already done.
	 */
	private GoalStatus diaryGoalStatus(DiaryTier tier, DiaryEntry entry, Snapshot snapshot, KnowledgeBase kb, DiaryTierProgress progress)
	{
		List<Gap> gaps = new ArrayList<>();
		List<String> notes = new ArrayList<>();
		Integer gameCount = progress == null ? null : progress.getGameCount();
		int total = entry.getTasks().size();

		if (gameCount == null || gameCount != total)
		{
			for (DiaryTask task : entry.getTasks())
			{
				if (task.getCompletion().isComplete(snapshot))
				{
					continue;
				}
				gaps.add(diaryTaskGap(task, snapshot, kb));
			}
			if (gameCount != null && gameCount > progress.getCompleted())
			{
				notes.add("game reports " + gameCount + "/" + total + " done; one of these tasks is already complete");
			}
		}

		String area = diaryArea(tier);
		String tierName = diaryTierName(tier);
		String goalId = "diary:" + tier.name();
		int priority = kb.getPriorityOverrides().getOrDefault(goalId, DIARY_PRIORITY);
		Goal goal = new Goal(goalId, GoalCategory.DIARY, area + " " + tierName + " Diary",
			WIKI_BASE + spacesToUnderscores(area) + "_Diary", priority, DIARY_STAGE.get(tier));
		return toGoalStatus(goal, gaps, notes);
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
			inner.add(new CombatLevelGap(snapshot.combatLevel(), task.getCombatLevelRequired(), false));
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
			gaps.add(new CombatLevelGap(snapshot.combatLevel(), entry.getCombatLevel(), false));
		}
		if (entry.getQuestPoints() != null && entry.getQuestPoints() > snapshot.getQuestPoints())
		{
			gaps.add(new QuestPointsGap(snapshot.getQuestPoints(), entry.getQuestPoints()));
		}

		for (ItemReq req : entry.getItems())
		{
			addMilestoneItemGapIfShort(gaps, snapshot, req);
		}

		if (entry.getRecommended() != null)
		{
			addRecommendedGaps(gaps, entry.getRecommended(), snapshot);
		}

		OwnedState ownedState = entry.getCategory() == MilestoneCategory.GEAR
			? ownedState(entry.getOwnedIf(), snapshot)
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
			WIKI_BASE + spacesToUnderscores(entry.getWikiTitle()), priority, entry.getStage());
		boolean bankUnknown = anyBankUnknown(gaps) || ownedState == OwnedState.UNKNOWN;
		return Optional.of(new GoalStatus(goal, List.copyOf(gaps), gaps.isEmpty(), bankUnknown, List.of()));
	}

	/**
	 * Adds gaps for a milestone's {@code recommended} profile (spec ruling 27) - a "actually ready"
	 * layer on top of hard requirements, e.g. a boss's recommended combat stats and gear. A boss (or
	 * any milestone) is only {@code ready} once these are met too, since they land in the same
	 * {@code gaps} list as the hard requirements.
	 */
	private static void addRecommendedGaps(List<Gap> gaps, RecommendedProfile recommended, Snapshot snapshot)
	{
		for (RecommendedSkill req : recommended.getSkills())
		{
			SkillState state = snapshot.getSkills().get(req.getSkill());
			int have = state == null ? 1 : state.getLevel();
			if (have >= req.getLevel())
			{
				continue;
			}
			long currentXp = state == null ? 0 : state.getXp();
			long xpDelta = Experience.getXpForLevel(req.getLevel()) - currentXp;
			gaps.add(new SkillLevelGap(req.getSkill(), have, req.getLevel(), xpDelta, false, null, true));
		}

		if (recommended.getCombatLevel() != null && recommended.getCombatLevel() > snapshot.combatLevel())
		{
			gaps.add(new CombatLevelGap(snapshot.combatLevel(), recommended.getCombatLevel(), true));
		}

		List<OwnedItem> gearOwnedAny = recommended.getGearOwnedAny();
		if (!gearOwnedAny.isEmpty())
		{
			int required = recommended.effectiveGearOwnedMin();
			int owned = 0;
			int maybeOwned = 0;
			for (OwnedItem item : gearOwnedAny)
			{
				if (anyIdHeld(item.getIds(), snapshot))
				{
					owned++;
				}
				else if (!snapshot.isBankKnown())
				{
					maybeOwned++;
				}
			}
			if (owned < required)
			{
				boolean bankUnknown = owned + maybeOwned >= required;
				gaps.add(new GearGap(gearOwnedAny, owned, required, bankUnknown));
			}
		}
	}

	private static void addMilestoneItemGapIfShort(List<Gap> gaps, Snapshot snapshot, ItemReq req)
	{
		Integer have = sumHaveByIds(snapshot, req.getIds());
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

	/** Sums bank + inventory + equipment across every id in {@code itemIds} (a requirement item's wiki-variant ids). */
	private static Integer sumHaveByIds(Snapshot snapshot, List<Integer> itemIds)
	{
		if (!snapshot.isBankKnown())
		{
			return null;
		}
		int sum = 0;
		for (int itemId : itemIds)
		{
			sum += snapshot.getBank().getOrDefault(itemId, 0)
				+ snapshot.getInventory().getOrDefault(itemId, 0)
				+ snapshot.getEquipment().getOrDefault(itemId, 0);
		}
		return sum;
	}

	private enum OwnedState
	{
		OWNED, NOT_OWNED, UNKNOWN
	}

	/**
	 * Whether any of {@code items}' ids (each item's full variant {@code ids} list, not just its
	 * primary {@code id}) is held (bank &cup; inventory &cup; equipment), shared by a milestone's
	 * {@code ownedIf} (gear category completion) and a {@code recommended} profile's
	 * {@code gearOwnedAny} (recommended-gear gap). Inventory and equipment are always known; the bank
	 * is only checked when {@link Snapshot#isBankKnown()}, so an unseen bank with nothing found
	 * elsewhere is {@link OwnedState#UNKNOWN} rather than {@link OwnedState#NOT_OWNED}.
	 */
	private static OwnedState ownedState(List<OwnedItem> items, Snapshot snapshot)
	{
		for (OwnedItem owned : items)
		{
			if (anyIdHeld(owned.getIds(), snapshot))
			{
				return OwnedState.OWNED;
			}
		}
		return snapshot.isBankKnown() ? OwnedState.NOT_OWNED : OwnedState.UNKNOWN;
	}

	/**
	 * Whether any of {@code itemIds} is held (bank &cup; inventory &cup; equipment) in any quantity.
	 * Shared with {@link dev.reece.nta.engine.StageEstimator}'s gear-milestone-owned check, since
	 * both need the exact same "any variant id, any container" rule.
	 */
	static boolean anyIdHeld(List<Integer> itemIds, Snapshot snapshot)
	{
		for (int itemId : itemIds)
		{
			if (snapshot.getInventory().getOrDefault(itemId, 0) > 0
				|| snapshot.getEquipment().getOrDefault(itemId, 0) > 0
				|| (snapshot.isBankKnown() && snapshot.getBank().getOrDefault(itemId, 0) > 0))
			{
				return true;
			}
		}
		return false;
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
		gaps.add(new SkillLevelGap(req.getSkill(), have, req.getLevel(), xpDelta, req.isBoostable(), boostableFrom, false));
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
			resolveStartedPrereqs(entry.getPrereqsStarted(), snapshot, kb, name, gapsByName);
			path.remove(name);
			gapsByName.put(name, new QuestPrereqGap(quest, state, !hasUnfinishedPrereq, false, wikiUrlFor(entry)));
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
	private static void resolveStartedPrereqs(List<String> names, Snapshot snapshot, KnowledgeBase kb, String forName,
		Map<String, QuestPrereqGap> gapsByName)
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
			gapsByName.put(name, new QuestPrereqGap(quest, state, true, true, wikiUrlFor(kb.questByName(name))));
		}
	}

	/** Same construction {@link Goal#getWikiUrl()} uses (spaces to underscores, no other encoding) - never {@code Quest.getName()}, which 404s for a subquest whose wiki title differs (e.g. a Recipe for Disaster subquest). */
	private static String wikiUrlFor(QuestEntry entry)
	{
		return WIKI_BASE + spacesToUnderscores(entry.getWikiTitle());
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
			if (gap instanceof GearGap && ((GearGap) gap).isBankUnknown())
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
