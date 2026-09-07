package dev.reece.nta.engine;

import dev.reece.nta.engine.model.Advice;
import dev.reece.nta.kb.KnowledgeBase;
import dev.reece.nta.kb.MilestoneCategory;
import dev.reece.nta.snapshot.Snapshot;
import dev.reece.nta.store.AccountData;
import dev.reece.nta.ui.GoalDetailPanel;
import dev.reece.nta.ui.GoalSearchField;
import dev.reece.nta.ui.SuggestPanel;
import java.awt.Component;
import java.awt.Container;
import java.awt.HeadlessException;
import java.lang.reflect.InvocationTargetException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import net.runelite.api.Skill;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
				assertNoTruncatedButtonText(panel);
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

	/** Task 49: no card/row/section button should ever fall back to an ellipsised label. */
	private static void assertNoTruncatedButtonText(Container container)
	{
		for (Component child : container.getComponents())
		{
			if (child instanceof JButton)
			{
				String text = ((JButton) child).getText();
				assertTrue(text == null || (!text.contains("...") && !text.contains("…")),
					"button text must never be truncated with an ellipsis, got: " + text);
			}
			if (child instanceof Container)
			{
				assertNoTruncatedButtonText((Container) child);
			}
		}
	}

	/** Final-review I4: a re-render with the same goal set (bank close, level-up) must not collapse "Show more" back to the first page. */
	@Test
	void showMorePagingSurvivesARenderWithTheSameGoalSet() throws Exception
	{
		KbBuilder builder = new KbBuilder();
		for (int i = 1; i <= 15; i++)
		{
			builder.quest(i, "Quest " + i);
		}
		KnowledgeBase kb = builder.build();
		Snapshot snapshot = new SnapshotBuilder().build();
		Engine engine = new Engine(new BoostTable());
		Advice first = engine.run(snapshot, kb, AccountData.empty(), Instant.now());
		Advice sameGoals = engine.run(snapshot, kb, AccountData.empty(), Instant.now());
		Advice fewerGoals = engine.run(snapshot, new KbBuilder().quest(1, "Quest 1").quest(2, "Quest 2").build(), AccountData.empty(), Instant.now());

		Consumer<String> noop = id -> { };
		SuggestPanel.Actions actions = new SuggestPanel.Actions(noop, noop, noop, noop, noop, noop, noop, () -> { });
		int[] counts = new int[4];

		try
		{
			SwingUtilities.invokeAndWait(() ->
			{
				SuggestPanel panel = new SuggestPanel(actions);
				panel.render(first);
				counts[0] = panel.shownNextCount();
				panel.showMore();
				counts[1] = panel.shownNextCount();
				panel.render(sameGoals);
				counts[2] = panel.shownNextCount();
				panel.render(fewerGoals);
				counts[3] = panel.shownNextCount();
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

		int rest = first.getRest().size();
		assertTrue(rest > 10, "fixture must overflow one page, rest=" + rest);
		assertEquals(10, counts[0], "first page");
		assertEquals(rest, counts[1], "everything shown after Show more");
		assertEquals(rest, counts[2], "same goal set: paging kept");
		assertEquals(0, counts[3], "different goal set: paging reset (2 goals, both picked, nothing in Next)");
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

	/**
	 * Task 47: {@link GoalSearchField} - hand-built {@link Advice} carrying an unowned "Barrows
	 * gloves" milestone, typing a substring of its name into the field, and checking the results
	 * list surfaces a "Barrows gloves" result button.
	 */
	@Test
	void goalSearchFieldFindsAMatchingGoalWithoutThrowing() throws Exception
	{
		KnowledgeBase kb = new KbBuilder()
			.milestone("m:barrows-gloves", MilestoneCategory.GEAR, "Barrows gloves", 8)
			.ownedIf("Barrows gloves", 7462)
			.skill(Skill.DEFENCE, 40)
			.build();
		Snapshot snapshot = new SnapshotBuilder().build();
		Advice advice = new Engine(new BoostTable()).run(snapshot, kb, AccountData.empty(), Instant.now());

		try
		{
			SwingUtilities.invokeAndWait(() ->
			{
				GoalSearchField field = new GoalSearchField(id -> { });
				field.render(advice);

				JTextField textField = findTextField(field);
				assertNotNull(textField, "GoalSearchField must contain a JTextField to type a query into");
				textField.setText("barrows");

				assertTrue(containsButtonWithText(field, "Barrows gloves"),
					"search results should list a goal whose name contains the typed substring");
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

	private static JTextField findTextField(Container container)
	{
		for (Component child : container.getComponents())
		{
			if (child instanceof JTextField)
			{
				return (JTextField) child;
			}
			if (child instanceof Container)
			{
				JTextField found = findTextField((Container) child);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	private static boolean containsButtonWithText(Container container, String text)
	{
		for (Component child : container.getComponents())
		{
			if (child instanceof JButton && text.equals(((JButton) child).getText()))
			{
				return true;
			}
			if (child instanceof Container && containsButtonWithText((Container) child, text))
			{
				return true;
			}
		}
		return false;
	}
}
