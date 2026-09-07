package dev.reece.nta.engine;

import dev.reece.nta.engine.model.Route;
import dev.reece.nta.engine.model.RouteStep;
import dev.reece.nta.kb.ItemQuantity;
import dev.reece.nta.kb.KnowledgeBase;
import dev.reece.nta.kb.MethodEntry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Experience;
import net.runelite.api.Skill;

/**
 * Pure function {@code (skill, fromXp, toXp, bank, kb) -> Route}: simulates training a skill from
 * a bank, per spec ruling 15. No {@link net.runelite.api.Client}, no I/O.
 *
 * <p>XP is tracked internally as an exact {@code double} (bundled {@code xpPerAction} values like
 * 87.5 aren't integral); every {@code long}/{@code int} xp or level exposed on {@link Route}/
 * {@link RouteStep} is derived from that double via {@link Math#floor} and
 * {@link Experience#getLevelForXp}, never accumulated as a rounded value itself, so results stay
 * exact regardless of how many steps a route takes.
 */
public final class RoutePlanner
{
	private static final String BARBARIAN_MIX_TYPE = "Barbarian Mix";

	private RoutePlanner()
	{
	}

	public static Route route(Skill skill, long fromXp, long toXp, Map<Integer, Integer> bank, KnowledgeBase kb)
	{
		Map<Integer, Integer> simBank = new LinkedHashMap<>(bank);
		double simXp = fromXp;
		List<RouteStep> steps = new ArrayList<>();

		while (simXp < toXp)
		{
			int simLevel = capLevel(simXp);
			MethodEntry best = null;
			for (MethodEntry candidate : candidatesAt(skill, simLevel, kb))
			{
				if (affordable(candidate, skill, simBank, kb) > 0)
				{
					best = candidate;
					break;
				}
			}
			if (best == null)
			{
				break;
			}

			int affordable = affordable(best, skill, simBank, kb);
			long unlockGap = Math.round(Experience.getXpForLevel(simLevel + 1) - simXp);
			long toTargetGap = Math.round(toXp - simXp);
			int count = (int) Math.min(affordable,
				Math.min(ceilDiv(unlockGap, best.getXpPerAction()), ceilDiv(toTargetGap, best.getXpPerAction())));
			count = Math.max(count, 1);

			List<RouteStep> crafts = new ArrayList<>();
			Map<Integer, Integer> materialsUsed = new LinkedHashMap<>();
			for (ItemQuantity material : best.getMaterials())
			{
				int need = (int) Math.ceil(count * material.getQuantity());
				materialsUsed.merge(material.getId(), need, Integer::sum);
				int haveDirect = simBank.getOrDefault(material.getId(), 0);
				if (haveDirect < need)
				{
					int shortfall = need - haveDirect;
					RouteStep craft = craftShortfall(material.getId(), shortfall, skill, simBank, kb, simLevel);
					if (craft != null)
					{
						crafts.add(craft);
					}
				}
				simBank.merge(material.getId(), -need, Integer::sum);
			}
			for (ItemQuantity output : best.getOutputs())
			{
				int produced = (int) Math.floor(count * output.getQuantity());
				simBank.merge(output.getId(), produced, Integer::sum);
			}

			int fromLevel = simLevel;
			simXp += count * best.getXpPerAction();
			int toLevel = capLevel(simXp);
			long xpGained = (long) Math.floor(count * best.getXpPerAction());

			if (!steps.isEmpty() && steps.get(steps.size() - 1).getMethod().equals(best))
			{
				RouteStep previous = steps.remove(steps.size() - 1);
				Map<Integer, Integer> mergedMaterials = new LinkedHashMap<>(previous.getMaterialsUsed());
				materialsUsed.forEach((id, qty) -> mergedMaterials.merge(id, qty, Integer::sum));
				List<RouteStep> mergedCrafts = new ArrayList<>(previous.getCrafts());
				mergedCrafts.addAll(crafts);
				steps.add(new RouteStep(best, previous.getCount() + count, previous.getFromLevel(), toLevel,
					previous.getXpGained() + xpGained, Map.copyOf(mergedMaterials), List.copyOf(mergedCrafts)));
			}
			else
			{
				steps.add(new RouteStep(best, count, fromLevel, toLevel, xpGained, Map.copyOf(materialsUsed), List.copyOf(crafts)));
			}
		}

		long uncoveredXp = Math.max(0, (long) Math.ceil(toXp - simXp));
		long finalXp = (long) Math.floor(simXp);
		return new Route(List.copyOf(steps), uncoveredXp, finalXp, Map.copyOf(simBank));
	}

