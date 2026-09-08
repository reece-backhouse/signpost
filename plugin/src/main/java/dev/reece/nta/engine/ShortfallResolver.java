package dev.reece.nta.engine;

import dev.reece.nta.engine.model.ItemSource;
import dev.reece.nta.engine.model.PlanOffer;
import dev.reece.nta.engine.model.Route;
import dev.reece.nta.engine.model.Shortfall;
import dev.reece.nta.engine.model.ShortfallItem;
import dev.reece.nta.kb.GatheringAlternative;
import dev.reece.nta.kb.GatheringPlan;
import dev.reece.nta.kb.GatheringRequires;
import dev.reece.nta.kb.GatheringStep;
import dev.reece.nta.kb.ItemQuantity;
import dev.reece.nta.kb.KnowledgeBase;
import dev.reece.nta.kb.MaterialEntry;
import dev.reece.nta.kb.MethodEntry;
import dev.reece.nta.kb.SkillReq;
import dev.reece.nta.snapshot.AccountType;
import dev.reece.nta.snapshot.SkillState;
import dev.reece.nta.snapshot.Snapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;

/**
 * Pure function {@code (skill, route, kb, snapshot) -> Shortfall}: explains a {@link Route}'s
 * {@code uncoveredXp}, per ticket C7. No {@link net.runelite.api.Client}, no I/O.
 */
public final class ShortfallResolver
{
	private static final SkillState UNKNOWN_SKILL = new SkillState(0, 0);

	private ShortfallResolver()
	{
	}

	public static Shortfall resolve(Skill skill, Route route, KnowledgeBase kb, Snapshot snapshot)
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
		// Spec ruling 30: the fastest method is always the primary; when its materials are not all
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

	private static Shortfall shortfallFor(MethodEntry method, Skill skill, Route route, KnowledgeBase kb, Snapshot snapshot,
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
			unobtainable(items, skill, simulatedBank, kb, snapshot));
	}

	/** Task 61: the names of the materials {@link #obtainable} rejects - descending into a craft chain to name its leaves. */
	private static List<String> unobtainable(List<ShortfallItem> items, Skill skill, Map<Integer, Integer> simulatedBank,
		KnowledgeBase kb, Snapshot snapshot)
	{
		List<String> names = new ArrayList<>();
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

	/** Spec ruling 30: every material of {@code method} is obtainable for the full {@code need} this candidate implies. */
	private static boolean obtainable(MethodEntry method, Route route, Skill skill, KnowledgeBase kb, Snapshot snapshot)
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
	private static boolean obtainable(ItemQuantity material, int need, Map<Integer, Integer> simulatedBank, Skill skill,
		KnowledgeBase kb, Snapshot snapshot, boolean viaCraft)
	{
		int have = simulatedBank.getOrDefault(material.getId(), 0);
		if (have >= need)
		{
			return true;
		}
		if (!kb.gatheringFor(material.getId()).isEmpty() || !kb.gatheringForName(material.getName()).isEmpty())
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

	private static ShortfallItem shortfallItem(ItemQuantity material, long actionsNeeded, Skill skill, Map<Integer, Integer> simulatedBank,
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

		// Spec ruling 28: this item's own curated plans, plus (unioned, not recursed into further)
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
	private static List<ShortfallItem> craftFrom(int itemId, int parentShortfall, Skill skill, Map<Integer, Integer> simulatedBank,
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
	private static List<PlanOffer> plansFor(String name, Integer id, KnowledgeBase kb, Snapshot snapshot)
	{
		List<GatheringPlan> plans = id != null ? kb.gatheringFor(id) : List.of();
		if (plans.isEmpty())
		{
			plans = kb.gatheringForName(name);
		}
		List<PlanOffer> offers = new ArrayList<>();
		for (GatheringPlan plan : plans)
		{
			offers.add(toPlanOffer(plan, snapshot));
		}
		return offers;
	}

	/**
	 * Flags a plan {@code meetsRequirements = false} (naming each shortfall) when any
	 * {@code requires.skills} level exceeds the snapshot, and (task 62) keeps only the steps -
	 * of the plan and of each alternative - whose own requirements the account meets.
	 */
	private static PlanOffer toPlanOffer(GatheringPlan plan, Snapshot snapshot)
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
		List<List<String>> alternativeSteps = new ArrayList<>();
		for (GatheringAlternative alternative : plan.getAlternatives())
		{
			alternativeSteps.add(doableSteps(alternative.getSteps(), snapshot));
		}
		return new PlanOffer(plan, missing.isEmpty(), List.copyOf(missing), doableSteps(plan.getSteps(), snapshot), List.copyOf(alternativeSteps));
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
