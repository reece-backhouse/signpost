package com.signpost.engine;

import com.signpost.engine.model.ItemSource;
import com.signpost.engine.model.PlanOffer;
import com.signpost.engine.model.Route;
import com.signpost.engine.model.Shortfall;
import com.signpost.engine.model.ShortfallItem;
import com.signpost.kb.GatheringAlternative;
import com.signpost.kb.GatheringPlan;
import com.signpost.kb.GatheringRequires;
import com.signpost.kb.GatheringStep;
import com.signpost.kb.ItemQuantity;
import com.signpost.kb.KnowledgeBase;
import com.signpost.kb.MaterialEntry;
import com.signpost.kb.MethodEntry;
import com.signpost.kb.SkillReq;
import com.signpost.kb.GatheringRisk;
import com.signpost.engine.model.BringItemStatus;
import com.signpost.snapshot.AccountType;
import com.signpost.snapshot.SkillState;
import com.signpost.snapshot.Snapshot;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;

/**
 * Pure function {@code (skill, route, kb, snapshot) -> Shortfall}: explains a {@link Route}'s
 * {@code uncoveredXp}. No {@link net.runelite.api.Client}, no I/O.
 */
public final class ShortfallResolver
{
	private static final SkillState UNKNOWN_SKILL = new SkillState(0, 0);

	private final boolean includeWilderness;

	private ShortfallResolver(boolean includeWilderness)
	{
		this.includeWilderness = includeWilderness;
	}

	public static Shortfall resolve(Skill skill, Route route, KnowledgeBase kb, Snapshot snapshot)
	{
		return resolve(skill, route, kb, snapshot, false);
	}

	public static Shortfall resolve(Skill skill, Route route, KnowledgeBase kb, Snapshot snapshot, boolean includeWilderness)
	{
		return new ShortfallResolver(includeWilderness).resolveInternal(skill, route, kb, snapshot);
	}

	private Shortfall resolveInternal(Skill skill, Route route, KnowledgeBase kb, Snapshot snapshot)
	{
		if (route.getUncoveredXp() <= 0)
		{
			return new Shortfall(null, List.of());
		}

		int level = RoutePlanner.capLevel(route.getFinalXp());
		List<MethodEntry> candidates = RoutePlanner.candidatesAt(skill, level, kb);
		if (candidates.isEmpty())
		{
			return new Shortfall(null, List.of());
		}
		// The fastest method is always the primary; when its materials are not all
		// obtainable, the best fully-obtainable candidate is offered as an alternative.
		MethodEntry primary = candidates.get(0);
		MethodEntry obtainable = candidates.stream()
			.filter(candidate -> obtainable(candidate, route, skill, kb, snapshot))
			.findFirst()
			.orElse(null);
		Shortfall alternative = obtainable == null || obtainable == primary
			? null
			: shortfallFor(obtainable, skill, route, kb, snapshot, null);
		return shortfallFor(primary, skill, route, kb, snapshot, alternative);
	}

	private Shortfall shortfallFor(MethodEntry method, Skill skill, Route route, KnowledgeBase kb, Snapshot snapshot,
		Shortfall alternative)
	{
		long actionsNeeded = actionsNeeded(route, method);
		Map<Integer, Integer> simulatedBank = route.getSimulatedBank();

		List<ShortfallItem> items = new ArrayList<>();
		for (ItemQuantity material : method.getMaterials())
		{
			items.add(shortfallItem(material, actionsNeeded, skill, simulatedBank, kb, snapshot));
		}
		return new Shortfall(method, List.copyOf(items), route.getUncoveredXp(), actionsNeeded, alternative,
			unobtainable(items, skill, simulatedBank, kb, snapshot), hiddenNotes(items, kb, snapshot));
	}

	/** The names of the materials {@link #obtainable} rejects - descending into a craft chain to name its leaves, each once. */
	private List<String> unobtainable(List<ShortfallItem> items, Skill skill, Map<Integer, Integer> simulatedBank,
		KnowledgeBase kb, Snapshot snapshot)
	{
		Set<String> names = new LinkedHashSet<>();
		for (ShortfallItem item : items)
		{
			if (obtainable(item.getItem(), item.getNeed(), simulatedBank, skill, kb, snapshot, false))
			{
				continue;
			}
			if (item.getCraftFrom().isEmpty())
			{
				names.add(item.getItem().getName());
			}
			else
			{
				names.addAll(unobtainable(item.getCraftFrom(), skill, simulatedBank, kb, snapshot));
			}
		}
		return List.copyOf(names);
	}

