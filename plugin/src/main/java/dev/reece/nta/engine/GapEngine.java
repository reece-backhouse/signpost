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
import dev.reece.nta.engine.model.Met;
import dev.reece.nta.engine.model.QuestPointsGap;
import dev.reece.nta.engine.model.QuestPrereqGap;
import dev.reece.nta.engine.model.SkillLevelGap;
import dev.reece.nta.kb.DiaryEntry;
import dev.reece.nta.kb.DiaryRef;
import dev.reece.nta.kb.DiaryTask;
import dev.reece.nta.kb.ItemReq;
import dev.reece.nta.kb.KnowledgeBase;
import dev.reece.nta.kb.MaterialEntry;
import dev.reece.nta.kb.MilestoneCategory;
import dev.reece.nta.kb.MilestoneEntry;
import dev.reece.nta.kb.OwnedItem;
import dev.reece.nta.kb.QuestEntry;
import dev.reece.nta.kb.RecommendedProfile;
import dev.reece.nta.kb.RecommendedSkill;
import dev.reece.nta.kb.SkillReq;
import dev.reece.nta.kb.WikiUrls;
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
 * incomplete diary tier, works out exactly what's missing - and (spec ruling 29) what's already
 * met, as {@link Met} entries in the same order the requirements are checked. No
 * {@link net.runelite.api.Client}, no I/O (global constraint: engine code is pure).
 */
public final class GapEngine
{
	private static final int QUEST_PRIORITY = 5;
	private static final int DIARY_PRIORITY = 4;

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
		List<Met> met = new ArrayList<>();
		boolean isIron = snapshot.getAccountType().isIron();

		for (SkillReq req : entry.getSkills())
		{
			if (req.isIronmanOnly() && !isIron)
			{
				continue;
			}
			addSkillGapIfShort(gaps, met, snapshot, req);
		}

		Map<String, QuestPrereqGap> prereqGaps = new LinkedHashMap<>();
		// Seed the recursion path with the quest's own name so a prerequisite cycle that loops back
		// to this quest is treated as "already visiting" rather than adding the quest as its own gap.
		Set<String> path = new LinkedHashSet<>();
		path.add(entry.getName());
		resolvePrereqs(entry.getPrereqs(), snapshot, kb, entry.getName(), true, prereqGaps, new ArrayList<>(), path);
		resolveStartedPrereqs(entry.getPrereqsStarted(), snapshot, kb, entry.getName(), prereqGaps);
		gaps.addAll(prereqGaps.values());
		addMetPrereqs(met, entry.getPrereqs(), snapshot, false);
		addMetPrereqs(met, entry.getPrereqsStarted(), snapshot, true);

		List<String> notes = new ArrayList<>(entry.getPrereqNotes());
		for (ItemReq req : entry.getItems())
		{
			addItemGapIfShort(gaps, met, notes, snapshot, kb, req.getName(), req.getQuantity());
		}

		if (entry.getQuestPointsRequired() != null)
		{
			addQuestPointsGapIfShort(gaps, met, snapshot, entry.getQuestPointsRequired());
		}
		if (entry.getKudosRequired() != null)
		{
			if (entry.getKudosRequired() > snapshot.getKudos())
			{
				gaps.add(new KudosGap(snapshot.getKudos(), entry.getKudosRequired()));
			}
			else
			{
				met.add(new Met(Met.Kind.KUDOS, "Kudos " + entry.getKudosRequired() + " (have " + snapshot.getKudos() + ")"));
			}
		}
		if (entry.getCombatLevelRequired() != null)
		{
			addCombatGapIfShort(gaps, met, snapshot, entry.getCombatLevelRequired(), false);
		}

		String goalId = "quest:" + entry.getId();
		int priority = kb.getPriorityOverrides().getOrDefault(goalId, QUEST_PRIORITY);
		Goal goal = new Goal(goalId, GoalCategory.QUEST, entry.getName(), WikiUrls.forTitle(entry.getWikiTitle()),
			priority, questStage(priority));
		return toGoalStatus(goal, gaps, notes, met);
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

