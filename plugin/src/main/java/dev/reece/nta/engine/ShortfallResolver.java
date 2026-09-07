package dev.reece.nta.engine;

import dev.reece.nta.engine.model.ItemSource;
import dev.reece.nta.engine.model.Route;
import dev.reece.nta.engine.model.Shortfall;
import dev.reece.nta.engine.model.ShortfallItem;
import dev.reece.nta.kb.ItemQuantity;
import dev.reece.nta.kb.KnowledgeBase;
import dev.reece.nta.kb.MaterialEntry;
import dev.reece.nta.kb.MethodEntry;
import dev.reece.nta.snapshot.AccountType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.runelite.api.Skill;

/**
 * Pure function {@code (skill, route, kb, account) -> Shortfall}: explains a {@link Route}'s
 * {@code uncoveredXp}, per ticket C7. No {@link net.runelite.api.Client}, no I/O.
 */
public final class ShortfallResolver
{
	private ShortfallResolver()
	{
	}

	public static Shortfall resolve(Skill skill, Route route, KnowledgeBase kb, AccountType account)
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
		MethodEntry method = candidates.get(0);

		long actionsNeeded = (long) Math.ceil(route.getUncoveredXp() / method.getXpPerAction());
		Map<Integer, Integer> simulatedBank = route.getSimulatedBank();

		List<ShortfallItem> items = new ArrayList<>();
		for (ItemQuantity material : method.getMaterials())
		{
			items.add(shortfallItem(material, actionsNeeded, skill, simulatedBank, kb, account));
		}
		return new Shortfall(method, List.copyOf(items));
	}

	private static ShortfallItem shortfallItem(ItemQuantity material, long actionsNeeded, Skill skill, Map<Integer, Integer> simulatedBank,
		KnowledgeBase kb, AccountType account)
	{
		int need = (int) Math.ceil(actionsNeeded * material.getQuantity());
		int have = simulatedBank.getOrDefault(material.getId(), 0);
		int shortfall = Math.max(0, need - have);

		MaterialEntry materialEntry = kb.materialById(material.getId());
		List<ItemSource> sources = materialEntry == null ? List.of() : GapEngine.sourcesFor(materialEntry.getSources(), account);
		boolean hasCraftSource = sources.stream().anyMatch(s -> "craft".equals(s.getType()));
		List<ShortfallItem> craftFrom = shortfall <= 0 || !hasCraftSource
			? List.of()
			: craftFrom(material.getId(), shortfall, skill, simulatedBank, kb, account);

		return new ShortfallItem(material, have, need, sources, craftFrom, materialEntry == null ? null : materialEntry.getWikiUrl());
	}

	/**
	 * One level of craft recursion: the intermediate method that produces {@code itemId} (if any),
	 * decomposed into its own materials' have/need/sources - never recursed into further.
	 */
	private static List<ShortfallItem> craftFrom(int itemId, int parentShortfall, Skill skill, Map<Integer, Integer> simulatedBank,
		KnowledgeBase kb, AccountType account)
	{
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
			result.add(new ShortfallItem(ingredient, have, need, ingredientSources, List.of(), materialEntry == null ? null : materialEntry.getWikiUrl()));
		}
		return result;
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