	private static long actionsNeeded(Route route, MethodEntry method)
	{
		return (long) Math.ceil(route.getUncoveredXp() / method.getXpPerAction());
	}

	/** Every material of {@code method} is obtainable for the full {@code need} this candidate implies. */
	private boolean obtainable(MethodEntry method, Route route, Skill skill, KnowledgeBase kb, Snapshot snapshot)
	{
		long actions = actionsNeeded(route, method);
		for (ItemQuantity material : method.getMaterials())
		{
			int need = (int) Math.ceil(actions * material.getQuantity());
			if (!obtainable(material, need, route.getSimulatedBank(), skill, kb, snapshot, true))
			{
				return false;
			}
		}
		return true;
	}

	/**
	 * A material is obtainable when the simulated bank covers {@code need}, it has a curated gathering
	 * plan, a non-iron account can buy it (shop/GE), or ({@code viaCraft}, one level only) it has a
	 * craft source whose intermediate's ingredients are all obtainable by the same rules.
	 */
	private boolean obtainable(ItemQuantity material, int need, Map<Integer, Integer> simulatedBank, Skill skill,
		KnowledgeBase kb, Snapshot snapshot, boolean viaCraft)
	{
		int have = simulatedBank.getOrDefault(material.getId(), 0);
		if (have >= need)
		{
			return true;
		}
		if (plansFor(material.getName(), material.getId(), kb, snapshot).stream()
			.anyMatch(offer -> offer.isMeetsRequirements() && !offer.getSteps().isEmpty()
				|| offer.getAlternativeSteps().stream().anyMatch(steps -> !steps.isEmpty())))
		{
			return true;
		}
		MaterialEntry entry = kb.materialById(material.getId());
		if (entry == null)
		{
			return false;
		}
		boolean iron = snapshot.getAccountType().isIron();
		boolean hasCraft = false;
		for (ItemSource source : entry.getSources())
		{
			String type = source.getType();
			if (!iron && ("shop".equals(type) || "GE".equals(type)))
			{
				return true;
			}
			hasCraft |= "craft".equals(type);
		}
		if (!viaCraft || !hasCraft)
		{
			return false;
		}
		MethodEntry intermediate = findIntermediateProducing(material.getId(), skill, kb);
		if (intermediate == null)
		{
			return false;
		}
		long craftActions = (long) Math.ceil((need - have) / outputQuantity(intermediate, material.getId()));
		for (ItemQuantity ingredient : intermediate.getMaterials())
		{
			int ingredientNeed = (int) Math.ceil(craftActions * ingredient.getQuantity());
			if (!obtainable(ingredient, ingredientNeed, simulatedBank, skill, kb, snapshot, false))
			{
				return false;
			}
		}
		return true;
	}

	private ShortfallItem shortfallItem(ItemQuantity material, long actionsNeeded, Skill skill, Map<Integer, Integer> simulatedBank,
		KnowledgeBase kb, Snapshot snapshot)
	{
		AccountType account = snapshot.getAccountType();
		int need = (int) Math.ceil(actionsNeeded * material.getQuantity());
		int have = simulatedBank.getOrDefault(material.getId(), 0);
		int shortfall = Math.max(0, need - have);

		MaterialEntry materialEntry = kb.materialById(material.getId());
		List<ItemSource> sources = materialEntry == null ? List.of() : GapEngine.sourcesFor(materialEntry.getSources(), account);
		boolean hasCraftSource = sources.stream().anyMatch(s -> "craft".equals(s.getType()));
		List<ShortfallItem> craftFrom = shortfall <= 0 || !hasCraftSource
			? List.of()
			: craftFrom(material.getId(), shortfall, skill, simulatedBank, kb, snapshot);

		// This item's own curated plans, plus (unioned, not recursed into further)
		// each craftFrom ingredient's own plans - so a dust shortfall reached only via the craft
		// chain still surfaces the upstream material's plan.
		List<PlanOffer> plans = new ArrayList<>(plansFor(material.getName(), material.getId(), kb, snapshot));
		for (ShortfallItem ingredient : craftFrom)
		{
			plans.addAll(ingredient.getPlans());
		}

		return new ShortfallItem(material, have, need, sources, craftFrom, materialEntry == null ? null : materialEntry.getWikiUrl(),
			List.copyOf(plans));
	}