	/**
	 * Crafts (from {@code simBank} alone, one intermediate action-batch, no further recursion) as
	 * much of {@code shortfall} units of {@code itemId} as {@link #maxCraftable} allows, consuming
	 * the intermediate's materials and adding its output into {@code simBank}. Returns {@code null}
	 * when no intermediate produces {@code itemId} (the outer material stays short; the caller
	 * already excluded this method from candidates unless the direct bank covered it).
	 */
	private static RouteStep craftShortfall(int itemId, int shortfall, Skill skill, Map<Integer, Integer> simBank, KnowledgeBase kb, int level)
	{
		MethodEntry intermediate = bestIntermediateFor(itemId, skill, simBank, kb);
		if (intermediate == null)
		{
			return null;
		}
		double outputQty = outputQuantity(intermediate, itemId);
		int craftCount = (int) Math.ceil(shortfall / outputQty);

		Map<Integer, Integer> materialsUsed = new LinkedHashMap<>();
		for (ItemQuantity material : intermediate.getMaterials())
		{
			int need = (int) Math.ceil(craftCount * material.getQuantity());
			materialsUsed.merge(material.getId(), need, Integer::sum);
			simBank.merge(material.getId(), -need, Integer::sum);
		}
		int produced = (int) Math.floor(craftCount * outputQty);
		simBank.merge(itemId, produced, Integer::sum);

		return new RouteStep(intermediate, craftCount, level, level, 0, Map.copyOf(materialsUsed), List.of());
	}

	/**
	 * The affordable action count for {@code method} from {@code simBank}, where a short material
	 * may be topped up by {@link #maxCraftable} - one level of intermediate crafting, no deeper
	 * recursion. 0 when any material can't be reached at all.
	 */
	private static int affordable(MethodEntry method, Skill skill, Map<Integer, Integer> simBank, KnowledgeBase kb)
	{
		int min = Integer.MAX_VALUE;
		for (ItemQuantity material : method.getMaterials())
		{
			int have = simBank.getOrDefault(material.getId(), 0) + maxCraftable(material.getId(), skill, simBank, kb);
			int afford = (int) Math.floor(have / material.getQuantity());
			min = Math.min(min, afford);
		}
		return min == Integer.MAX_VALUE ? 0 : min;
	}

	/**
	 * The largest total quantity of {@code itemId} obtainable by running the best (highest-yield,
	 * ties by name) intermediate method that outputs it, using only what's already in
	 * {@code simBank} - never a deeper recursive craft.
	 */
	private static int maxCraftable(int itemId, Skill skill, Map<Integer, Integer> simBank, KnowledgeBase kb)
	{
		MethodEntry best = bestIntermediateFor(itemId, skill, simBank, kb);
		return best == null ? 0 : producibleUnits(best, itemId, simBank);
	}

	private static MethodEntry bestIntermediateFor(int itemId, Skill skill, Map<Integer, Integer> simBank, KnowledgeBase kb)
	{
		MethodEntry best = null;
		int bestUnits = -1;
		for (MethodEntry candidate : kb.methodsFor(skill))
		{
			if (!candidate.isIntermediate() || !candidate.isUsable() || outputQuantity(candidate, itemId) <= 0)
			{
				continue;
			}
			int units = producibleUnits(candidate, itemId, simBank);
			if (units > bestUnits || (units == bestUnits && best != null && candidate.getName().compareTo(best.getName()) < 0))
			{
				best = candidate;
				bestUnits = units;
			}
		}
		return best;
	}

	private static int producibleUnits(MethodEntry intermediate, int itemId, Map<Integer, Integer> simBank)
	{
		if (intermediate.getMaterials().isEmpty())
		{
			return 0;
		}
		int maxActions = Integer.MAX_VALUE;
		for (ItemQuantity material : intermediate.getMaterials())
		{
			int have = simBank.getOrDefault(material.getId(), 0);
			maxActions = Math.min(maxActions, (int) Math.floor(have / material.getQuantity()));
		}
		return (int) Math.floor(maxActions * outputQuantity(intermediate, itemId));
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

	/** Every usable, non-intermediate, real-xp method of {@code skill} unlocked at {@code level}, excluding Barbarian
	 * Mix (ruling 15), ordered by preference: highest xp/action, then lowest levelReq, then name. */
	static List<MethodEntry> candidatesAt(Skill skill, int level, KnowledgeBase kb)
	{
		List<MethodEntry> candidates = new ArrayList<>();
		for (MethodEntry method : kb.methodsFor(skill))
		{
			if (method.isUsable() && !method.isIntermediate() && method.getXpPerAction() > 0 && method.getLevelReq() <= level
				&& !method.getTypes().contains(BARBARIAN_MIX_TYPE))
			{
				candidates.add(method);
			}
		}
		candidates.sort(Comparator
			.comparingDouble(MethodEntry::getXpPerAction).reversed()
			.thenComparingInt(MethodEntry::getLevelReq)
			.thenComparing(MethodEntry::getName));
		return candidates;
	}

	/** Package-visible: also used by {@link ShortfallResolver} to find the level a route stopped at. */
	static int capLevel(double xp)
	{
		return Math.min(Experience.MAX_REAL_LEVEL, Experience.getLevelForXp((int) xp));
	}

	private static long ceilDiv(long numerator, double denominator)
	{
		return (long) Math.ceil(numerator / denominator);
	}
}
