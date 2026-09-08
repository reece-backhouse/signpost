package dev.reece.nta.engine;

import dev.reece.nta.engine.model.Advice;
import dev.reece.nta.engine.model.FocusDetail;
import dev.reece.nta.engine.model.Goal;
import dev.reece.nta.engine.model.GoalCategory;
import dev.reece.nta.engine.model.GoalRef;
import dev.reece.nta.engine.model.GoalStatus;
import dev.reece.nta.engine.model.PlanOffer;
import dev.reece.nta.engine.model.RankedGoal;
import dev.reece.nta.engine.model.Route;
import dev.reece.nta.engine.model.RouteStep;
import dev.reece.nta.engine.model.Shortfall;
import dev.reece.nta.engine.model.ShortfallItem;
import dev.reece.nta.engine.model.SkillLevelGap;
import dev.reece.nta.engine.model.SkillPlan;
import dev.reece.nta.kb.GatheringAlternative;
import dev.reece.nta.kb.GatheringPlan;
import dev.reece.nta.kb.GatheringRequires;
import dev.reece.nta.kb.GatheringStep;
import dev.reece.nta.kb.ItemQuantity;
import dev.reece.nta.kb.MethodEntry;
import dev.reece.nta.kb.KnowledgeBase;
import dev.reece.nta.kb.MilestoneCategory;
import dev.reece.nta.snapshot.Snapshot;
import dev.reece.nta.store.AccountData;
import dev.reece.nta.store.AccountDataMutations;
import dev.reece.nta.ui.GoalDetailPanel;
import dev.reece.nta.ui.GoalSearchField;
import dev.reece.nta.ui.Icons;
import dev.reece.nta.ui.NextTargetPanel;
import dev.reece.nta.ui.ProgressBar;
import dev.reece.nta.ui.SuggestPanel;
import java.awt.Component;
import java.awt.Container;
import java.awt.HeadlessException;
import java.awt.event.MouseEvent;
import java.lang.reflect.InvocationTargetException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import net.runelite.api.Skill;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.ui.PluginPanel;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
		// fix round 1: 5 parents, to exercise skillTargetDetail's cap at 3 + "+2 more".
		List<GoalRef> parents = List.of(
			new GoalRef("quest:song-of-the-elves", "Song of the Elves", 75),
			new GoalRef("quest:parent-b", "Parent B", 75),
			new GoalRef("quest:parent-c", "Parent C", 75),
			new GoalRef("quest:parent-d", "Parent D", 75),
			new GoalRef("quest:parent-e", "Parent E", 75));
		Route bankRoute = new Route(List.of(), 0L, 1_000_000L, Map.of());
		// main added GoalStatus.parentScore (b4e8915); 0 is correct here since this fixture is
		// bank-covered, not an uncovered target, and the hand-built picked list below bypasses
		// Ranker/pick3 entirely, so no code path reads this value.
		GoalStatus skillStatus = new GoalStatus(skillGoal, List.of(skillGap), false, false, List.of(), List.of(), parents, bankRoute, 0);
		RankedGoal skillRanked = new RankedGoal(skillStatus, 10.0, false, false);

		// picked is replaced (not merged) with just the skill target, so it's deterministically the
		// first (and only) "Pick one" card - the goal set the assertions below target.
		List<GoalStatus> statuses = new ArrayList<>(base.getStatuses());
		statuses.add(skillStatus);
		List<RankedGoal> ranked = new ArrayList<>(base.getRanked());
		ranked.add(skillRanked);
		List<RankedGoal> picked = List.of(skillRanked);

		Map<String, List<String>> explanations = new HashMap<>(base.getExplanations());
		explanations.put(skillGoal.getId(), List.of("Needed for Song of the Elves (75 Woodcutting)", "Bank covers 60-75"));

		Advice advice = new Advice(base.getSnapshot(), statuses, base.getDiaryProgress(), base.getComputedAt(),
			ranked, picked, base.getRest(), base.getAccountStage(), base.getLater(), base.getWhys(), explanations,
			base.getReasons(), base.getOwnedManuallyNames(), base.getPrefs(), base.getFocus());

		Consumer<String> noop = id -> { };
		SuggestPanel.Actions actions = new SuggestPanel.Actions(noop, noop, noop, noop, noop, noop, noop, () -> { }, noop, noop, () -> 7);

		try
		{
			SwingUtilities.invokeAndWait(() ->
			{
				SuggestPanel panel = new SuggestPanel(actions, icons());
				panel.render(advice);
				layoutAtRealPanelWidth(panel);
				assertNoButtonNarrowerThanItsPreferredWidth(panel);

				JLabel whyToggle = findLabelStartingWith(panel, "Why?");
				assertNotNull(whyToggle, "a Why? toggle must exist on a card");
				assertTrue(whyToggle.getText().endsWith("[+]"), "collapsed by default, got: " + whyToggle.getText());
				assertFalse(containsLabelContaining(panel, "Bank covers 60"),
					"explanation line must not be present before the toggle is clicked");

				whyToggle.dispatchEvent(new MouseEvent(whyToggle, MouseEvent.MOUSE_CLICKED,
					System.currentTimeMillis(), 0, 1, 1, 1, false));

				// the toggle click rebuilds the card from scratch (unlike the persistent Later/
				// Snoozed/Ignored Header fields, which mutate their own label in place), so the
				// post-click label must be re-found rather than re-read off the stale reference.
				JLabel whyToggleAfterClick = findLabelStartingWith(panel, "Why?");
				assertNotNull(whyToggleAfterClick, "a Why? toggle must still exist after the click");
				assertTrue(whyToggleAfterClick.getText().endsWith("[-]"),
					"clicking the toggle must expand it, got: " + whyToggleAfterClick.getText());
				assertTrue(containsLabelContaining(panel, "Bank covers 60"),
					"explanation line must be present in the tree after expanding");

				assertTrue(containsLabelContaining(panel, "Skill"), "SKILL_TARGET category label 'Skill' must render");
				assertTrue(containsLabelContaining(panel, "for Song of the Elves"), "skill-target card must show its parent goal");
				assertTrue(containsLabelContaining(panel, "Parent C"), "third parent name must render (cap is 3)");
				assertFalse(containsLabelContaining(panel, "Parent D"), "fourth parent must be capped, not rendered");
				assertTrue(containsLabelContaining(panel, "+2 more"), "capped parents must show a +N more suffix");
				assertTrue(containsLabelContaining(panel, "materials in bank"), "bank-covered skill target must show the badge");
				assertTrue(containsIconOnlyLabel(panel), "task 57: the skill-target card must carry the skill's icon");
				// RL-011 AC1: a thin xp progress bar with "have/need" text, the detail view's component
				assertTrue(containsLabelContaining(panel, "60/75"), "skill-target card must show have/need level text");
				assertTrue(containsComponentOfType(panel, ProgressBar.class), "skill-target card must carry a ProgressBar");
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
	 * Ticket 55 addition: {@link NextTargetPanel}'s header shows a "stale" hint (warning colour)
	 * when the bank snapshot is more than 60 minutes older than {@code computedAt} - compared to
	 * that, not wall-clock, so the check is deterministic in a test.
	 */
	@Test
	void headerShowsStaleBankHintWhenBankAsOfIsOverAnHourBeforeComputedAt() throws Exception
	{
		Instant bankAsOf = Instant.parse("2026-09-07T10:00:00Z");
		Instant computedAt = bankAsOf.plus(2, ChronoUnit.HOURS);
		Snapshot snapshot = new SnapshotBuilder().build().toBuilder().bankAsOf(bankAsOf).build();
		Advice advice = new Engine(new BoostTable()).run(snapshot, new KbBuilder().build(), AccountData.empty(), computedAt);

		Consumer<String> noop = id -> { };
		SuggestPanel.Actions actions = new SuggestPanel.Actions(noop, noop, noop, noop, noop, noop, noop, () -> { }, noop, noop, () -> 7);
		GoalDetailPanel.Actions detailActions = new GoalDetailPanel.Actions(noop, () -> { });

		try
		{
			SwingUtilities.invokeAndWait(() ->
			{
				NextTargetPanel panel = new NextTargetPanel(() -> { }, actions, detailActions, null, new SkillIconManager());
				panel.render(advice);
				assertTrue(containsLabelContaining(panel, "stale"),
					"bank line must show the stale hint when bankAsOf is over 60 minutes before computedAt");
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
		// task 57: a panel with no parent returns from revalidate() before invalidating, so after a
		// re-render (a toggle click) every BoxLayout still holds the sizes it cached the first time.
		// Invalidate the whole tree by hand first so the measurements below reflect the current content.
		invalidateRecursively(container);
		int width = PluginPanel.PANEL_WIDTH - PluginPanel.SCROLLBAR_WIDTH;
		container.setSize(width, container.getPreferredSize().height);
		layoutRecursively(container);
	}

	private static void invalidateRecursively(Container container)
	{
		container.invalidate();
		for (Component child : container.getComponents())
		{
			if (child instanceof Container)
			{
				invalidateRecursively((Container) child);
			}
			else
			{
				child.invalidate();
			}
		}
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
		SuggestPanel.Actions actions = new SuggestPanel.Actions(noop, noop, noop, noop, noop, noop, noop, () -> { }, noop, noop, () -> 7);
		int[] counts = new int[4];

		try
		{
			SwingUtilities.invokeAndWait(() ->
			{
				SuggestPanel panel = new SuggestPanel(actions, icons());
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
	 * Ticket 55: a MILESTONE/SLAYER_TARGET/BOSS card renders an "Own it" (or, for a boss, "Done it")
	 * action alongside "Do this"/"Not now"/"Ignore" without squeezing any button below its preferred
	 * width, and a manually-owned goal (never emitted by GapEngine, so it can't render as a card
	 * itself) surfaces in a collapsed "Owned (manual)" section with an "Unmark" button.
	 */
	@Test
	void ownItButtonRendersOnAMilestoneCardAndOwnedManualSectionListsAnUnmarkableName() throws Exception
	{
		KnowledgeBase kb = new KbBuilder()
			.milestone("m:barrows-gloves", MilestoneCategory.GEAR, "Barrows gloves", 8)
			.ownedIf("Barrows gloves", 7462)
			.skill(Skill.DEFENCE, 40)
			.milestone("boss:test", MilestoneCategory.BOSS, "Test Boss", 5)
			.build();
		Snapshot snapshot = new SnapshotBuilder().build();
		Advice unowned = new Engine(new BoostTable()).run(snapshot, kb, AccountData.empty(), Instant.now());

		AccountData ownedData = new AccountData(new HashMap<>(), null, new HashMap<>(), new HashSet<>(), new ArrayList<>(), null,
			new HashSet<>(java.util.Set.of("m:barrows-gloves")));
		Advice owned = new Engine(new BoostTable()).run(snapshot, kb, ownedData, Instant.now());

		Consumer<String> noop = id -> { };
		SuggestPanel.Actions actions = new SuggestPanel.Actions(noop, noop, noop, noop, noop, noop, noop, () -> { }, noop, noop, () -> 7);

		try
		{
			SwingUtilities.invokeAndWait(() ->
			{
				SuggestPanel panel = new SuggestPanel(actions, icons());
				panel.render(unowned);
				layoutAtRealPanelWidth(panel);
				assertNoButtonNarrowerThanItsPreferredWidth(panel);
				assertTrue(containsButtonWithText(panel, "Own it"), "a milestone card must render an Own it button");
				assertTrue(containsButtonWithText(panel, "Done it"), "a boss card/row must render a Done it button");

				panel.render(owned);
				JLabel ownedHeaderLabel = findLabelStartingWith(panel, "OWNED (MANUAL)");
				assertNotNull(ownedHeaderLabel, "Owned (manual) section header must exist");
				assertTrue(ownedHeaderLabel.getText().contains("(1)"),
					"header count must reflect one manually-owned goal: " + ownedHeaderLabel.getText());

				ownedHeaderLabel.dispatchEvent(new MouseEvent(ownedHeaderLabel, MouseEvent.MOUSE_CLICKED,
					System.currentTimeMillis(), 0, 1, 1, 1, false));

				assertTrue(containsLabelContaining(panel, "Barrows gloves"), "the manually-owned goal's name must render");
				assertTrue(containsButtonWithText(panel, "Unmark"), "an Unmark button must render in the Owned (manual) section");
				layoutAtRealPanelWidth(panel);
				assertNoButtonNarrowerThanItsPreferredWidth(panel);
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
	 * RL-011 AC2: after "Not now" on a card, the render that drops the card shows a one-line grey
	 * status where the card was, and the render after that shows nothing.
	 */
	@Test
	void notNowLeavesAOneRenderStatusLineWhereTheCardWas() throws Exception
	{
		KnowledgeBase kb = new KbBuilder()
			.milestone("m:barrows-gloves", MilestoneCategory.GEAR, "Barrows gloves", 8)
			.ownedIf("Barrows gloves", 7462)
			.skill(Skill.DEFENCE, 40)
			.build();
		Snapshot snapshot = new SnapshotBuilder().build();
		Engine engine = new Engine(new BoostTable());
		Advice before = engine.run(snapshot, kb, AccountData.empty(), Instant.now());
		AccountData snoozed = AccountDataMutations.snooze(AccountData.empty(), "m:barrows-gloves", Instant.now().plus(7, ChronoUnit.DAYS), null);
		Advice after = engine.run(snapshot, kb, snoozed, Instant.now());

		Consumer<String> noop = id -> { };
		SuggestPanel.Actions actions = new SuggestPanel.Actions(noop, noop, noop, noop, noop, noop, noop, () -> { }, noop, noop, () -> 7);

		try
		{
			SwingUtilities.invokeAndWait(() ->
			{
				SuggestPanel panel = new SuggestPanel(actions, icons());
				panel.render(before);
				assertFalse(containsLabelContaining(panel, "Not now:"), "no status before any click");
				findButtonWithText(panel, "Not now").doClick();

				panel.render(after);
				assertTrue(containsLabelContaining(panel, "Not now: hidden for 7 days or until something changes"),
					"the render after the click must show the status line");
				layoutAtRealPanelWidth(panel);
				assertNothingEndsPastTheRightEdge(panel);

				panel.render(after);
				assertFalse(containsLabelContaining(panel, "Not now:"), "the status line lasts one render only");
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
	 * Fix round 1: a collapsible section header (Later/Snoozed/Ignored) must toggle when the click
	 * lands on the label the user actually sees ("LATER (0) [+]"), not just on the outer panel -
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
		SuggestPanel.Actions actions = new SuggestPanel.Actions(noop, noop, noop, noop, noop, noop, noop, () -> { }, noop, noop, () -> 7);

		try
		{
			SwingUtilities.invokeAndWait(() ->
			{
				SuggestPanel panel = new SuggestPanel(actions, icons());
				panel.render(advice);

				JLabel laterLabel = findLabelStartingWith(panel, "LATER");
				assertNotNull(laterLabel, "Later section header label must exist");
				assertTrue(laterLabel.getText().endsWith("[+]"), "collapsed by default, got: " + laterLabel.getText());

				laterLabel.dispatchEvent(new MouseEvent(laterLabel, MouseEvent.MOUSE_CLICKED,
					System.currentTimeMillis(), 0, 1, 1, 1, false));

				assertTrue(laterLabel.getText().endsWith("[-]"),
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
	/** Wraps each text as a {@link GatheringStep} carrying {@code requires} (task 62). */
	private static List<GatheringStep> steps(GatheringRequires requires, String... texts)
	{
		List<GatheringStep> steps = new ArrayList<>();
		for (String text : texts)
		{
			steps.add(new GatheringStep(text, requires));
		}
		return steps;
	}

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
		AccountData data = new AccountData(new HashMap<>(), null, new HashMap<>(), new HashSet<>(), new ArrayList<>(), "quest:0", new HashSet<>());
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
				GoalDetailPanel panel = new GoalDetailPanel(actions, icons());
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

	/** Ticket 58: a missing quest item whose {@link dev.reece.nta.engine.model.ItemGap} carries a resolved item id renders an icon row under "Missing", not just a wiki-linked name. */
	@Test
	void goalDetailPanelRendersAnIconForAMissingItemWithAResolvedItemId() throws Exception
	{
		KnowledgeBase kb = new KbBuilder()
			.quest(0, "Test Quest").item("Steel full helm", 1).material("Steel full helm", 1157)
			.build();
		Snapshot snapshot = new SnapshotBuilder().build();
		AccountData data = new AccountData(new HashMap<>(), null, new HashMap<>(), new HashSet<>(), new ArrayList<>(), "quest:0", new HashSet<>());
		Advice advice = new Engine(new BoostTable()).run(snapshot, kb, data, Instant.now());
		assertNotNull(advice.getFocus(), "fixture must produce a focus for the render to exercise the detail panel");

		Consumer<String> noop = id -> { };
		GoalDetailPanel.Actions actions = new GoalDetailPanel.Actions(noop, () -> { });

		try
		{
			SwingUtilities.invokeAndWait(() ->
			{
				GoalDetailPanel panel = new GoalDetailPanel(actions, icons());
				panel.render(advice);
				assertTrue(containsComponentNamed(panel, Icons.PLACEHOLDER_NAME),
					"a missing item with a resolved item id must render an item-icon label (placeholder without an ItemManager)");
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
	 * Task 52b-2, spec ruling 28: as {@link #constructsAndRendersGoalDetailPanelWithARouteAndAShortfallWithoutThrowing},
	 * but the real engine-produced shortfall item's {@code plans} is replaced with a hand-built
	 * {@link PlanOffer} carrying two steps and one alternative, to exercise the gathering-plan
	 * rendering and its "Alternatives" toggle without needing a KbBuilder gathering-plan chain.
	 */
	@Test
	void goalDetailPanelRendersAGatheringPlanWithStepsAndAnAlternative() throws Exception
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
		AccountData data = new AccountData(new HashMap<>(), null, new HashMap<>(), new HashSet<>(), new ArrayList<>(), "quest:0", new HashSet<>());
		Advice base = new Engine(new BoostTable()).run(snapshot, kb, data, Instant.now());
		assertNotNull(base.getFocus(), "fixture must produce a focus for the render to exercise the detail panel");
		assertNotNull(base.getFocus().getShortfall(), "fixture must produce a shortfall (bank only partly covers the route)");
		ShortfallItem baseItem = base.getFocus().getShortfall().getItems().get(0);

		GatheringRequires requires = new GatheringRequires(List.of(), null, List.of(), List.of(), null);
		GatheringAlternative alternative = new GatheringAlternative("Kill blue dragons",
			steps(requires, "Travel to the dragon lair", "Kill and collect scales"), requires);
		// task 62: the plan carries a Weiss step the engine dropped from the offer (quest not done)
		GatheringPlan plan = new GatheringPlan("Ranarr potion (unf)", ranarrUnf, "Farm ranarr weeds", requires, 120,
			steps(requires, "Plant ranarr seeds at Falador farm", "Weiss: plant at the disease-free patch", "Harvest and return"),
			List.of(alternative), "https://oldschool.runescape.wiki/w/Ranarr_weed");
		PlanOffer offer = new PlanOffer(plan, true, List.of(), List.of("Plant ranarr seeds at Falador farm", "Harvest and return"),
			List.of(List.of("Travel to the dragon lair", "Kill and collect scales")));
		ShortfallItem itemWithPlan = new ShortfallItem(baseItem.getItem(), baseItem.getHave(), baseItem.getNeed(),
			baseItem.getSources(), baseItem.getCraftFrom(), baseItem.getWikiUrl(), List.of(offer));
		Shortfall shortfall = new Shortfall(base.getFocus().getShortfall().getMethod(), List.of(itemWithPlan));
		// task 57: the shortfall renders under its skill's row, so it must be on the SkillPlan too
		SkillPlan basePlan = base.getFocus().getNextSkillPlan();
		assertNotNull(basePlan, "fixture must produce a next skill plan");
		SkillPlan planWithShortfall = new SkillPlan(basePlan.getSkill(), basePlan.getFromLevel(), basePlan.getToLevel(), basePlan.getFromXp(),
			basePlan.getToXp(), basePlan.isRecommended(), basePlan.getRoute(), shortfall, false, basePlan.getSource());
		FocusDetail focus = new FocusDetail(base.getFocus().getStatus(), base.getFocus().getNext(), base.getFocus().getRoute(), shortfall,
			base.getFocus().getFromLevel(), base.getFocus().getToLevel(), List.of(planWithShortfall), planWithShortfall);

		Advice advice = new Advice(base.getSnapshot(), base.getStatuses(), base.getDiaryProgress(), base.getComputedAt(),
			base.getRanked(), base.getPicked(), base.getRest(), base.getAccountStage(), base.getLater(), base.getWhys(),
			base.getExplanations(), base.getReasons(), base.getOwnedManuallyNames(), base.getPrefs(), focus);

		Consumer<String> noop = id -> { };
		GoalDetailPanel.Actions actions = new GoalDetailPanel.Actions(noop, () -> { });

		try
		{
			SwingUtilities.invokeAndWait(() ->
			{
				GoalDetailPanel panel = new GoalDetailPanel(actions, icons());
				panel.render(advice);
				layoutAtRealPanelWidth(panel);
				assertNoButtonNarrowerThanItsPreferredWidth(panel);

				assertTrue(containsLabelContaining(panel, "Farm ranarr weeds"), "plan title must render");
				assertTrue(containsLabelContaining(panel, "Plant ranarr seeds at Falador farm"), "first step must render");
				assertTrue(containsLabelContaining(panel, "2. Harvest and return"), "steps must be numbered after filtering");
				assertFalse(containsLabelContaining(panel, "Weiss"), "a step the offer dropped must not render");
				assertTrue(containsLabelContaining(panel, "Alternatives (1)"), "alternatives toggle must render");
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

	/**
	 * Task 57: the detail view must take only its own preferred height inside a body taller than
	 * it (the sidebar viewport), never stretching its sections apart - the whitespace the user saw
	 * with the old {@link java.awt.CardLayout}, which sized both views to the taller Suggest list.
	 */
	@Test
	void goalDetailViewDoesNotStretchToFillATallBody() throws Exception
	{
		int ranarrUnf = 200;
		KnowledgeBase kb = new KbBuilder()
			.quest(0, "Test Quest").skill(Skill.HERBLORE, 20)
			.method(Skill.HERBLORE, "Prayer potion(3)", 1, 4)
			.material(ranarrUnf, 1)
			.material("Ranarr potion (unf)", ranarrUnf)
			.source("GE", "Grand Exchange")
			.build();
		// the header formats bankAsOf, which SnapshotBuilder.bankItem leaves null
		Snapshot snapshot = new SnapshotBuilder().bankItem(ranarrUnf, "Ranarr potion (unf)", 2).build()
			.toBuilder().bankAsOf(Instant.now()).build();
		AccountData data = new AccountData(new HashMap<>(), null, new HashMap<>(), new HashSet<>(), new ArrayList<>(), "quest:0", new HashSet<>());
		Advice advice = new Engine(new BoostTable()).run(snapshot, kb, data, Instant.now());
		assertNotNull(advice.getFocus(), "fixture must produce a focus");

		Consumer<String> noop = id -> { };
		SuggestPanel.Actions actions = new SuggestPanel.Actions(noop, noop, noop, noop, noop, noop, noop, () -> { }, noop, noop, () -> 7);
		GoalDetailPanel.Actions detailActions = new GoalDetailPanel.Actions(noop, () -> { });

		try
		{
			SwingUtilities.invokeAndWait(() ->
			{
				NextTargetPanel panel = new NextTargetPanel(() -> { }, actions, detailActions, null, new SkillIconManager());
				panel.render(advice);
				GoalDetailPanel detail = findDetailPanel(panel);
				assertNotNull(detail, "detail view must be in the tree when advice has a focus");

				int width = PluginPanel.PANEL_WIDTH - PluginPanel.SCROLLBAR_WIDTH;
				panel.setSize(width, 3000);
				layoutRecursively(panel);

				assertEquals(detail.getPreferredSize().height, detail.getHeight(),
					"detail view laid out in a 3000px body must keep its preferred height, not stretch");
				int childrenPreferred = 0;
				for (Component child : detail.getComponents())
				{
					if (child.isVisible())
					{
						assertEquals(child.getPreferredSize().height, child.getHeight(),
							"section " + child.getClass().getSimpleName() + " must not be stretched");
						childrenPreferred += child.getPreferredSize().height;
					}
				}
				assertTrue(detail.getHeight() < 2 * childrenPreferred,
					"detail height " + detail.getHeight() + " must be close to the sum of its sections " + childrenPreferred);
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
	 * Task 57: every skill gap under "Missing" is a row that expands into its {@link SkillPlan}'s
	 * route. Two plans - Herblore (the next step: uncovered, with a shortfall carrying a gathering
	 * plan) and Woodcutting (covered, one route step whose output has an item id): the next plan's
	 * row starts expanded and the other collapsed; clicking the collapsed row's label swaps them;
	 * the route output renders an item-icon label (the placeholder path, since no ItemManager is
	 * given); no button is squeezed and nothing stretches.
	 */
	@Test
	void skillRowsExpandOneAtATimeIntoTheirRoutesWithItemIcons() throws Exception
	{
		int ranarrUnf = 200;
		KnowledgeBase kb = new KbBuilder()
			.quest(0, "Test Quest").skill(Skill.HERBLORE, 20).skill(Skill.WOODCUTTING, 75)
			.method(Skill.HERBLORE, "Prayer potion(3)", 1, 4)
			.material(ranarrUnf, 1)
			.material("Ranarr potion (unf)", ranarrUnf)
			.source("GE", "Grand Exchange")
			.build();
		Snapshot snapshot = new SnapshotBuilder().bankItem(ranarrUnf, "Ranarr potion (unf)", 2).build();
		AccountData data = new AccountData(new HashMap<>(), null, new HashMap<>(), new HashSet<>(), new ArrayList<>(), "quest:0", new HashSet<>());
		Advice base = new Engine(new BoostTable()).run(snapshot, kb, data, Instant.now());
		assertNotNull(base.getFocus(), "fixture must produce a focus");
		assertEquals(2, base.getFocus().getSkillPlans().size(), "fixture must produce one plan per skill gap");

		SkillPlan herblore = null;
		for (SkillPlan plan : base.getFocus().getSkillPlans())
		{
			if (plan.getSkill() == Skill.HERBLORE)
			{
				herblore = plan;
			}
		}
		assertNotNull(herblore, "fixture must plan Herblore");
		assertNotNull(herblore.getShortfall(), "Herblore must be uncovered (bank has 2 of the unf potions)");
		ShortfallItem baseItem = herblore.getShortfall().getItems().get(0);
		GatheringRequires requires = new GatheringRequires(List.of(), null, List.of(), List.of(), null);
		GatheringPlan plan = new GatheringPlan("Ranarr potion (unf)", ranarrUnf, "Farm ranarr weeds", requires, 120,
			steps(requires, "Plant ranarr seeds at Falador farm", "Harvest and return"), List.of(),
			"https://oldschool.runescape.wiki/w/Ranarr_weed");
		ShortfallItem itemWithPlan = new ShortfallItem(baseItem.getItem(), baseItem.getHave(), baseItem.getNeed(),
			baseItem.getSources(), baseItem.getCraftFrom(), baseItem.getWikiUrl(),
			List.of(new PlanOffer(plan, true, List.of(), List.of("Plant ranarr seeds at Falador farm", "Harvest and return"), List.of())));
		SkillPlan herblorePlan = new SkillPlan(Skill.HERBLORE, herblore.getFromLevel(), herblore.getToLevel(), herblore.getFromXp(),
			herblore.getToXp(), false, herblore.getRoute(), new Shortfall(herblore.getShortfall().getMethod(), List.of(itemWithPlan)),
			false, "quest");

		int willowLogs = 1519;
		int axe = 1351;
		MethodEntry chopWillows = new MethodEntry(Skill.WOODCUTTING, "Chop willow trees", "Chop willow trees", 30, 67.5,
			List.of(new ItemQuantity("Axe", axe, 0)), List.of(new ItemQuantity("Willow logs", willowLogs, 1)),
			List.of(), false, false, null, false, true);
		RouteStep chop = new RouteStep(chopWillows, 13_875, 60, 75, 936_679L, Map.of(axe, 1), List.of());
		SkillPlan woodcuttingPlan = new SkillPlan(Skill.WOODCUTTING, 60, 75, 273_742L, 1_210_421L, false,
			new Route(List.of(chop), 0L, 1_210_421L, Map.of()), null, true, "quest");

		FocusDetail focus = new FocusDetail(base.getFocus().getStatus(), base.getFocus().getNext(), herblorePlan.getRoute(),
			herblorePlan.getShortfall(), herblorePlan.getFromLevel(), herblorePlan.getToLevel(),
			List.of(herblorePlan, woodcuttingPlan), herblorePlan);
		Advice advice = new Advice(base.getSnapshot(), base.getStatuses(), base.getDiaryProgress(), base.getComputedAt(),
			base.getRanked(), base.getPicked(), base.getRest(), base.getAccountStage(), base.getLater(), base.getWhys(),
			base.getExplanations(), base.getReasons(), base.getOwnedManuallyNames(), base.getPrefs(), focus);

		Consumer<String> noop = id -> { };
		GoalDetailPanel.Actions actions = new GoalDetailPanel.Actions(noop, () -> { });

		try
		{
			SwingUtilities.invokeAndWait(() ->
			{
				GoalDetailPanel panel = new GoalDetailPanel(actions, icons());
				panel.render(advice);
				layoutAtRealPanelWidth(panel);
				assertNoButtonNarrowerThanItsPreferredWidth(panel);

				// by name, not text: the Why? explanation above "Missing" also says "Woodcutting 1/75"
				JLabel herbloreRow = findLabelNamed(panel, GoalDetailPanel.SKILL_ROW_NAME + "HERBLORE");
				JLabel woodcuttingRow = findLabelNamed(panel, GoalDetailPanel.SKILL_ROW_NAME + "WOODCUTTING");
				assertNotNull(herbloreRow, "Herblore skill row must exist");
				assertNotNull(woodcuttingRow, "Woodcutting skill row must exist");
				assertTrue(woodcuttingRow.getText().contains("Woodcutting 1/75"), "skill row shows have/need: " + woodcuttingRow.getText());
				assertTrue(containsLabelContaining(panel, "materials in bank"), "covered Woodcutting plan must show the badge");

				// next skill plan (Herblore) starts expanded: its route step and shortfall plan are in the tree; Woodcutting's route is not
				assertTrue(containsLabelContaining(panel, "Prayer potion(3) ×2"), "expanded Herblore row must show its route step");
				assertTrue(containsLabelContaining(panel, "Farm ranarr weeds"), "expanded Herblore row must show its shortfall's gathering plan");
				assertTrue(containsLabelContaining(panel, "1. Plant ranarr seeds"), "gathering plan steps must be numbered");
				assertFalse(containsLabelContaining(panel, "Chop willow trees"), "collapsed Woodcutting row must not show its route");

				woodcuttingRow.dispatchEvent(new MouseEvent(woodcuttingRow, MouseEvent.MOUSE_CLICKED,
					System.currentTimeMillis(), 0, 1, 1, 1, false));

				assertTrue(containsLabelContaining(panel, "Chop willow trees ×13,875"), "clicking the Woodcutting label must expand its route");
				assertTrue(containsLabelContaining(panel, "60-75, +936,679 xp"), "route step must show levels and xp");
				assertFalse(containsLabelContaining(panel, "Prayer potion(3) ×2"), "expanding Woodcutting must collapse Herblore");
				assertTrue(containsComponentNamed(panel, Icons.PLACEHOLDER_NAME),
					"a route output must render an item-icon label (placeholder without an ItemManager)");

				layoutAtRealPanelWidth(panel);
				assertNoButtonNarrowerThanItsPreferredWidth(panel);
				for (Component child : panel.getComponents())
				{
					if (child.isVisible())
					{
						assertEquals(child.getPreferredSize().height, child.getHeight(),
							"section " + child.getClass().getSimpleName() + " must not be stretched");
					}
				}
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
	 * Task 59 A: a real-engine focus whose expanded Herblore row nests a route with a craft
	 * sub-step, then a shortfall whose item has a craft chain (Ranarr weed + Vial of water) with a
	 * gathering plan and long source lines - every descendant, laid out at the real sidebar
	 * width, must end inside the panel. Before the fix every nested label wrapped at the full
	 * panel width regardless of its indent and icon column, so its text painted past the edge.
	 */
	@Test
	void everyDetailRowEndsInsideThePanelWhenNestedUnderAnExpandedSkillRow() throws Exception
	{
		Advice advice = craftChainFixture();

		Consumer<String> noop = id -> { };
		GoalDetailPanel.Actions actions = new GoalDetailPanel.Actions(noop, () -> { });

		try
		{
			SwingUtilities.invokeAndWait(() ->
			{
				GoalDetailPanel panel = new GoalDetailPanel(actions, icons());
				panel.render(advice);
				layoutAtRealPanelWidth(panel);
				assertTrue(containsLabelContaining(panel, "Ranarr weed"), "the craft chain ingredient must render");
				assertTrue(containsLabelContaining(panel, "Gather Ranarr weed"), "the ingredient's gathering plan must render");
				assertNothingEndsPastTheRightEdge(panel);
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
	 * Task 59: a focused quest needing Herblore 20 - the bank brews a few Prayer potions (crafting
	 * some unfinished potions first), then falls short; the unfinished potion is craftable from
	 * Ranarr weed + Vial of water, and Ranarr weed has a curated gathering plan (which the engine
	 * also unions into the parent item, spec ruling 28).
	 */
	private static Advice craftChainFixture()
	{
		int ranarrUnf = 200;
		int ranarr = 201;
		int vial = 202;
		KnowledgeBase kb = new KbBuilder()
			.quest(0, "Test Quest").skill(Skill.HERBLORE, 20)
			.method(Skill.HERBLORE, "Prayer potion(3)", 1, 4)
			.material(ranarrUnf, 1)
			.method(Skill.HERBLORE, "Ranarr potion (unf)", 1, 0)
			.material(ranarr, 1)
			.material(vial, 1)
			.output(ranarrUnf, 1)
			.intermediate()
			.material("Ranarr potion (unf)", ranarrUnf)
			.source("craft", "Ranarr weed + Vial of water")
			.source("shop", "Myths' Guild Herbalist 1 gp, stock 100 – restocks slowly")
			.material("Ranarr weed", ranarr)
			.source("drop", "Chaos druids in Taverley Dungeon; take the long way round past the poison spiders")
			.material("Vial of water", vial)
			.source("shop", "Any general store")
			.gatheringPlan("Ranarr weed", ranarr)
			.build();
		Snapshot snapshot = new SnapshotBuilder()
			.bankItem(ranarrUnf, "Ranarr potion (unf)", 2)
			.bankItem(ranarr, "Ranarr weed", 3)
			.bankItem(vial, "Vial of water", 3)
			.build();
		AccountData data = new AccountData(new HashMap<>(), null, new HashMap<>(), new HashSet<>(), new ArrayList<>(), "quest:0", new HashSet<>());
		Advice advice = new Engine(new BoostTable()).run(snapshot, kb, data, Instant.now());
		assertNotNull(advice.getFocus(), "fixture must produce a focus");
		SkillPlan herblore = advice.getFocus().getNextSkillPlan();
		assertNotNull(herblore, "fixture must produce a next skill plan");
		assertNotNull(herblore.getShortfall(), "fixture must leave Herblore short");
		assertFalse(herblore.getRoute().getSteps().get(0).getCrafts().isEmpty(), "fixture route must craft unfinished potions first");
		assertFalse(herblore.getShortfall().getItems().get(0).getCraftFrom().isEmpty(), "fixture shortfall must carry a craft chain");
		return advice;
	}

	/** Task 59 A: every visible descendant's right edge, in {@code root}'s coordinates, is within {@code root}'s width. */
	private static void assertNothingEndsPastTheRightEdge(Container root)
	{
		List<String> offenders = new ArrayList<>();
		collectPastTheRightEdge(root, root, offenders);
		assertTrue(offenders.isEmpty(), "past the panel's width " + root.getWidth() + ":\n" + String.join("\n", offenders));
	}

	private static void collectPastTheRightEdge(Container container, Container root, List<String> offenders)
	{
		for (Component child : container.getComponents())
		{
			if (!child.isVisible())
			{
				continue;
			}
			int right = SwingUtilities.convertPoint(child, child.getWidth(), 0, root).x;
			int preferredRight = SwingUtilities.convertPoint(child, child.getPreferredSize().width, 0, root).x;
			if (right > root.getWidth() || preferredRight > root.getWidth())
			{
				offenders.add(describe(child) + " ends at x=" + right + " (preferred " + preferredRight + ")");
			}
			if (child instanceof Container)
			{
				collectPastTheRightEdge((Container) child, root, offenders);
			}
		}
	}

	private static String describe(Component c)
	{
		String text = c instanceof JLabel ? " '" + ((JLabel) c).getText() + "'" : "";
		return c.getClass().getSimpleName() + text;
	}

	/**
	 * Task 59 B: RuneLite's RuneScape font has no glyph for the arrows and triangles the panels
	 * used ("61→62", "▶"/"▼"), which painted as boxes and bars. Every label in both panels, with
	 * the detail's skill row expanded, must use only ASCII plus the few symbols the font does have.
	 */
	@Test
	void panelsUseOnlyGlyphsTheRunescapeFontHas() throws Exception
	{
		Advice advice = craftChainFixture();

		Consumer<String> noop = id -> { };
		SuggestPanel.Actions actions = new SuggestPanel.Actions(noop, noop, noop, noop, noop, noop, noop, () -> { }, noop, noop, () -> 7);
		GoalDetailPanel.Actions detailActions = new GoalDetailPanel.Actions(noop, () -> { });

		try
		{
			SwingUtilities.invokeAndWait(() ->
			{
				SuggestPanel suggest = new SuggestPanel(actions, icons());
				suggest.render(advice);
				assertOnlyRenderableGlyphs(suggest);

				GoalDetailPanel detail = new GoalDetailPanel(detailActions, icons());
				detail.render(advice);
				assertTrue(containsLabelContaining(detail, "Prayer potion(3)"), "the skill row must be expanded so its route renders");
				assertOnlyRenderableGlyphs(detail);

				// task 61: the quest marker's text fallback (when the world-map icon isn't loadable) must be renderable too
				JPanel fallback = new JPanel();
				fallback.add(new JLabel(Icons.QUEST_GLYPH));
				assertOnlyRenderableGlyphs(fallback);
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

	/** Task 59 B: no label text has a character above U+007F other than "×", "—", "…" and "·" (the ones the RuneScape font renders; "’" is bold-only). */
	private static void assertOnlyRenderableGlyphs(Container container)
	{
		List<String> offenders = new ArrayList<>();
		collectUnrenderableGlyphs(container, offenders);
		assertTrue(offenders.isEmpty(), "labels with glyphs the RuneScape font lacks:\n" + String.join("\n", offenders));
	}

	private static void collectUnrenderableGlyphs(Container container, List<String> offenders)
	{
		for (Component child : container.getComponents())
		{
			if (child instanceof JLabel)
			{
				String text = ((JLabel) child).getText();
				for (int i = 0; i < text.length(); i++)
				{
					char c = text.charAt(i);
					if (c > 0x7F && "×—…·".indexOf(c) < 0)
					{
						offenders.add(String.format("U+%04X in '%s'", (int) c, text));
						break;
					}
				}
			}
			if (child instanceof Container)
			{
				collectUnrenderableGlyphs((Container) child, offenders);
			}
		}
	}

	/** Task 59 C2: a route step whose method is typed "Cleaning grimy herbs" reads "Clean Kwuarm", not the bare herb name. */
	@Test
	void cleaningStepRendersAsCleanPlusTheHerbName() throws Exception
	{
		Advice advice = foldableRouteFixture();
		Consumer<String> noop = id -> { };
		GoalDetailPanel.Actions actions = new GoalDetailPanel.Actions(noop, () -> { });
		try
		{
			SwingUtilities.invokeAndWait(() ->
			{
				GoalDetailPanel panel = new GoalDetailPanel(actions, icons());
				panel.render(advice);
				assertTrue(containsLabelContaining(panel, "Clean Kwuarm ×40"), "a cleaning step must read 'Clean <herb>'");
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
	 * Task 59 C3: steps worth under 2% of the route's xp fold into one collapsed "+ N small steps"
	 * toggle at the end of the route; clicking it lists them. A 0-xp craft is never folded.
	 */
	@Test
	void smallRouteStepsFoldIntoAToggleAtTheEndOfTheRoute() throws Exception
	{
		Advice advice = foldableRouteFixture();
		Consumer<String> noop = id -> { };
		GoalDetailPanel.Actions actions = new GoalDetailPanel.Actions(noop, () -> { });
		try
		{
			SwingUtilities.invokeAndWait(() ->
			{
				GoalDetailPanel panel = new GoalDetailPanel(actions, icons());
				panel.render(advice);
				assertTrue(containsLabelContaining(panel, "Prayer potion(3) ×340"), "the big step must render");
				assertTrue(containsLabelContaining(panel, "craft Ranarr potion (unf) ×100"), "a 0-xp craft is never folded");
				assertFalse(containsLabelContaining(panel, "Attack potion(3) ×2"), "a step under 2% of the route's xp must be folded");
				JLabel toggle = findLabelStartingWith(panel, "+ 1 small step");
				assertNotNull(toggle, "a collapsed small-steps toggle must render at the end of the route");
				assertTrue(toggle.getText().contains("(+50 xp)") && toggle.getText().endsWith("[+]"), "toggle text: " + toggle.getText());

				toggle.dispatchEvent(new MouseEvent(toggle, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 1, 1, 1, false));

				assertTrue(containsLabelContaining(panel, "Attack potion(3) ×2"), "expanding the toggle must list the small step");
				JLabel expanded = findLabelStartingWith(panel, "+ 1 small step");
				assertNotNull(expanded);
				assertTrue(expanded.getText().endsWith("[-]"), "toggle text after click: " + expanded.getText());
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
	 * Task 59 C4: a step's materials are a strip of chips (item icon + "×N") on a line under its
	 * text rather than an east strip of bare icons, so the text column gets the full row width;
	 * a 0-xp craft drops its "from ..." line (the chips carry it). Nothing ends past the edge.
	 */
	@Test
	void stepMaterialsRenderAsChipsUnderTheStepText() throws Exception
	{
		Advice advice = foldableRouteFixture();
		Consumer<String> noop = id -> { };
		GoalDetailPanel.Actions actions = new GoalDetailPanel.Actions(noop, () -> { });
		try
		{
			SwingUtilities.invokeAndWait(() ->
			{
				GoalDetailPanel panel = new GoalDetailPanel(actions, icons());
				panel.render(advice);
				assertNotNull(findLabelWithText(panel, "×340"), "the brewing step's unfinished potions must render as a ×N chip");
				assertNotNull(findLabelWithText(panel, "×100"), "the craft's ingredients must render as ×N chips");
				assertFalse(containsLabelContaining(panel, "from Ranarr weed"), "the craft's 'from ...' line is replaced by its chips");
				layoutAtRealPanelWidth(panel);
				assertNothingEndsPastTheRightEdge(panel);
				assertOnlyRenderableGlyphs(panel);
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
	 * Task 59 C: {@link #craftChainFixture()}'s focus with its Herblore plan replaced by a
	 * hand-built covered route - brewing 340 Prayer potions (crafting 100 unfinished ones first),
	 * cleaning 40 Kwuarm (a "Cleaning grimy herbs" method), and 2 Attack potions worth 50 xp of
	 * the route's 34,800 (under 2%).
	 */
	private static Advice foldableRouteFixture()
	{
		Advice base = craftChainFixture();
		int unf = 200;
		int ranarr = 201;
		int vial = 202;
		int prayerPot = 203;
		int grimyKwuarm = 204;
		int kwuarm = 205;
		int guamUnf = 206;
		int attackPot = 207;
		MethodEntry makeUnf = new MethodEntry(Skill.HERBLORE, "Ranarr potion (unf)", "Ranarr potion (unf)", 30, 0,
			List.of(new ItemQuantity("Ranarr weed", ranarr, 1), new ItemQuantity("Vial of water", vial, 1)),
			List.of(new ItemQuantity("Ranarr potion (unf)", unf, 1)), List.of(), true, false, null, true, true);
		MethodEntry brew = new MethodEntry(Skill.HERBLORE, "Prayer potion(3)", "Prayer potion(3)", 38, 87.5,
			List.of(new ItemQuantity("Ranarr potion (unf)", unf, 1)), List.of(new ItemQuantity("Prayer potion(3)", prayerPot, 1)),
			List.of(), true, false, null, false, true);
		MethodEntry clean = new MethodEntry(Skill.HERBLORE, "Kwuarm", "Kwuarm", 54, 11.3,
			List.of(new ItemQuantity("Grimy kwuarm", grimyKwuarm, 1)), List.of(new ItemQuantity("Kwuarm", kwuarm, 1)),
			List.of("Cleaning grimy herbs"), true, false, null, false, true);
		MethodEntry attack = new MethodEntry(Skill.HERBLORE, "Attack potion(3)", "Attack potion(3)", 3, 25,
			List.of(new ItemQuantity("Guam potion (unf)", guamUnf, 1)), List.of(new ItemQuantity("Attack potion(3)", attackPot, 1)),
			List.of(), true, false, null, false, true);
		RouteStep craft = new RouteStep(makeUnf, 100, 61, 61, 0L, Map.of(ranarr, 100, vial, 100), List.of());
		RouteStep brewStep = new RouteStep(brew, 340, 61, 66, 29_750L, Map.of(unf, 340), List.of(craft));
		RouteStep cleanStep = new RouteStep(clean, 40, 66, 66, 5_000L, Map.of(grimyKwuarm, 40), List.of());
		RouteStep attackStep = new RouteStep(attack, 2, 66, 66, 50L, Map.of(guamUnf, 2), List.of());
		long fromXp = 302_288L;
		long toXp = 737_627L;
		Route route = new Route(List.of(brewStep, cleanStep, attackStep), 0L, toXp, Map.of());
		SkillPlan plan = new SkillPlan(Skill.HERBLORE, 61, 70, fromXp, toXp, false, route, null, true, "quest");
		FocusDetail focus = new FocusDetail(base.getFocus().getStatus(), base.getFocus().getNext(), route, null, 61, 70, List.of(plan), plan);
		return new Advice(base.getSnapshot(), base.getStatuses(), base.getDiaryProgress(), base.getComputedAt(),
			base.getRanked(), base.getPicked(), base.getRest(), base.getAccountStage(), base.getLater(), base.getWhys(),
			base.getExplanations(), base.getReasons(), base.getOwnedManuallyNames(), base.getPrefs(), focus);
	}

	private static JLabel findLabelWithText(Container container, String text)
	{
		for (Component child : container.getComponents())
		{
			if (child instanceof JLabel && text.equals(((JLabel) child).getText()))
			{
				return (JLabel) child;
			}
			if (child instanceof Container)
			{
				JLabel found = findLabelWithText((Container) child, text);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	/** Task 59 D1 / task 61: the shortfall header says how much xp is still uncovered, the method, and how many actions close it. */
	@Test
	void shortfallHeaderNamesTheUncoveredXpAndTheMethod() throws Exception
	{
		Advice advice = craftChainFixture();
		SkillPlan herblore = advice.getFocus().getNextSkillPlan();
		String expected = "Then still short ~" + String.format(java.util.Locale.ENGLISH, "%,d", herblore.getRoute().getUncoveredXp())
			+ " xp: Prayer potion(3) ×" + grouped(herblore.getShortfall().getActionsNeeded());
		Consumer<String> noop = id -> { };
		GoalDetailPanel.Actions actions = new GoalDetailPanel.Actions(noop, () -> { });
		try
		{
			SwingUtilities.invokeAndWait(() ->
			{
				GoalDetailPanel panel = new GoalDetailPanel(actions, icons());
				panel.render(advice);
				assertTrue(containsLabelContaining(panel, expected), "shortfall header must read '" + expected + "'");
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

	/** Task 59 D2: a shortfall item line leads with the shortage - "<name>: short N (have h, need n)" - not the have/need bookkeeping. */
	@Test
	void shortfallItemLineLeadsWithTheShortage() throws Exception
	{
		Advice advice = craftChainFixture();
		ShortfallItem item = advice.getFocus().getNextSkillPlan().getShortfall().getItems().get(0);
		String parens = "(have " + item.getHave() + ", need " + item.getNeed() + ")";
		Consumer<String> noop = id -> { };
		GoalDetailPanel.Actions actions = new GoalDetailPanel.Actions(noop, () -> { });
		try
		{
			SwingUtilities.invokeAndWait(() ->
			{
				GoalDetailPanel panel = new GoalDetailPanel(actions, icons());
				panel.render(advice);
				assertTrue(containsLabelContaining(panel, "short " + (item.getNeed() - item.getHave()) + "</span> " + parens),
					"item line must read '<name>: short N " + parens + "'");
				assertFalse(containsLabelContaining(panel, ": have " + item.getHave() + ", need "), "the old 'have h, need n, short' order must be gone");
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
	 * Task 59 D3: the engine unions an ingredient's gathering plan into its parent item (spec
	 * ruling 28), so the same plan reached the tree twice - under the parent and again under the
	 * craft-from ingredient. It must render once, at its first occurrence.
	 */
	@Test
	void aGatheringPlanRendersOncePerSkillPlanEvenWhenTheEngineUnionsItIntoTheParent() throws Exception
	{
		Advice advice = craftChainFixture();
		Consumer<String> noop = id -> { };
		GoalDetailPanel.Actions actions = new GoalDetailPanel.Actions(noop, () -> { });
		try
		{
			SwingUtilities.invokeAndWait(() ->
			{
				GoalDetailPanel panel = new GoalDetailPanel(actions, icons());
				panel.render(advice);
				assertEquals(1, countLabelsContaining(panel, "Gather Ranarr weed"), "the Ranarr weed plan must render exactly once");
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
	 * Task 59 D4: an item's shop/drop/spawn sources collapse under a "Sources (n)" toggle, closed by
	 * default; its "craft:" source line is dropped when craft-from rows already show the recipe,
	 * and stays visible outside the toggle when they don't.
	 */
	@Test
	void shortfallSourcesCollapseUnderAToggleAndTheCraftLineShowsOnlyWithoutCraftFromRows() throws Exception
	{
		Advice withCraftFrom = craftChainFixture();
		SkillPlan herblore = withCraftFrom.getFocus().getNextSkillPlan();
		ShortfallItem item = herblore.getShortfall().getItems().get(0);
		ShortfallItem noCraftFrom = new ShortfallItem(item.getItem(), item.getHave(), item.getNeed(), item.getSources(), List.of(),
			item.getWikiUrl(), item.getPlans());
		Shortfall shortfall = new Shortfall(herblore.getShortfall().getMethod(), List.of(noCraftFrom));
		SkillPlan plan = new SkillPlan(herblore.getSkill(), herblore.getFromLevel(), herblore.getToLevel(), herblore.getFromXp(),
			herblore.getToXp(), herblore.isRecommended(), herblore.getRoute(), shortfall, false, herblore.getSource());
		FocusDetail focus = new FocusDetail(withCraftFrom.getFocus().getStatus(), withCraftFrom.getFocus().getNext(), plan.getRoute(),
			shortfall, plan.getFromLevel(), plan.getToLevel(), List.of(plan), plan);
		Advice withoutCraftFrom = new Advice(withCraftFrom.getSnapshot(), withCraftFrom.getStatuses(), withCraftFrom.getDiaryProgress(),
			withCraftFrom.getComputedAt(), withCraftFrom.getRanked(), withCraftFrom.getPicked(), withCraftFrom.getRest(),
			withCraftFrom.getAccountStage(), withCraftFrom.getLater(), withCraftFrom.getWhys(), withCraftFrom.getExplanations(),
			withCraftFrom.getReasons(), withCraftFrom.getOwnedManuallyNames(), withCraftFrom.getPrefs(), focus);

		Consumer<String> noop = id -> { };
		GoalDetailPanel.Actions actions = new GoalDetailPanel.Actions(noop, () -> { });
		try
		{
			SwingUtilities.invokeAndWait(() ->
			{
				GoalDetailPanel panel = new GoalDetailPanel(actions, icons());
				panel.render(withCraftFrom);
				assertFalse(containsLabelContaining(panel, "shop: Myths"), "sources must be collapsed by default");
				assertFalse(containsLabelContaining(panel, "craft: Ranarr weed"), "the craft line is redundant next to craft-from rows");
				JLabel toggle = findLabelStartingWith(panel, "Sources (1)");
				assertNotNull(toggle, "the parent item's shop source must sit under a 'Sources (1)' toggle");
				assertTrue(toggle.getText().endsWith("[+]"), "closed by default: " + toggle.getText());

				toggle.dispatchEvent(new MouseEvent(toggle, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 1, 1, 1, false));

				assertTrue(containsLabelContaining(panel, "shop: Myths"), "expanding the toggle must list the source");
				// task 61: materials.json "where" strings carry U+2013 en dashes the RuneScape font lacks
				assertTrue(containsLabelContaining(panel, "stock 100 - restocks"), "the source's en dash must render as a hyphen");
				assertTrue(findLabelStartingWith(panel, "Sources (1)").getText().endsWith("[-]"), "toggle must read open after the click");
				layoutAtRealPanelWidth(panel);
				assertNothingEndsPastTheRightEdge(panel);
				assertOnlyRenderableGlyphs(panel);

				GoalDetailPanel bare = new GoalDetailPanel(actions, icons());
				bare.render(withoutCraftFrom);
				assertTrue(containsLabelContaining(bare, "craft: Ranarr weed"), "with no craft-from rows the craft line stays visible");
				assertFalse(containsLabelContaining(bare, "shop: Myths"), "the shop source still sits in the closed toggle");
				assertNotNull(findLabelStartingWith(bare, "Sources (1)"), "the craft line is outside the toggle, not counted in it");
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
	 * Task 61 (spec ruling 30): when the primary method's materials can't all be obtained, the
	 * detail view keeps the primary (with a note naming what has no known source) and then offers
	 * the fully-gatherable alternative under its own green header, its items rendered the same
	 * way - no plan repeated, nothing past the panel edge, ASCII-only labels.
	 */
	@Test
	void shortfallOffersTheGatherableAlternativeUnderThePrimary() throws Exception
	{
		Advice advice = unobtainablePrimaryFixture();
		Shortfall shortfall = advice.getFocus().getNextSkillPlan().getShortfall();
		String primaryHeader = "Then still short ~" + grouped(shortfall.getXpShort()) + " xp: Goading potion(3) ×" + grouped(shortfall.getActionsNeeded());
		String altHeader = "Or, everything gatherable: Prayer potion(3) ×" + grouped(shortfall.getAlternative().getActionsNeeded());
		Consumer<String> noop = id -> { };
		GoalDetailPanel.Actions actions = new GoalDetailPanel.Actions(noop, () -> { });
		try
		{
			SwingUtilities.invokeAndWait(() ->
			{
				GoalDetailPanel panel = new GoalDetailPanel(actions, icons());
				panel.render(advice);
				assertTrue(containsLabelContaining(panel, primaryHeader), "primary header must read '" + primaryHeader + "'");
				assertTrue(containsLabelContaining(panel, "Aldarium: no gathering plan"), "the note must name the unobtainable material");
				assertTrue(containsLabelContaining(panel, altHeader), "alternative header must read '" + altHeader + "'");
				assertTrue(containsLabelContaining(panel, "Ranarr potion (unf)"), "the alternative's items must render");
				assertEquals(1, countLabelsContaining(panel, "Gather Ranarr weed"), "the alternative's plan must render exactly once");
				layoutAtRealPanelWidth(panel);
				assertNothingEndsPastTheRightEdge(panel);
				assertOnlyRenderableGlyphs(panel);
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

	/** Task 61: a primary whose materials are all obtainable gets no alternative header and no note. */
	@Test
	void shortfallWithAnObtainablePrimaryOffersNoAlternative() throws Exception
	{
		Advice advice = craftChainFixture();
		assertNull(advice.getFocus().getNextSkillPlan().getShortfall().getAlternative(), "fixture primary must be obtainable");
		Consumer<String> noop = id -> { };
		GoalDetailPanel.Actions actions = new GoalDetailPanel.Actions(noop, () -> { });
		try
		{
			SwingUtilities.invokeAndWait(() ->
			{
				GoalDetailPanel panel = new GoalDetailPanel(actions, icons());
				panel.render(advice);
				assertFalse(containsLabelContaining(panel, "Or, everything gatherable"), "no alternative header without an alternative");
				assertFalse(containsLabelContaining(panel, "no gathering plan"), "no note without an alternative");
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
	 * Task 61: as {@link #craftChainFixture()}, plus a faster Goading potion whose Aldarium is
	 * drop-only with no plan - so for a normal account the fastest method is the primary, is not
	 * obtainable, and Prayer potion (unf craftable from a planned Ranarr weed + shop vial) is the
	 * alternative.
	 */
	private static Advice unobtainablePrimaryFixture()
	{
		int ranarrUnf = 200;
		int ranarr = 201;
		int vial = 202;
		int aldarium = 203;
		KnowledgeBase kb = new KbBuilder()
			.quest(0, "Test Quest").skill(Skill.HERBLORE, 20)
			.method(Skill.HERBLORE, "Goading potion(3)", 1, 100)
			.material(aldarium, 1)
			.method(Skill.HERBLORE, "Prayer potion(3)", 1, 4)
			.material(ranarrUnf, 1)
			.method(Skill.HERBLORE, "Ranarr potion (unf)", 1, 0)
			.material(ranarr, 1)
			.material(vial, 1)
			.output(ranarrUnf, 1)
			.intermediate()
			.material("Aldarium", aldarium)
			.source("drop", "Moons of Peril")
			.material("Ranarr potion (unf)", ranarrUnf)
			.source("craft", "Ranarr weed + Vial of water")
			.material("Ranarr weed", ranarr)
			.source("drop", "Chaos druids")
			.material("Vial of water", vial)
			.source("shop", "Any general store")
			.gatheringPlan("Ranarr weed", ranarr)
			.build();
		Snapshot snapshot = new SnapshotBuilder().bankItem(ranarrUnf, "Ranarr potion (unf)", 2).build();
		AccountData data = new AccountData(new HashMap<>(), null, new HashMap<>(), new HashSet<>(), new ArrayList<>(), "quest:0", new HashSet<>());
		Advice advice = new Engine(new BoostTable()).run(snapshot, kb, data, Instant.now());
		SkillPlan herblore = advice.getFocus().getNextSkillPlan();
		assertNotNull(herblore.getShortfall(), "fixture must leave Herblore short");
		assertEquals("Goading potion(3)", herblore.getShortfall().getMethod().getName(), "the fastest method stays primary");
		assertNotNull(herblore.getShortfall().getAlternative(), "fixture primary must be unobtainable");
		assertEquals(List.of("Aldarium"), herblore.getShortfall().getUnobtainable());
		return advice;
	}

	private static String grouped(long n)
	{
		return String.format(java.util.Locale.ENGLISH, "%,d", n);
	}

	private static int countLabelsContaining(Container container, String substring)
	{
		int n = 0;
		for (Component child : container.getComponents())
		{
			if (child instanceof JLabel && ((JLabel) child).getText().contains(substring))
			{
				n++;
			}
			if (child instanceof Container)
			{
				n += countLabelsContaining((Container) child, substring);
			}
		}
		return n;
	}

	/** Task 57: item images need an {@link net.runelite.client.game.ItemManager} (client-backed, so null here - placeholder path); skill icons come from the real resource-backed manager. */
	private static Icons icons()
	{
		return new Icons(null, new SkillIconManager());
	}

	private static JLabel findLabelNamed(Container container, String name)
	{
		for (Component child : container.getComponents())
		{
			if (child instanceof JLabel && name.equals(child.getName()))
			{
				return (JLabel) child;
			}
			if (child instanceof Container)
			{
				JLabel found = findLabelNamed((Container) child, name);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	private static boolean containsIconOnlyLabel(Container container)
	{
		for (Component child : container.getComponents())
		{
			if (child instanceof JLabel && ((JLabel) child).getIcon() != null && ((JLabel) child).getText().isEmpty())
			{
				return true;
			}
			if (child instanceof Container && containsIconOnlyLabel((Container) child))
			{
				return true;
			}
		}
		return false;
	}

	private static boolean containsComponentOfType(Container container, Class<?> type)
	{
		for (Component child : container.getComponents())
		{
			if (type.isInstance(child) || (child instanceof Container && containsComponentOfType((Container) child, type)))
			{
				return true;
			}
		}
		return false;
	}

	private static boolean containsComponentNamed(Container container, String name)
	{
		for (Component child : container.getComponents())
		{
			if (name.equals(child.getName()))
			{
				return true;
			}
			if (child instanceof Container && containsComponentNamed((Container) child, name))
			{
				return true;
			}
		}
		return false;
	}

	private static GoalDetailPanel findDetailPanel(Container container)
	{
		for (Component child : container.getComponents())
		{
			if (child instanceof GoalDetailPanel)
			{
				return (GoalDetailPanel) child;
			}
			if (child instanceof Container)
			{
				GoalDetailPanel found = findDetailPanel((Container) child);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
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

	private static JButton findButtonWithText(Container container, String text)
	{
		for (Component child : container.getComponents())
		{
			if (child instanceof JButton && text.equals(((JButton) child).getText()))
			{
				return (JButton) child;
			}
			if (child instanceof Container)
			{
				JButton found = findButtonWithText((Container) child, text);
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
