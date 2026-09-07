package dev.reece.nta.engine;

import dev.reece.nta.engine.model.Advice;
import dev.reece.nta.engine.model.GoalStatus;
import dev.reece.nta.engine.model.PrefsView;
import dev.reece.nta.engine.model.RankedGoal;
import dev.reece.nta.kb.KnowledgeBase;
import dev.reece.nta.kb.MilestoneEntry;
import dev.reece.nta.snapshot.Snapshot;
import dev.reece.nta.store.AccountData;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Value;

/**
 * Pure entry point wiring {@link GapEngine}, {@link DiaryProgress}, {@link PrefsResolver},
 * {@link Ranker}, {@link SuggestSelector}, and {@link WhyBuilder} together: one {@link #run} call
 * turns a {@link Snapshot} + {@link KnowledgeBase} + {@link AccountData} into a complete
 * {@link Advice}. No {@link net.runelite.api.Client}, no I/O (global constraint: engine code is
 * pure).
 */
@Value
public class Engine
{
	GapEngine gapEngine;
	BoostTable boostTable;
	PrefsResolver prefsResolver;
	Ranker ranker;
	SuggestSelector suggestSelector;
	WhyBuilder whyBuilder;

	public Engine(BoostTable boostTable)
	{
		this.boostTable = boostTable;
		this.gapEngine = new GapEngine(boostTable);
		this.prefsResolver = new PrefsResolver();
		this.ranker = new Ranker();
		this.suggestSelector = new SuggestSelector();
		this.whyBuilder = new WhyBuilder();
	}

	public Advice run(Snapshot snapshot, KnowledgeBase kb, AccountData data, Instant now)
	{
		List<GoalStatus> statuses = gapEngine.evaluate(snapshot, kb);
		PrefsView prefs = prefsResolver.resolve(data, statuses, now);
		List<RankedGoal> ranked = ranker.rank(statuses, prefs.getHidden(), prefs.getPins());
		List<RankedGoal> picked = suggestSelector.pick3(ranked);
		List<RankedGoal> rest = suggestSelector.rest(ranked, picked);

		Map<String, String> whys = new LinkedHashMap<>();
		Map<String, String> reasons = new LinkedHashMap<>();
		for (RankedGoal r : ranked)
		{
			String goalId = r.getStatus().getGoal().getId();
			whys.put(goalId, whyBuilder.why(r, kb, snapshot));

			MilestoneEntry entry = kb.milestoneById(goalId);
			if (entry != null && entry.getReason() != null && !entry.getReason().isEmpty())
			{
				reasons.put(goalId, entry.getReason());
			}
		}

		return new Advice(snapshot, statuses, DiaryProgress.compute(snapshot, kb), now, ranked, picked, rest, whys, reasons, prefs);
	}

	/** Thin overload for callers with no account data (e.g. existing tests): behaves as {@link #run} with an empty {@link AccountData} and the current time. */
	public Advice run(Snapshot snapshot, KnowledgeBase kb)
	{
		return run(snapshot, kb, AccountData.empty(), Instant.now());
	}
}