	/**
	 * One level of craft recursion: the intermediate method that produces {@code itemId} (if any),
	 * decomposed into its own materials' have/need/sources - never recursed into further.
	 */
	private List<ShortfallItem> craftFrom(int itemId, int parentShortfall, Skill skill, Map<Integer, Integer> simulatedBank,
		KnowledgeBase kb, Snapshot snapshot)
	{
		AccountType account = snapshot.getAccountType();
		MethodEntry intermediate = findIntermediateProducing(itemId, skill, kb);
		if (intermediate == null)
		{
			return List.of();
		}
		double outputQty = outputQuantity(intermediate, itemId);
		long craftActions = (long) Math.ceil(parentShortfall / outputQty);

		List<ShortfallItem> result = new ArrayList<>();
		for (ItemQuantity ingredient : intermediate.getMaterials())
		{
			int need = (int) Math.ceil(craftActions * ingredient.getQuantity());
			int have = simulatedBank.getOrDefault(ingredient.getId(), 0);
			MaterialEntry materialEntry = kb.materialById(ingredient.getId());
			List<ItemSource> ingredientSources = materialEntry == null ? List.of() : GapEngine.sourcesFor(materialEntry.getSources(), account);
			List<PlanOffer> ingredientPlans = plansFor(ingredient.getName(), ingredient.getId(), kb, snapshot);
			result.add(new ShortfallItem(ingredient, have, need, ingredientSources, List.of(),
				materialEntry == null ? null : materialEntry.getWikiUrl(), ingredientPlans));
		}
		return result;
	}

	/** {@code id}-based lookup first, falling back to a name match when {@code id} isn't known or has no plan. */
	private List<PlanOffer> plansFor(String name, Integer id, KnowledgeBase kb, Snapshot snapshot)
	{
		List<GatheringPlan> plans = id != null ? kb.gatheringFor(id) : List.of();
		if (plans.isEmpty())
		{
			plans = kb.gatheringForName(name);
		}
		List<PlanOffer> offers = new ArrayList<>();
		for (GatheringPlan plan : plans)
		{
			if (plan.getRisk().allowed(snapshot.getAccountType(), includeWilderness))
			{
				offers.add(toPlanOffer(plan, snapshot));
			}
			else
			{
				for (GatheringAlternative alternative : plan.getAlternatives())
				{
					if (alternative.getRisk().allowed(snapshot.getAccountType(), includeWilderness))
					{
						offers.add(toPlanOffer(new GatheringPlan(plan.getItem(), plan.getId(), alternative.getTitle(),
							alternative.getRequires(), null, alternative.getSteps(), List.of(), plan.getWikiUrl(), alternative.getRisk()), snapshot));
					}
				}
			}
		}
		return offers;
	}

	/**
	 * Flags a plan {@code meetsRequirements = false} (naming each shortfall) when any
	 * {@code requires.skills} level exceeds the snapshot, and keeps only the steps -
	 * of the plan and of each alternative - whose own requirements the account meets.
	 */
	private PlanOffer toPlanOffer(GatheringPlan plan, Snapshot snapshot)
	{
		List<String> missing = new ArrayList<>();
		for (SkillReq req : plan.getRequires().getSkills())
		{
			int have = snapshot.getSkills().getOrDefault(req.getSkill(), UNKNOWN_SKILL).getLevel();
			if (have < req.getLevel())
			{
				missing.add(req.getSkill().getName() + " " + req.getLevel() + " (have " + have + ")");
			}
		}
		for (String questName : plan.getRequires().getQuests())
		{
			Quest quest = GapEngine.questByName(questName);
			if (quest == null || snapshot.getQuests().get(quest) != QuestState.FINISHED)
			{
				missing.add(questName);
			}
		}
		Integer combat = plan.getRequires().getCombatLevel();
		if (combat != null && snapshot.combatLevel() < combat)
		{
			missing.add("Combat " + combat + " (have " + snapshot.combatLevel() + ")");
		}
		List<GatheringAlternative> alternatives = new ArrayList<>();
		List<List<BringItemStatus>> alternativeItems = new ArrayList<>();
		List<List<String>> alternativeSteps = new ArrayList<>();
		for (GatheringAlternative alternative : plan.getAlternatives())
		{
			if (!alternative.getRisk().allowed(snapshot.getAccountType(), includeWilderness)
				|| !meets(alternative.getRequires(), snapshot))
			{
				continue;
			}
			alternatives.add(alternative);
			alternativeSteps.add(doableSteps(alternative.getSteps(), snapshot));
			alternativeItems.add(NextStepGuidance.items(alternative.getRequires().getItems(), snapshot));
		}
		GatheringPlan visible = new GatheringPlan(plan.getItem(), plan.getId(), plan.getTitle(), plan.getRequires(),
			plan.getRatePerHour(), plan.getSteps(), alternatives, plan.getWikiUrl(), plan.getRisk());
		return new PlanOffer(visible, missing.isEmpty(), missing, doableSteps(plan.getSteps(), snapshot), alternativeSteps,
			NextStepGuidance.items(plan.getRequires().getItems(), snapshot), alternativeItems);
	}

