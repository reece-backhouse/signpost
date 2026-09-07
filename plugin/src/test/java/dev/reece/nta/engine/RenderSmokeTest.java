package dev.reece.nta.engine;

import dev.reece.nta.engine.model.Advice;
import dev.reece.nta.engine.model.Goal;
import dev.reece.nta.engine.model.GoalCategory;
import dev.reece.nta.engine.model.GoalRef;
import dev.reece.nta.engine.model.GoalStatus;
import dev.reece.nta.engine.model.RankedGoal;
import dev.reece.nta.engine.model.Route;
import dev.reece.nta.engine.model.SkillLevelGap;
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
import java.awt.event.MouseEvent;
import java.lang.reflect.InvocationTargetException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import net.runelite.api.Skill;
import net.runelite.client.ui.PluginPanel;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
		Advice base = new Engine(new BoostTable()).run(snapshot, kb, AccountData.empty(), Instant.now());

		// task 52b: append a synthetic SKILL_TARGET goal (parents + a bank-covered route) to the
		// hand-built advice, and an explanation for the existing milestone goal, to exercise the
		// Why? toggle and the skill-target card rendering without needing SkillTargetSynthesiser.
		Goal skillGoal = new Goal("skill:woodcutting:75", GoalCategory.SKILL_TARGET, "75 Woodcutting", null, 0, 1);
		SkillLevelGap skillGap = new SkillLevelGap(Skill.WOODCUTTING, 60, 75, 500_000L, false, null, false);
		GoalRef parent = new GoalRef("quest:song-of-the-elves", "Song of the Elves", 75);
		Route bankRoute = new Route(List.of(), 0L, 1_000_000L, Map.of());
		GoalStatus skillStatus = new GoalStatus(skillGoal, List.of(skillGap), false, false, List.of(), List.of(), List.of(parent), bankRoute);
		RankedGoal skillRanked = new RankedGoal(skillStatus, 10.0, false, false);

		// picked is replaced (not merged) with just the skill target, so it's deterministically the
		// first (and only) "Pick one" card - the goal set the assertions below target.
		List<GoalStatus> statuses = new ArrayList<>(base.getStatuses());
		statuses.add(skillStatus);
		List<RankedGoal> ranked = new ArrayList<>(base.getRanked());
		ranked.add(skillRanked);
		List<RankedGoal> picked = List.of(skillRanked);

		Map<String, List<String>> explanations = new HashMap<>(base.getExplanations());
		explanations.put(skillGoal.getId(), List.of("Needed for Song of the Elves (75 Woodcutting)", "Bank covers 60→75"));

		Advice advice = new Advice(base.getSnapshot(), statuses, base.getDiaryProgress(), base.getComputedAt(),
			ranked, picked, base.getRest(), base.getAccountStage(), base.getLater(), base.getWhys(), explanations,
			base.getReasons(), base.getPrefs(), base.getFocus());

		Consumer<String> noop = id -> { };
		SuggestPanel.Actions actions = new SuggestPanel.Actions(noop, noop, noop, noop, noop, noop, noop, () -> { });

		try
		{
			SwingUtilities.invokeAndWait(() ->
			{
				SuggestPanel panel = new SuggestPanel(actions);
				panel.render(advice);
				layoutAtRealPanelWidth(panel);
				assertNoButtonNarrowerThanItsPreferredWidth(panel);

				JLabel whyToggle = findLabelStartingWith(panel, "Why?");
				assertNotNull(whyToggle, "a Why? toggle must exist on a card");
				assertTrue(whyToggle.getText().endsWith("▶"), "collapsed by default, got: " + whyToggle.getText());
				assertFalse(containsLabelContaining(panel, "Bank covers 60"),
					"explanation line must not be present before the toggle is clicked");

				whyToggle.dispatchEvent(new MouseEvent(whyToggle, MouseEvent.MOUSE_CLICKED,
					System.currentTimeMillis(), 0, 1, 1, 1, false));

				// the toggle click rebuilds the card from scratch (unlike the persistent Later/
				// Snoozed/Ignored Header fields, which mutate their own label in place), so the
				// post-click label must be re-found rather than re-read off the stale reference.
				JLabel whyToggleAfterClick = findLabelStartingWith(panel, "Why?");
				assertNotNull(whyToggleAfterClick, "a Why? toggle must still exist after the click");
				assertTrue(whyToggleAfterClick.getText().endsWith("▼"),
					"clicking the toggle must expand it, got: " + whyToggleAfterClick.getText());
				assertTrue(containsLabelContaining(panel, "Bank covers 60"),
					"explanation line must be present in the tree after expanding");

				assertTrue(containsLabelContaining(panel, "Skill"), "SKILL_TARGET category label 'Skill' must render");
				assertTrue(containsLabelContaining(panel, "for Song of the Elves"), "skill-target card must show its parent goal");
				assertTrue(containsLabelContaining(panel, "materials in bank"), "bank-covered skill target must show the badge");
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
	 * Fix round 1: {@code JButton#getText()} never contains "..." - Swing clips the paint, it
	 * doesn't rewrite the string - so the original check could never fail. This lays the panel
	 * out at the real (scrollbar-adjusted) sidebar width and asserts every button actually got at
	 * least its own preferred width, i.e. a {@link java.awt.GridLayout} row never squeezed it
	 * smaller than its text needs.
	 */
	/**
	 * {@link Container#validate()} is a no-op on a component tree with no realized ancestor
	 * (peer) - confirmed empirically: every descendant stayed at 0x0 bounds even though
	 * {@code getPreferredSize()} was correct throughout. {@code doLayout()} only positions a
	 * container's own direct children, so it has to be walked and called at every level by hand.
	 */
	private static void layoutAtRealPanelWidth(Container container)
	{
		int width = PluginPanel.PANEL_WIDTH - PluginPanel.SCROLLBAR_WIDTH;
		container.setSize(width, container.getPreferredSize().height);
		layoutRecursively(container);
	}

	private static void layoutRecursively(Container container)
	{
		container.doLayout();
		for (Component child : container.getComponents())
		{
			if (child instanceof Container)
			{
				layoutRecursively((Container) child);
			}
		}
	}

	private static void assertNoButtonNarrowerThanItsPreferredWidth(Container container)
	{
		for (Component child : container.getComponents())
		{
			// an invisible component (e.g. the focus banner with no active focus) is skipped by
			// its parent's layout manager and stays at width 0 - that's correct, not truncation.
			if (!child.isVisible())
			{
				continue;
			}
			if (child instanceof JButton)
			{
				JButton b = (JButton) child;
				assertTrue(b.getWidth() >= b.getPreferredSize().width,
					"button '" + b.getText() + "' was laid out " + b.getWidth()
						+ "px wide, narrower than its preferred width " + b.getPreferredSize().width + "px");
			}
			if (child instanceof Container)
			{
				assertNoButtonNarrowerThanItsPreferredWidth((Container) child);
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
	 * Fix round 1: a collapsible section header (Later/Snoozed/Ignored) must toggle when the click
	 * lands on the label the user actually sees ("LATER (0) ▶"), not just on the outer panel -
	 * AWT delivers a click to the deepest component under the cursor and does not bubble it to
	 * ancestors.
	 */
	@Test
	void collapsibleHeaderTogglesWhenClickedOnItsLabel() throws Exception
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

				JLabel laterLabel = findLabelStartingWith(panel, "LATER");
				assertNotNull(laterLabel, "Later section header label must exist");
				assertTrue(laterLabel.getText().endsWith("▶"), "collapsed by default, got: " + laterLabel.getText());

				laterLabel.dispatchEvent(new MouseEvent(laterLabel, MouseEvent.MOUSE_CLICKED,
					System.currentTimeMillis(), 0, 1, 1, 1, false));

				assertTrue(laterLabel.getText().endsWith("▼"),
					"clicking the header's own label must toggle expansion, got: " + laterLabel.getText());
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

	private static JLabel findLabelStartingWith(Container container, String prefix)
	{
		for (Component child : container.getComponents())
		{
			if (child instanceof JLabel && ((JLabel) child).getText().startsWith(prefix))
			{
				return (JLabel) child;
			}
			if (child instanceof Container)
			{
				JLabel found = findLabelStartingWith((Container) child, prefix);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	/** Task 52b: as {@link #findLabelStartingWith}, but a substring match anywhere in the label's text (HTML-wrapped explanation/parent lines included). */
	private static boolean containsLabelContaining(Container container, String substring)
	{
		for (Component child : container.getComponents())
		{
			if (child instanceof JLabel && ((JLabel) child).getText().contains(substring))
			{
				return true;
			}
			if (child instanceof Container && containsLabelContaining((Container) child, substring))
			{
				return true;
			}
		}
		return false;
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
