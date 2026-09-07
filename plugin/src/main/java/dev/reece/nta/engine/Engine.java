package dev.reece.nta.engine;

import dev.reece.nta.engine.model.Advice;
import dev.reece.nta.kb.KnowledgeBase;
import dev.reece.nta.snapshot.Snapshot;
import java.time.Instant;
import lombok.Value;

/**
 * Pure entry point wiring {@link GapEngine} and {@link DiaryProgress} together: one {@link #run}
 * call turns a {@link Snapshot} + {@link KnowledgeBase} into a complete {@link Advice}. No
 * {@link net.runelite.api.Client}, no I/O (global constraint: engine code is pure).
 */
@Value
public class Engine
{
	GapEngine gapEngine;
	BoostTable boostTable;

	public Engine(BoostTable boostTable)
	{
		this.boostTable = boostTable;
		this.gapEngine = new GapEngine(boostTable);
	}

	public Advice run(Snapshot snapshot, KnowledgeBase kb)
	{
		return new Advice(snapshot, gapEngine.evaluate(snapshot, kb), DiaryProgress.compute(snapshot, kb), Instant.now());
	}
}
