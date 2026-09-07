package dev.reece.nta.engine;

import dev.reece.nta.engine.model.Route;
import dev.reece.nta.engine.model.RouteStep;
import dev.reece.nta.kb.ItemQuantity;
import dev.reece.nta.kb.KnowledgeBase;
import dev.reece.nta.kb.MethodEntry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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
					RouteStep craft = craftShortfall(material.getId(), shortfall, skill, simBank, kb, simXp);
					if (craft != null)
					{
						simXp += craft.getXpGained();
						crafts.add(craft);
					}
				}
				simBank.merge(material.getId(), -need, Integer::sum);
				assertNonNegative(simBank, material.getId(), best);
			}
			for (ItemQuantity output : best.getOutputs())
			{
				if (output.getId() == null)
				{
					// Unresolved (generic) output: not tracked in the simulated bank, but the method
					// stays usable as long as its own materials (inputs) all resolve.
					continue;
				}
				int produced = (int) Math.floor(count * output.getQuantity());
				simBank.merge(output.getId(), produced, Integer::sum);
			}

			// fromLevel is taken AFTER crediting any craft sub-step xp above: crafting is a real
			// action performed before the method's own action this iteration.
			int fromLevel = capLevel(simXp);
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
	 * already excluded this method from candidates unless the direct bank covered it). Every
	 * bundled intermediate is 0-xp today, but ruling 15 anticipates a non-zero one (e.g. herb
	 * cleaning), so the craft's own xp is credited here too - {@code xpAtStart} is the running
	 * {@code simXp} at the moment this craft runs, used only to report the craft's from/to level.
	 */
	private static RouteStep craftShortfall(int itemId, int shortfall, Skill skill, Map<Integer, Integer> simBank, KnowledgeBase kb,
		double xpAtStart)
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
			assertNonNegative(simBank, material.getId(), intermediate);
		}
		int produced = (int) Math.floor(craftCount * outputQty);
		simBank.merge(itemId, produced, Integer::sum);

		long craftXpGained = (long) Math.floor(craftCount * intermediate.getXpPerAction());
		int fromLevel = capLevel(xpAtStart);
		int toLevel = capLevel(xpAtStart + craftXpGained);
		return new RouteStep(intermediate, craftCount, fromLevel, toLevel, craftXpGained, Map.copyOf(materialsUsed), List.of());
	}

	/** Fail loud (never silently let a simulated bank entry go negative) rather than produce a route that spends items it doesn't have. */
	private static void assertNonNegative(Map<Integer, Integer> bank, int itemId, MethodEntry method)
	{
		Integer qty = bank.get(itemId);
		if (qty != null && qty < 0)
		{
			throw new IllegalStateException(
				"Route simulation drove item " + itemId + " negative (" + qty + ") consuming materials for \"" + method.getName() + "\"");
		}
	}

	/**
	 * The affordable action count for {@code method} from {@code simBank}, where a short material
	 * may be topped up by {@link #maxCraftable} - one level of intermediate crafting, no deeper
	 * recursion. Two materials whose intermediates draw on the same raw ingredient (or that overlap
	 * with the method's own other material) can't both be checked independently against the same
	 * undecremented bank - that double-counts the shared stock and can drive {@code simBank}
	 * negative once actions actually run. So this binary-searches the true count: {@link #feasible}
	 * simulates {@code count} actions (materials + crafts) against a scratch copy of the bank and
	 * reports whether every entry stays non-negative, which is monotonic in {@code count} (higher
	 * count only needs more, never less), so binary search converges on the exact answer.
	 * {@code independentUpperBound} - the old per-material calculation - only bounds the search.
	 */
	private static int affordable(MethodEntry method, Skill skill, Map<Integer, Integer> simBank, KnowledgeBase kb)
	{
		int upperBound = independentUpperBound(method, skill, simBank, kb);
		if (upperBound <= 0)
		{
			return 0;
		}
		int low = 0;
		int high = upperBound;
		while (low < high)
		{
			int mid = low + (high - low + 1) / 2;
			if (feasible(method, mid, skill, simBank, kb))
			{
				low = mid;
			}
			else
			{
				high = mid - 1;
			}
		}
		return low;
	}

	private static int independentUpperBound(MethodEntry method, Skill skill, Map<Integer, Integer> simBank, KnowledgeBase kb)
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

	/** Whether {@code count} actions of {@code method} are jointly affordable: simulated against a scratch copy of
	 * {@code bank}, crafting each short material (one level, from whatever the scratch bank has left at that point)
	 * and failing as soon as a material can't be fully covered. */
	private static boolean feasible(MethodEntry method, int count, Skill skill, Map<Integer, Integer> bank, KnowledgeBase kb)
	{
		Map<Integer, Integer> scratch = new HashMap<>(bank);
		for (ItemQuantity material : method.getMaterials())
		{
			int need = (int) Math.ceil(count * material.getQuantity());
			int have = scratch.getOrDefault(material.getId(), 0);
			if (have < need && !craftIntoScratch(material.getId(), need - have, skill, scratch, kb))
			{
				return false;
			}
			scratch.merge(material.getId(), -need, Integer::sum);
		}
		return true;
	}

	/** As {@link #craftShortfall}, but on a throwaway scratch bank and reporting success/failure instead of
	 * recording a {@link RouteStep} - used only by {@link #feasible} to probe a candidate count. */
	private static boolean craftIntoScratch(int itemId, int shortfall, Skill skill, Map<Integer, Integer> scratch, KnowledgeBase kb)
	{
		MethodEntry intermediate = bestIntermediateFor(itemId, skill, scratch, kb);
		if (intermediate == null || producibleUnits(intermediate, itemId, scratch) < shortfall)
		{
			return false;
		}
		double outputQty = outputQuantity(intermediate, itemId);
		int craftCount = (int) Math.ceil(shortfall / outputQty);
		for (ItemQuantity material : intermediate.getMaterials())
		{
			int need = (int) Math.ceil(craftCount * material.getQuantity());
			scratch.merge(material.getId(), -need, Integer::sum);
		}
		scratch.merge(itemId, (int) Math.floor(craftCount * outputQty), Integer::sum);
		return true;
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
