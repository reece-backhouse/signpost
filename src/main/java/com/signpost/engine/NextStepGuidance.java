package com.signpost.engine;

import com.signpost.engine.model.BringItemStatus;
import com.signpost.engine.model.NextStep;
import com.signpost.engine.model.PlanOffer;
import com.signpost.engine.model.RouteStep;
import com.signpost.engine.model.Shortfall;
import com.signpost.engine.model.ShortfallItem;
import com.signpost.kb.BringItem;
import com.signpost.kb.ItemQuantity;
import com.signpost.kb.KnowledgeBase;
import com.signpost.kb.MethodEntry;
import com.signpost.kb.MethodGuidance;
import com.signpost.snapshot.Snapshot;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pure presentation derived from the already selected route and risk-filtered offers. */
public final class NextStepGuidance
{
	private NextStepGuidance() {}

	public static List<BringItemStatus> items(List<BringItem> items, Snapshot snapshot)
	{
		Map<Integer, Integer> owned = NextStepPicker.bankAll(snapshot);
		List<BringItemStatus> result = new ArrayList<>();
		for (BringItem item : items)
		{
			result.add(new BringItemStatus(item, item.getQuantity(), owned.getOrDefault(item.getId(), 0)));
		}
		return List.copyOf(result);
	}

	public static NextStep derive(NextStep next, Shortfall shortfall, Snapshot snapshot, KnowledgeBase kb)
	{
		if (next.getRoute() != null && !next.getRoute().getSteps().isEmpty())
		{
			RouteStep step = next.getRoute().getSteps().get(0);
			while (!step.getCrafts().isEmpty())
			{
				step = step.getCrafts().get(0);
			}
			if (step.getQuest() != null) { return next; }
			NextStep training = method(next, step, snapshot, kb);
			if (training.hasGuidance()) { return training; }
		}
		if (shortfall != null)
		{
			NextStep gathering = gathering(next, shortfall.getItems());
			if (gathering.hasGuidance()) { return gathering; }
			if (shortfall.getAlternative() != null)
			{
				return gathering(next, shortfall.getAlternative().getItems());
			}
		}
		return next;
	}

	private static NextStep method(NextStep next, RouteStep step, Snapshot snapshot, KnowledgeBase kb)
	{
		MethodEntry method = step.getMethod();
		Map<Integer, Integer> owned = NextStepPicker.bankAll(snapshot);
		int count = step.getCount();
		// Merged route steps can include later batches: recommend only the batch affordable now.
		for (ItemQuantity material : method.getMaterials())
		{
			if (material.getId() == null) { return next; }
			count = Math.min(count, (int) Math.floor(owned.getOrDefault(material.getId(), 0) / material.getQuantity()));
		}
		if (count <= 0) { return next; }
		MethodGuidance guidance = kb.guidanceFor(method.getName());
		Map<Integer, BringItemStatus> bring = new LinkedHashMap<>();
		for (ItemQuantity material : method.getMaterials())
		{
			long quantity = (long) Math.ceil(material.getQuantity() * count);
			bring.put(material.getId(), new BringItemStatus(new BringItem(material.getName(), material.getId()), quantity,
				owned.getOrDefault(material.getId(), 0)));
		}
		for (BringItem tool : guidance.getBring())
		{
			bring.putIfAbsent(tool.getId(), new BringItemStatus(tool, tool.getQuantity(), owned.getOrDefault(tool.getId(), 0)));
		}
		String output = method.getOutputs().isEmpty() ? method.getName() : method.getOutputs().get(0).getName();
		long outputCount = method.getOutputs().isEmpty() ? count : (long) Math.ceil(method.getOutputs().get(0).getQuantity() * count);
		return next.withGuidance(List.copyOf(bring.values()), guidance.locationFor(method), "Make " + outputCount + " " + output);
	}

	private static NextStep gathering(NextStep next, List<ShortfallItem> items)
	{
		for (ShortfallItem item : items)
		{
			if (item.getHave() >= item.getNeed()) { continue; }
			for (PlanOffer offer : item.getPlans())
			{
				// Ingredient offers are also collected on their parent item for display.
				// Select them through craftFrom so already-owned ingredients are skipped.
				if ((item.getItem().getId() == null || offer.getPlan().getId() != item.getItem().getId())
					&& !offer.getPlan().getItem().equals(item.getItem().getName())) { continue; }
				if (offer.isMeetsRequirements() && !offer.getSteps().isEmpty())
				{
					String first = offer.getSteps().get(0);
					return next.withGuidance(offer.getItems(), firstSentence(first), first);
				}
				for (int i = 0; i < offer.getAlternativeSteps().size(); i++)
				{
					List<String> steps = offer.getAlternativeSteps().get(i);
					if (!steps.isEmpty())
					{
						String first = steps.get(0);
						return next.withGuidance(offer.getAlternativeItems().get(i), firstSentence(first), first);
					}
				}
			}
			NextStep ingredient = gathering(next, item.getCraftFrom());
			if (ingredient.hasGuidance()) { return ingredient; }
		}
		return next;
	}

	private static String firstSentence(String text)
	{
		int end = text.indexOf(". ");
		return end < 0 ? text : text.substring(0, end + 1);
	}
}