		int done = 0;
		if (gameCount == null || gameCount != total)
		{
			for (DiaryTask task : entry.getTasks())
			{
				if (task.getCompletion().isComplete(snapshot))
				{
					done++;
					continue;
				}
				gaps.add(diaryTaskGap(task, snapshot, kb));
			}
			if (gameCount != null && gameCount > progress.getCompleted())
			{
				notes.add("game reports " + gameCount + "/" + total + " done; one of these tasks is already complete");
			}
		}
		// The game's own count is trusted over the bit map whenever it's known (see above).
		int tasksDone = gameCount != null ? gameCount : done;
		List<Met> met = tasksDone == 0
			? List.of()
			: List.of(new Met(Met.Kind.DIARY, tasksDone + " of " + total + " tasks done", tasksDone, total));

		String area = diaryArea(tier);
		String tierName = diaryTierName(tier);
		String goalId = "diary:" + tier.name();
		int priority = kb.getPriorityOverrides().getOrDefault(goalId, DIARY_PRIORITY);
		Goal goal = new Goal(goalId, GoalCategory.DIARY, area + " " + tierName + " Diary",
			WikiUrls.forTitle(area + " Diary"), priority, DIARY_STAGE.get(tier));
		return toGoalStatus(goal, gaps, notes, met);
	}

	private DiaryTaskGap diaryTaskGap(DiaryTask task, Snapshot snapshot, KnowledgeBase kb)
	{
		List<Gap> inner = new ArrayList<>();
		List<Met> innerMet = new ArrayList<>(); // A task is a gap node (ruling 16): its own met requirements are not surfaced.
		boolean isIron = snapshot.getAccountType().isIron();

		for (SkillReq req : task.getSkills())
		{
			if (req.isIronmanOnly() && !isIron)
			{
				continue;
			}
			addSkillGapIfShort(inner, innerMet, snapshot, req);
		}

		Map<String, QuestPrereqGap> questGaps = new LinkedHashMap<>();
		List<String> extraNotes = new ArrayList<>();
		resolvePrereqs(task.getQuests(), snapshot, kb, "diary task " + task.getOrdinal(), false, questGaps, extraNotes, new LinkedHashSet<>());
		inner.addAll(questGaps.values());

		for (String itemName : task.getItems())
		{
			addItemGapIfShort(inner, innerMet, extraNotes, snapshot, kb, itemName, 1);
		}

		if (task.getCombatLevelRequired() != null)
		{
			addCombatGapIfShort(inner, innerMet, snapshot, task.getCombatLevelRequired(), false);
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
		List<Met> met = new ArrayList<>();
		boolean isIron = snapshot.getAccountType().isIron();

		for (SkillReq req : entry.getSkills())
		{
			if (req.isIronmanOnly() && !isIron)
			{
				continue;
			}
			addSkillGapIfShort(gaps, met, snapshot, req);
		}

		Map<String, QuestPrereqGap> prereqGaps = new LinkedHashMap<>();
		resolvePrereqs(entry.getQuests(), snapshot, kb, entry.getName(), true, prereqGaps, new ArrayList<>(), new LinkedHashSet<>());
		gaps.addAll(prereqGaps.values());
		addMetPrereqs(met, entry.getQuests(), snapshot, false);

		for (DiaryRef diaryRef : entry.getDiaries())
		{
			if (!Boolean.TRUE.equals(snapshot.getDiaryTiers().get(diaryRef.getTier())))
			{
				gaps.add(new DiaryTierGap(diaryRef.getTier()));
			}
			else
			{
				met.add(new Met(Met.Kind.DIARY, diaryArea(diaryRef.getTier()) + " " + diaryTierName(diaryRef.getTier()) + " Diary"));
			}
		}

		if (entry.getCombatLevel() != null)
		{
			addCombatGapIfShort(gaps, met, snapshot, entry.getCombatLevel(), false);
		}
		if (entry.getQuestPoints() != null)
		{
			addQuestPointsGapIfShort(gaps, met, snapshot, entry.getQuestPoints());
		}

		for (ItemReq req : entry.getItems())
		{
			addMilestoneItemGapIfShort(gaps, met, snapshot, kb, req);
		}

		if (entry.getRecommended() != null)
		{
			addRecommendedGaps(gaps, met, entry.getRecommended(), snapshot);
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
			WikiUrls.forTitle(entry.getWikiTitle()), priority, entry.getStage());
		boolean bankUnknown = anyBankUnknown(gaps) || ownedState == OwnedState.UNKNOWN;
		return Optional.of(new GoalStatus(goal, List.copyOf(gaps), gaps.isEmpty(), bankUnknown, List.of(), List.copyOf(met), List.of(), null, 0));
	}

	/**
	 * Adds gaps for a milestone's {@code recommended} profile (spec ruling 27) - a "actually ready"
	 * layer on top of hard requirements, e.g. a boss's recommended combat stats and gear. A boss (or
	 * any milestone) is only {@code ready} once these are met too, since they land in the same
	 * {@code gaps} list as the hard requirements.
	 */
	private static void addRecommendedGaps(List<Gap> gaps, List<Met> met, RecommendedProfile recommended, Snapshot snapshot)
	{
		for (RecommendedSkill req : recommended.getSkills())
		{
			SkillState state = snapshot.getSkills().get(req.getSkill());
			int have = state == null ? 1 : state.getLevel();
			if (have >= req.getLevel())
			{
				met.add(new Met(Met.Kind.RECOMMENDED_SKILL, skillLabel(req.getSkill(), req.getLevel(), have)));
				continue;
			}
			long currentXp = state == null ? 0 : state.getXp();
			long xpDelta = Experience.getXpForLevel(req.getLevel()) - currentXp;
			gaps.add(new SkillLevelGap(req.getSkill(), have, req.getLevel(), xpDelta, false, null, true));
		}

		if (recommended.getCombatLevel() != null)
		{
			addCombatGapIfShort(gaps, met, snapshot, recommended.getCombatLevel(), true);
		}

		List<OwnedItem> gearOwnedAny = recommended.getGearOwnedAny();
		if (!gearOwnedAny.isEmpty())
		{
			int required = recommended.effectiveGearOwnedMin();
			List<String> ownedNames = new ArrayList<>();
			int maybeOwned = 0;
			for (OwnedItem item : gearOwnedAny)
			{
				if (anyIdHeld(item.getIds(), snapshot))
				{
					ownedNames.add(item.getName());
				}
				else if (!snapshot.isBankKnown())
				{
					maybeOwned++;
				}
			}
			int owned = ownedNames.size();
			// Owned names are recorded even below the minimum, so the explanation can say
			// "Meets 2 of 4 recommended gear: ..." for a boss that is not yet ready (spec ruling 29).
			for (String name : ownedNames)
			{
				met.add(new Met(Met.Kind.RECOMMENDED_GEAR, name, owned, required));
			}
			if (owned < required)
			{
				boolean bankUnknown = owned + maybeOwned >= required;
				gaps.add(new GearGap(gearOwnedAny, owned, required, bankUnknown));
			}
		}
	}

	private static void addCombatGapIfShort(List<Gap> gaps, List<Met> met, Snapshot snapshot, int need, boolean recommended)
	{
		int have = snapshot.combatLevel();
		if (need > have)
		{
			gaps.add(new CombatLevelGap(have, need, recommended));
		}
		else
		{
			met.add(new Met(Met.Kind.COMBAT, "Combat " + need + " (have " + have + ")"));
		}
	}

	private static void addQuestPointsGapIfShort(List<Gap> gaps, List<Met> met, Snapshot snapshot, int need)
	{
		int have = snapshot.getQuestPoints();
		if (need > have)
		{
			gaps.add(new QuestPointsGap(have, need));
		}
		else
		{
			met.add(new Met(Met.Kind.QUEST_POINTS, "Quest points " + need + " (have " + have + ")"));
		}
	}

	/**
	 * Records a {@link Met.Kind#QUEST} for each of {@code names} that is finished (or, when
	 * {@code startedOnly}, merely started) - direct prerequisites only, never the transitive
	 * closure {@link #resolvePrereqs} walks for gaps. A name absent from RuneLite's quest list is
	 * skipped here; the gap side has already thrown or noted it.
	 */
	private static void addMetPrereqs(List<Met> met, List<String> names, Snapshot snapshot, boolean startedOnly)
	{
		for (String name : names)
		{
			Quest quest = QUESTS_BY_NAME.get(name);
			if (quest == null)
			{
				continue;
			}
			QuestState state = snapshot.getQuests().getOrDefault(quest, QuestState.NOT_STARTED);
			if (state == QuestState.FINISHED || (startedOnly && state != QuestState.NOT_STARTED))
			{
				met.add(new Met(Met.Kind.QUEST, name));
			}
		}
	}

	private static String skillLabel(Skill skill, int need, int have)
	{
		return skill.getName() + " " + need + " (have " + have + ")";
	}

	private static void addMilestoneItemGapIfShort(List<Gap> gaps, List<Met> met, Snapshot snapshot, KnowledgeBase kb, ItemReq req)
	{
		Integer have = sumHaveByIds(snapshot, req.getIds());
		if (have != null && have >= req.getQuantity())
		{
			met.add(new Met(Met.Kind.ITEM, itemLabel(req.getName(), req.getQuantity())));
			return;
		}
		List<ItemSource> rawSources = req.getSources().stream()
			.map(s -> new ItemSource(s, "", ""))
			.collect(Collectors.toList());
		List<ItemSource> sources = sourcesFor(rawSources, snapshot.getAccountType());
		boolean mustObtain = mustObtain(rawSources, snapshot.getAccountType());
		gaps.add(new ItemGap(req.getName(), have, req.getQuantity(), sources, mustObtain, wikiUrlFor(kb, req.getName())));
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

	private void addSkillGapIfShort(List<Gap> gaps, List<Met> met, Snapshot snapshot, SkillReq req)
	{
		SkillState state = snapshot.getSkills().get(req.getSkill());
		int have = state == null ? 1 : state.getLevel();
		if (have >= req.getLevel())
		{
			met.add(new Met(Met.Kind.SKILL, skillLabel(req.getSkill(), req.getLevel(), have)));
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

	/**
	 * A quest/diary item requirement by name. A name the knowledge base marks {@code generic}
	 * ("Pickaxe", "Combat gear", "Light source": a category or prose reference, not an in-game item
	 * name) can never be matched against the bank, so it becomes a note rather than an
	 * {@link ItemGap} - it must not keep a goal from "Ready now" or become a "Get pickaxe" next
	 * step (final-review I5). A name with no material entry at all is matched as before.
	 */
	private static void addItemGapIfShort(List<Gap> gaps, List<Met> met, List<String> notes, Snapshot snapshot, KnowledgeBase kb, String name, int need)
	{
		MaterialEntry material = kb.materialByName(name);
		if (material != null && material.isGeneric())
		{
			notes.add("Bring: " + itemLabel(name, need) + " (see wiki)");
			return;
		}
		Integer have = sumHave(snapshot, name);
		if (have == null || have < need)
		{
			gaps.add(new ItemGap(name, have, need, List.of(), false, material == null ? null : material.getWikiUrl()));
		}
		else
		{
			met.add(new Met(Met.Kind.ITEM, itemLabel(name, need)));
		}
	}

	private static String itemLabel(String name, int need)
	{
		return name + (need > 1 ? " ×" + need : "");
	}

	private static String wikiUrlFor(KnowledgeBase kb, String itemName)
	{
		MaterialEntry material = kb.materialByName(itemName);
		return material == null ? WikiUrls.forTitle(itemName) : material.getWikiUrl();
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
		return WikiUrls.forTitle(entry.getWikiTitle());
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

	private static GoalStatus toGoalStatus(Goal goal, List<Gap> gaps, List<String> notes, List<Met> met)
	{
		return new GoalStatus(goal, List.copyOf(gaps), gaps.isEmpty(), anyBankUnknown(gaps), List.copyOf(notes), List.copyOf(met), List.of(), null, 0);
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