	/** The text of each step whose skill levels the snapshot meets and whose quests it has finished, in order. */
	private static List<String> doableSteps(List<GatheringStep> steps, Snapshot snapshot)
	{
		List<String> doable = new ArrayList<>();
		for (GatheringStep step : steps)
		{
			if (meets(step.getRequires(), snapshot))
			{
				doable.add(step.getText());
			}
		}
		return List.copyOf(doable);
	}

	private static boolean meets(GatheringRequires requires, Snapshot snapshot)
	{
		if (requires.getCombatLevel() != null && snapshot.combatLevel() < requires.getCombatLevel())
		{
			return false;
		}
		for (SkillReq req : requires.getSkills())
		{
			if (snapshot.getSkills().getOrDefault(req.getSkill(), UNKNOWN_SKILL).getLevel() < req.getLevel())
			{
				return false;
			}
		}
		for (String questName : requires.getQuests())
		{
			Quest quest = GapEngine.questByName(questName);
			if (quest == null || snapshot.getQuests().get(quest) != QuestState.FINISHED)
			{
				return false;
			}
		}
		return true;
	}

	private List<String> hiddenNotes(List<ShortfallItem> items, KnowledgeBase kb, Snapshot snapshot)
	{
		Set<GatheringPlan> plans = new LinkedHashSet<>();
		collectPlans(items, kb, plans);
		int wilderness = 0;
		int unsafe = 0;
		for (GatheringPlan plan : plans)
		{
			if (!plan.getRisk().allowed(snapshot.getAccountType(), includeWilderness))
			{
				if (plan.getRisk() == GatheringRisk.WILDERNESS) { wilderness++; }
				else { unsafe++; }
			}
			for (GatheringAlternative alternative : plan.getAlternatives())
			{
				if (!alternative.getRisk().allowed(snapshot.getAccountType(), includeWilderness))
				{
					if (alternative.getRisk() == GatheringRisk.WILDERNESS) { wilderness++; }
					else { unsafe++; }
				}
			}
		}
		List<String> notes = new ArrayList<>();
		if (wilderness > 0)
		{
			notes.add(wilderness + " wilderness plan" + (wilderness == 1 ? "" : "s") + " hidden"
				+ " (enable in settings)");
		}
		if (unsafe > 0)
		{
			notes.add(unsafe + " hardcore-unsafe plan" + (unsafe == 1 ? "" : "s") + " hidden (hardcore account)");
		}
		return notes;
	}

	private static void collectPlans(List<ShortfallItem> items, KnowledgeBase kb, Set<GatheringPlan> plans)
	{
		for (ShortfallItem item : items)
		{
			if (item.getNeed() > item.getHave())
			{
				List<GatheringPlan> matching = item.getItem().getId() != null
					? kb.gatheringFor(item.getItem().getId()) : List.of();
				plans.addAll(matching.isEmpty() ? kb.gatheringForName(item.getItem().getName()) : matching);
				collectPlans(item.getCraftFrom(), kb, plans);
			}
		}
	}

	private static MethodEntry findIntermediateProducing(int itemId, Skill skill, KnowledgeBase kb)
	{
		for (MethodEntry candidate : kb.methodsFor(skill))
		{
			if (candidate.isIntermediate() && candidate.isUsable() && outputQuantity(candidate, itemId) > 0)
			{
				return candidate;
			}
		}
		return null;
	}

	private static double outputQuantity(MethodEntry method, int itemId)
	{
		for (ItemQuantity output : method.getOutputs())
		{
			if (output.getId() != null && output.getId() == itemId)
			{
				return output.getQuantity();
			}
		}
		return 0;
	}
}
