package dev.reece.nta.engine;

import dev.reece.nta.engine.model.Advice;
import dev.reece.nta.kb.KnowledgeBase;
import dev.reece.nta.kb.MilestoneCategory;
import dev.reece.nta.snapshot.Snapshot;
import dev.reece.nta.store.AccountData;
import dev.reece.nta.ui.GoalDetailPanel;
import dev.reece.nta.ui.SuggestPanel;
import java.awt.HeadlessException;
import java.lang.reflect.InvocationTargetException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import net.runelite.api.Skill;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Task 30: not a behaviour test (the Swing panel isn't unit-tested per the plan) - just a smoke
 * check that {@link SuggestPanel} can be built and {@link SuggestPanel#render} run against a
 * hand-built {@link Advice} without throwing, on the EDT. Skips itself if the environment is truly
 * headless (no display for Swing component construction).
 */
class RenderSmokeTest
{
	@Test
	void constructsAndRendersWithoutThrowing() throws Exception
	{
		KnowledgeBase kb = new KbBuilder()
			.milestone("m:barrows-gloves", MilestoneCategory.GEAR, "Barrows gloves", 8)
			.ownedIf("Barrows gloves", 7462)
			.skill(Skill.DEFENCE, 40)
			.build();
		Snapshot snapshot = new SnapshotBuilder().build();
		Advice advice = new Engine(new BoostTable()).run(snapshot, kb, AccountData.empty(), Instant.now());

		Consumer<String> noop = id -> { };
		SuggestPanel.Actions actions = new SuggestPanel.Actions(noop, noop, noop, noop, noop, noop, noop, () -> { });

		try
		{
			SwingUtilities.invokeAndWait(() ->
			{
				SuggestPanel panel = new SuggestPanel(actions);
				panel.render(advice);
			});
		}
		catch (InvocationTargetException e)
		{
			if (e.getCause() instanceof HeadlessException)
			{
				Assumptions.abort("Headless environment cannot construct Swing components: " + e.getCause().getMessage());
			}
			throw e;
		}
	}

	/**
	 * Task 39: same smoke check as above, but for {@link GoalDetailPanel} - a focused quest whose
	 * bank only partly covers its Herblore route, so {@code advice.getFocus()} carries both a
	 * non-empty {@link dev.reece.nta.engine.model.Route} and a non-null
	 * {@link dev.reece.nta.engine.model.Shortfall}.
	 */
	@Test
	void constructsAndRendersGoalDetailPanelWithARouteAndAShortfallWithoutThrowing() throws Exception
	{
		int ranarrUnf = 200;
		KnowledgeBase kb = new KbBuilder()
			.quest(0, "Test Quest").skill(Skill.HERBLORE, 20)
			.method(Skill.HERBLORE, "Prayer potion(3)", 1, 4)
			.material(ranarrUnf, 1)
			.material("Ranarr potion (unf)", ranarrUnf)
			.source("GE", "Grand Exchange")
			.build();
		Snapshot snapshot = new SnapshotBuilder().bankItem(ranarrUnf, "Ranarr potion (unf)", 2).build();
		AccountData data = new AccountData(new HashMap<>(), null, new HashMap<>(), new HashSet<>(), new ArrayList<>(), "quest:0");
		Advice advice = new Engine(new BoostTable()).run(snapshot, kb, data, Instant.now());
		assertNotNull(advice.getFocus(), "fixture must produce a focus for the render to exercise the detail panel");
		assertNotNull(advice.getFocus().getRoute(), "fixture must produce a route");
		assertNotNull(advice.getFocus().getShortfall(), "fixture must produce a shortfall (bank only partly covers the route)");

		Consumer<String> noop = id -> { };
		GoalDetailPanel.Actions actions = new GoalDetailPanel.Actions(noop, () -> { });

		try
		{
			SwingUtilities.invokeAndWait(() ->
			{
				GoalDetailPanel panel = new GoalDetailPanel(actions);
				panel.render(advice);
			});
		}
		catch (InvocationTargetException e)
		{
			if (e.getCause() instanceof HeadlessException)
			{
				Assumptions.abort("Headless environment cannot construct Swing components: " + e.getCause().getMessage());
			}
			throw e;
		}
	}
}
