package com.signpost.ui;

import com.signpost.engine.Eta;
import com.signpost.engine.model.Advice;
import com.signpost.engine.model.Gap;
import com.signpost.engine.model.Goal;
import com.signpost.engine.model.GoalCategory;
import com.signpost.engine.model.GoalRef;
import com.signpost.engine.model.GoalStatus;
import com.signpost.engine.model.PrefsView;
import com.signpost.engine.model.RankedGoal;
import com.signpost.engine.model.SkillLevelGap;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.SwingUtilities;
import lombok.Value;
import net.runelite.api.Experience;
import net.runelite.api.Skill;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/**
 * Suggest-mode content, embedded by {@link NextTargetPanel} under the account header: the focus
 * banner, the "Pick one" three cards, the "Next" ranked list (10 + show more), and collapsed
 * Snoozed/Ignored sections. Renders {@link Advice} only - every button here calls back into a
 * plugin action method; the panel computes nothing and mutations go through the plugin.
 *
 * <p>No button label is ever truncated. Buttons are always full text ("Do this", "Not
 * now", "Ignore", "Pin"/"Unpin") and {@link #actionRow} measures the buttons' own preferred widths
 * to decide whether they fit one row or need to wrap to two - never relying on the look-and-feel
 * to ellipsise.
 */
public class SuggestPanel extends JPanel
{
	private static final int PAGE_SIZE = 10;
	static final int WRAP_WIDTH = PluginPanel.PANEL_WIDTH - 30;
	private static final int CARD_BORDER_THICKNESS = 1;
	private static final int CARD_PADDING = 6;
	// The coloured category accent down a card's left edge
	private static final int CARD_ACCENT_WIDTH = 4;
	// A card's text wraps to the card's interior, not the panel width, so the card's
	// preferred width never ends past the panel edge (the overflow walker's rule).
	private static final int CARD_WRAP_WIDTH = WRAP_WIDTH - CARD_ACCENT_WIDTH - 2 * (CARD_BORDER_THICKNESS + CARD_PADDING);
	// the narrowest a button row is ever actually laid out in: a card's interior (the sidebar
	// minus its scrollbar, minus the card's own border and padding on both sides). List rows have
	// no card border, so they always get at least this much room - using the card's tighter figure
	// for both callers is conservative, never wrong.
	private static final int BUTTON_ROW_WIDTH = PluginPanel.PANEL_WIDTH - PluginPanel.SCROLLBAR_WIDTH
		- 2 * (CARD_BORDER_THICKNESS + CARD_PADDING) - CARD_ACCENT_WIDTH;
	// Char-count heuristic for the milestone reason's 3-line cap; tune if a real font's
	// wrapping at WRAP_WIDTH turns out to differ noticeably from this.
	private static final int REASON_MAX_CHARS = 140;
	/** Component name of the "Done:" strip, for tests. */
	public static final String DONE_STRIP_NAME = "done-strip";

	private final Actions actions;
	private final Icons icons;
	// "Done: X" shown from the render that reports a completion until the next action
	private final JLabel doneStrip = new JLabel();
	private final JPanel focusBannerPanel = new JPanel();
	private final JLabel focusLabel = new JLabel();
	private final JPanel pickOnePanel = new JPanel();
	private final JPanel nextListPanel = new JPanel();
	private final JButton showMoreButton = new JButton("Show more");
	private final Header laterHeader;
	private final JPanel laterContent = new JPanel();
	private final Header snoozedHeader;
	private final JPanel snoozedContent = new JPanel();
	private final Header ignoredHeader;
	private final JPanel ignoredContent = new JPanel();
	private final Header ownedHeader;
	private final JPanel ownedContent = new JPanel();
	private final Header achievedHeader;
	private final JPanel achievedContent = new JPanel();
	/** Every goal completed since the panel was last cleared (login), id to name. */
	private final Map<String, String> achieved = new LinkedHashMap<>();

	private Advice currentAdvice;
	private Set<String> lastGoalIds;
	// The previous render's card/row order, so a status line can sit where the acted-on
	// goal used to be; pendingStatus is set by a click and becomes shownStatus on the next render,
	// which is the only render that shows it.
	private List<String> lastPickedIds = List.of();
	private List<String> lastRestIds = List.of();
	private String pendingStatusGoal;
	private String pendingStatusText;
	private String shownStatusText;
	private int statusPickIndex = -1;
	private int statusRestIndex = -1;
	private int nextShown = PAGE_SIZE;
	private boolean laterExpanded;
	private boolean snoozedExpanded;
	private boolean ignoredExpanded;
	private boolean ownedExpanded;
	private boolean achievedExpanded;
	// Which goals' "Why?" explanation is expanded, keyed by goal id - never cleared on
	// re-render (same rule as laterExpanded/snoozedExpanded/ignoredExpanded above), so a re-render
	// with the same goal set keeps whatever the user had open.
	private final Set<String> expandedWhy = new HashSet<>();

	public SuggestPanel(Actions actions, Icons icons)
	{
		this.actions = actions;
		this.icons = icons;

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setAlignmentX(Component.LEFT_ALIGNMENT);

		doneStrip.setName(DONE_STRIP_NAME);
		doneStrip.setFont(FontManager.getRunescapeBoldFont());
		doneStrip.setForeground(ColorScheme.PROGRESS_COMPLETE_COLOR);
		doneStrip.setAlignmentX(Component.LEFT_ALIGNMENT);
		doneStrip.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, CARD_ACCENT_WIDTH, 0, 0, ColorScheme.PROGRESS_COMPLETE_COLOR),
			BorderFactory.createEmptyBorder(4, CARD_PADDING, 4, 0)));
		doneStrip.setVisible(false);
		add(doneStrip);

		focusBannerPanel.setLayout(new BoxLayout(focusBannerPanel, BoxLayout.X_AXIS));
		focusBannerPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		focusBannerPanel.setVisible(false);
		focusBannerPanel.add(focusLabel);
		focusBannerPanel.add(Box.createHorizontalStrut(6));
		focusBannerPanel.add(action("Clear focus", actions.getClearFocus()));
		add(focusBannerPanel);

		add(sectionLabel("Pick one"));
		pickOnePanel.setLayout(new BoxLayout(pickOnePanel, BoxLayout.Y_AXIS));
		pickOnePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		add(pickOnePanel);

		add(sectionLabel("Next"));
		nextListPanel.setLayout(new BoxLayout(nextListPanel, BoxLayout.Y_AXIS));
		nextListPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		add(nextListPanel);
		showMoreButton.setAlignmentX(Component.LEFT_ALIGNMENT);
		showMoreButton.addActionListener(e ->
		{
			nextShown += PAGE_SIZE;
			rebuildNextSection();
		});
		add(showMoreButton);

		laterHeader = new Header("Later", () ->
		{
			laterExpanded = !laterExpanded;
			rebuild();
		});
		add(laterHeader);
		laterContent.setLayout(new BoxLayout(laterContent, BoxLayout.Y_AXIS));
		laterContent.setAlignmentX(Component.LEFT_ALIGNMENT);
		laterContent.setVisible(false);
		add(laterContent);

		snoozedHeader = new Header("Snoozed", () ->
		{
			snoozedExpanded = !snoozedExpanded;
			rebuild();
		});
		add(snoozedHeader);
		snoozedContent.setLayout(new BoxLayout(snoozedContent, BoxLayout.Y_AXIS));
		snoozedContent.setAlignmentX(Component.LEFT_ALIGNMENT);
		snoozedContent.setVisible(false);
		add(snoozedContent);

		ignoredHeader = new Header("Ignored", () ->
		{
			ignoredExpanded = !ignoredExpanded;
			rebuild();
		});
		add(ignoredHeader);
		ignoredContent.setLayout(new BoxLayout(ignoredContent, BoxLayout.Y_AXIS));
		ignoredContent.setAlignmentX(Component.LEFT_ALIGNMENT);
		ignoredContent.setVisible(false);
		add(ignoredContent);

		ownedHeader = new Header("Owned (manual)", () ->
		{
			ownedExpanded = !ownedExpanded;
			rebuild();
		});
		add(ownedHeader);
		ownedContent.setLayout(new BoxLayout(ownedContent, BoxLayout.Y_AXIS));
		ownedContent.setAlignmentX(Component.LEFT_ALIGNMENT);
		ownedContent.setVisible(false);
		add(ownedContent);

		achievedHeader = new Header("Achieved this session", () ->
		{
			achievedExpanded = !achievedExpanded;
			rebuild();
		});
		add(achievedHeader);
		achievedContent.setLayout(new BoxLayout(achievedContent, BoxLayout.Y_AXIS));
		achievedContent.setAlignmentX(Component.LEFT_ALIGNMENT);
		achievedContent.setVisible(false);
		add(achievedContent);
	}

	/**
	 * Renders {@code advice}. Must be called on the EDT. Resets the "show more" paging back to the
	 * first page only when the set of ranked goals changed since the previous render -
	 * the same rule {@link GoalSearchField} uses to keep its query - so a re-run that merely
	 * refreshed the same goals (bank close, level-up) does not collapse the list.
	 */
	public void render(Advice advice)
	{
		if (!SwingUtilities.isEventDispatchThread())
		{
			throw new IllegalStateException("SuggestPanel.render must run on the EDT");
		}

		Set<String> goalIds = GoalSearchField.goalIds(advice);
		if (!goalIds.equals(lastGoalIds))
		{
			nextShown = PAGE_SIZE;
		}
		lastGoalIds = goalIds;
		this.currentAdvice = advice;

		shownStatusText = pendingStatusText;
		statusPickIndex = pendingStatusGoal == null ? -1 : lastPickedIds.indexOf(pendingStatusGoal);
		statusRestIndex = pendingStatusGoal == null ? -1 : lastRestIds.indexOf(pendingStatusGoal);
		if (shownStatusText != null && statusPickIndex < 0 && statusRestIndex < 0)
		{
			statusPickIndex = 0;
		}
		pendingStatusGoal = null;
		pendingStatusText = null;
		lastPickedIds = ids(advice.getPicked());
		lastRestIds = ids(advice.getRest());

		if (!advice.getCompletedSinceLast().isEmpty())
		{
			for (Goal goal : advice.getCompletedSinceLast())
			{
				achieved.put(goal.getId(), goal.getName());
			}
			doneStrip.setText(wrap("Done: " + advice.getCompletedSinceLast().stream().map(Goal::getName).collect(Collectors.joining(", ")), WRAP_WIDTH - CARD_ACCENT_WIDTH - CARD_PADDING));
			doneStrip.setVisible(true);
		}
		rebuild();
	}

	private static List<String> ids(List<RankedGoal> goals)
	{
		return goals.stream().map(r -> r.getStatus().getGoal().getId()).collect(Collectors.toList());
	}

	/** Wraps {@code action} so the render it triggers shows {@code text} where the goal's card/row was. */
	private Runnable withStatus(String goalId, String text, Consumer<String> action)
	{
		return () ->
		{
			pendingStatusGoal = goalId;
			pendingStatusText = text;
			action.accept(goalId);
		};
	}

	/**
	 * Why "Pick one" is empty, the first that applies - bank never seen; everything
	 * left is Later (naming the closest); nothing ranked at all - plus a link that expands the
	 * Later section (or Snoozed when there is nothing Later but something snoozed).
	 */
	private JPanel emptyState(Advice advice)
	{
		JPanel panel = GoalDetailPanel.column();
		String text;
		if (!advice.getSnapshot().isBankKnown())
		{
			text = "Bank not seen yet: open your bank once";
		}
		else if (!advice.getLater().isEmpty())
		{
			text = "Everything left is a stage above yours; the closest is " + advice.getLater().get(0).getStatus().getGoal().getName() + " (Later)";
		}
		else
		{
			text = "Nothing to suggest: all known goals done or hidden";
		}
		JLabel label = new JLabel(wrap(text));
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.add(label);

		int laterCount = advice.getLater().size();
		int snoozedCount = advice.getPrefs().getSnoozedActive().size();
		if (laterCount > 0)
		{
			panel.add(link("Show Later (" + laterCount + ")", () -> laterExpanded = true));
		}
		else if (snoozedCount > 0)
		{
			panel.add(link("Show Snoozed (" + snoozedCount + ")", () -> snoozedExpanded = true));
		}
		return panel;
	}

	/** A small underlined-looking clickable label that runs {@code onClick} then rebuilds. */
	private JLabel link(String text, Runnable onClick)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(ColorScheme.BRAND_ORANGE);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		label.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				onClick.run();
				rebuild();
			}
		});
		return label;
	}

	private JLabel statusLine()
	{
		JLabel label = new JLabel(wrap(shownStatusText));
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		label.setBorder(BorderFactory.createEmptyBorder(2, 0, 6, 0));
		return label;
	}

	/** Removes every card and row (logged out). Must be called on the EDT. */
	public void clear()
	{
		currentAdvice = null;
		lastGoalIds = null;
		lastPickedIds = List.of();
		lastRestIds = List.of();
		pendingStatusGoal = null;
		pendingStatusText = null;
		shownStatusText = null;
		achieved.clear();
		doneStrip.setVisible(false);
		nextShown = PAGE_SIZE;
		focusBannerPanel.setVisible(false);
		for (JPanel content : List.of(pickOnePanel, nextListPanel, laterContent, snoozedContent, ignoredContent, ownedContent, achievedContent))
		{
			content.removeAll();
		}
		showMoreButton.setVisible(false);
		revalidate();
		repaint();
	}

	/** Test seam: how many "Next" rows are currently shown. */
	public int shownNextCount()
	{
		return nextListPanel.getComponentCount();
	}

	/** Test seam: the "Show more" button's action. */
	public void showMore()
	{
		showMoreButton.doClick();
	}

	private void rebuild()
	{
		Advice advice = currentAdvice;
		if (advice == null)
		{
			return;
		}

		PrefsView prefs = advice.getPrefs();

		if (prefs.getFocusGoalId() != null)
		{
			focusLabel.setText("Focus: " + nameFor(prefs.getFocusGoalId(), advice));
			focusBannerPanel.setVisible(true);
		}
		else
		{
			focusBannerPanel.setVisible(false);
		}

		pickOnePanel.removeAll();
		List<RankedGoal> picked = advice.getPicked();
		for (int i = 0; i <= picked.size(); i++)
		{
			if (shownStatusText != null && statusPickIndex == Math.min(i, picked.size()))
			{
				pickOnePanel.add(statusLine());
			}
			if (i < picked.size())
			{
				pickOnePanel.add(buildCard(picked.get(i), advice.getWhys(), advice.getReasons(), advice.getExplanations(), advice.getXpPerHour()));
				pickOnePanel.add(Box.createVerticalStrut(6));
			}
		}
		if (picked.isEmpty())
		{
			pickOnePanel.add(emptyState(advice));
		}

		rebuildNextSection();

		laterHeader.update(advice.getLater().size(), laterExpanded);
		laterContent.removeAll();
		laterContent.setVisible(laterExpanded);
		if (laterExpanded)
		{
			for (RankedGoal r : advice.getLater())
			{
				laterContent.add(buildRow(r, advice.getWhys(), advice.getExplanations()));
			}
		}

		Set<String> ignoredIds = new LinkedHashSet<>(prefs.getHidden());
		ignoredIds.removeAll(prefs.getSnoozedActive());

		snoozedHeader.update(prefs.getSnoozedActive().size(), snoozedExpanded);
		snoozedContent.removeAll();
		snoozedContent.setVisible(snoozedExpanded);
		if (snoozedExpanded)
		{
			for (String goalId : prefs.getSnoozedActive())
			{
				snoozedContent.add(bringBackRow(goalId, advice, "Bring back", actions.getUnsnooze()));
			}
		}

		ignoredHeader.update(ignoredIds.size(), ignoredExpanded);
		ignoredContent.removeAll();
		ignoredContent.setVisible(ignoredExpanded);
		if (ignoredExpanded)
		{
			for (String goalId : ignoredIds)
			{
				ignoredContent.add(bringBackRow(goalId, advice, "Restore", actions.getUnignore()));
			}
		}

		Map<String, String> ownedManuallyNames = advice.getOwnedManuallyNames();
		ownedHeader.update(ownedManuallyNames.size(), ownedExpanded);
		ownedContent.removeAll();
		ownedContent.setVisible(ownedExpanded);
		if (ownedExpanded)
		{
			for (Map.Entry<String, String> entry : ownedManuallyNames.entrySet())
			{
				ownedContent.add(ownedRow(entry.getKey(), entry.getValue()));
			}
		}

		achievedHeader.update(achieved.size(), achievedExpanded);
		achievedContent.removeAll();
		achievedContent.setVisible(achievedExpanded);
		if (achievedExpanded)
		{
			for (String name : achieved.values())
			{
				JLabel label = new JLabel(wrap(name));
				label.setFont(FontManager.getRunescapeSmallFont());
				label.setAlignmentX(Component.LEFT_ALIGNMENT);
				label.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
				achievedContent.add(label);
			}
		}

		revalidate();
		repaint();
	}

	private void rebuildNextSection()
	{
		nextListPanel.removeAll();
		Advice advice = currentAdvice;
		List<RankedGoal> rest = advice.getRest();
		int shown = Math.min(nextShown, rest.size());
		for (int i = 0; i <= shown; i++)
		{
			if (shownStatusText != null && statusRestIndex >= 0 && Math.min(statusRestIndex, shown) == i)
			{
				nextListPanel.add(statusLine());
			}
			if (i < shown)
			{
				nextListPanel.add(buildRow(rest.get(i), advice.getWhys(), advice.getExplanations()));
			}
		}
		showMoreButton.setVisible(shown < rest.size());
		revalidate();
		repaint();
	}

	private JPanel buildCard(RankedGoal r, Map<String, String> whys, Map<String, String> reasons,
		Map<String, List<String>> explanations, Map<Skill, Long> xpPerHour)
	{
		Goal goal = r.getStatus().getGoal();

		JPanel card = new JPanel();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setAlignmentX(Component.LEFT_ALIGNMENT);
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, CARD_ACCENT_WIDTH, 0, 0, Icons.categoryColor(goal.getCategory())),
			BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR, CARD_BORDER_THICKNESS),
				BorderFactory.createEmptyBorder(CARD_PADDING, CARD_PADDING, CARD_PADDING, CARD_PADDING))));

		JLabel nameLabel = new JLabel(goal.getName() + (r.isPinned() ? " (pinned)" : ""));
		nameLabel.setFont(FontManager.getRunescapeBoldFont());
		nameLabel.setForeground(ColorScheme.TEXT_COLOR);
		nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		Skill skill = targetSkill(r.getStatus());
		if (skill != null)
		{
			JPanel nameRow = GoalDetailPanel.borderRow();
			nameRow.add(icons.skill(skill), BorderLayout.WEST);
			nameRow.add(nameLabel, BorderLayout.CENTER);
			card.add(nameRow);
		}
		else
		{
			card.add(nameLabel);
		}
		card.add(Box.createVerticalStrut(4));

		JLabel categoryLabel = new JLabel(categoryLabel(goal.getCategory()) + " — stage " + goal.getStage());
		categoryLabel.setFont(FontManager.getRunescapeSmallFont());
		categoryLabel.setForeground(Icons.categoryColor(goal.getCategory()));
		categoryLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		card.add(categoryLabel);
		card.add(Box.createVerticalStrut(4));

		if (goal.getCategory() == GoalCategory.SKILL_TARGET)
		{
			card.add(skillTargetDetail(r.getStatus(), xpPerHour));
		}

		JLabel whyLabel = new JLabel(wrap(whys.getOrDefault(goal.getId(), ""), CARD_WRAP_WIDTH));
		whyLabel.setFont(FontManager.getRunescapeFont());
		whyLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		card.add(whyLabel);

		String reason = reasons.get(goal.getId());
		if (reason != null && !reason.isEmpty())
		{
			card.add(Box.createVerticalStrut(4));
			JLabel reasonLabel = new JLabel(wrap(truncateReason(reason), CARD_WRAP_WIDTH));
			reasonLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.ITALIC));
			reasonLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
			card.add(reasonLabel);
		}

		card.add(Box.createVerticalStrut(4));
		card.add(buildWhyToggle(goal.getId(), explanations, CARD_WRAP_WIDTH));

		card.add(Box.createVerticalStrut(6));
		JButton doThis = action("Do this", () -> actions.getDoThis().accept(goal.getId()));
		JButton notNow = action("Not now", notNow(goal.getId()));
		JButton ignore = action("Ignore", ignore(goal.getId()));
		card.add(ownableLabel(goal.getCategory()) != null
			? actionRow(doThis, notNow, ignore, action(ownableLabel(goal.getCategory()), markOwned(goal.getId())))
			: actionRow(doThis, notNow, ignore));

		return card;
	}

	private JPanel buildRow(RankedGoal r, Map<String, String> whys, Map<String, List<String>> explanations)
	{
		Goal goal = r.getStatus().getGoal();
		String why = whys.getOrDefault(goal.getId(), "");

		JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));

		JLabel label = new JLabel(wrap(goal.getName() + (r.isPinned() ? " (pinned)" : "") + " — " + why));
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.add(label);
		row.add(Box.createVerticalStrut(4));
		row.add(buildWhyToggle(goal.getId(), explanations, WRAP_WIDTH));
		row.add(Box.createVerticalStrut(4));

		JButton doThis = action("Do this", () -> actions.getDoThis().accept(goal.getId()));
		JButton notNow = action("Not now", notNow(goal.getId()));
		JButton ignore = action("Ignore", ignore(goal.getId()));
		JButton pin = r.isPinned()
			? action("Unpin", () -> actions.getUnpin().accept(goal.getId()))
			: action("Pin", withStatus(goal.getId(), "Pinned: stays in Pick one until you unpin it", actions.getPin()));
		String ownLabel = ownableLabel(goal.getCategory());
		row.add(ownLabel != null
			? actionRow(doThis, notNow, ignore, pin, action(ownLabel, markOwned(goal.getId())))
			: actionRow(doThis, notNow, ignore, pin));

		return row;
	}

	private Runnable notNow(String goalId)
	{
		return withStatus(goalId, "Not now: hidden for " + actions.getSnoozeDays().getAsInt() + " days or until something changes", actions.getNotNow());
	}

	private Runnable ignore(String goalId)
	{
		return withStatus(goalId, "Ignored: hidden until you restore it from Ignored", actions.getIgnore());
	}

	private Runnable markOwned(String goalId)
	{
		return withStatus(goalId, "Owned: marked done by hand, undo under Owned (manual)", actions.getMarkOwned());
	}

	/** A {@link GoalCategory#SKILL_TARGET}'s skill (its one {@link SkillLevelGap}), for the card's icon; {@code null} for any other goal. */
	private static Skill targetSkill(GoalStatus status)
	{
		SkillLevelGap gap = skillGap(status);
		return gap == null ? null : gap.getSkill();
	}

	/** A {@link GoalCategory#SKILL_TARGET}'s one {@link SkillLevelGap}; {@code null} for any other goal. */
	private static SkillLevelGap skillGap(GoalStatus status)
	{
		if (status.getGoal().getCategory() != GoalCategory.SKILL_TARGET)
		{
			return null;
		}
		for (Gap gap : status.getGaps())
		{
			if (gap instanceof SkillLevelGap)
			{
				return (SkillLevelGap) gap;
			}
		}
		return null;
	}

	private static final int MAX_PARENTS = 3;

	/** A {@link GoalCategory#SKILL_TARGET} card's parent goals line (capped at {@value #MAX_PARENTS}, matching {@link com.signpost.engine.WhyBuilder#explain}), the level progress bar with its eta at the observed rate and, when {@link GoalStatus#isBankCovered()}, a small "materials in bank" badge. */
	private static JPanel skillTargetDetail(GoalStatus status, Map<Skill, Long> xpPerHour)
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);

		List<GoalRef> parents = status.getParents();
		if (!parents.isEmpty())
		{
			String names = parents.stream().limit(MAX_PARENTS).map(GoalRef::getName).collect(Collectors.joining(", "));
			int more = parents.size() - MAX_PARENTS;
			JLabel parentsLabel = new JLabel(wrap("for " + names + (more > 0 ? ", +" + more + " more" : ""), CARD_WRAP_WIDTH));
			parentsLabel.setFont(FontManager.getRunescapeSmallFont());
			parentsLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			parentsLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
			panel.add(parentsLabel);
		}

		SkillLevelGap gap = skillGap(status);
		if (gap != null)
		{
			JLabel levels = new JLabel(gap.getHave() + "/" + gap.getNeed());
			levels.setFont(FontManager.getRunescapeSmallFont());
			levels.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			levels.setAlignmentX(Component.LEFT_ALIGNMENT);
			panel.add(levels);
			// Fraction of the target level's xp already earned
			long targetXp = Experience.getXpForLevel(gap.getNeed());
			ProgressBar bar = new ProgressBar((targetXp - gap.getXpDelta()) / (double) targetXp,
				status.isBankCovered() ? ColorScheme.PROGRESS_COMPLETE_COLOR : ColorScheme.BRAND_ORANGE);
			bar.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
			panel.add(bar);
			// Time left at the observed rate, over the xp the bank route doesn't cover
			String eta = Eta.text(Eta.remainingXp(status.getBankRoute(), gap.getXpDelta()), xpPerHour.get(gap.getSkill()));
			if (eta != null)
			{
				JLabel etaLabel = new JLabel(eta);
				etaLabel.setFont(FontManager.getRunescapeSmallFont());
				etaLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
				etaLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
				panel.add(etaLabel);
			}
		}

		if (status.isBankCovered())
		{
			JLabel badge = new JLabel("materials in bank");
			badge.setFont(FontManager.getRunescapeSmallFont());
			badge.setForeground(ColorScheme.PROGRESS_COMPLETE_COLOR);
			badge.setAlignmentX(Component.LEFT_ALIGNMENT);
			panel.add(badge);
		}

		panel.add(Box.createVerticalStrut(4));
		return panel;
	}

	/**
	 * A collapsed-by-default "Why?" toggle; expanding it lists {@code explanations}'
	 * lines for {@code goalId} in the small grey font, wrapped to {@code width}. Expansion state
	 * persists per goal id in {@link #expandedWhy} across re-renders (same rule as the collapsible
	 * section headers).
	 */
	private JPanel buildWhyToggle(String goalId, Map<String, List<String>> explanations, int width)
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);

		boolean expanded = expandedWhy.contains(goalId);

		JLabel toggle = new JLabel("Why? " + (expanded ? "[-]" : "[+]"));
		toggle.setFont(FontManager.getRunescapeSmallFont());
		toggle.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		toggle.setAlignmentX(Component.LEFT_ALIGNMENT);
		toggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		toggle.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (expandedWhy.contains(goalId))
				{
					expandedWhy.remove(goalId);
				}
				else
				{
					expandedWhy.add(goalId);
				}
				rebuild();
			}
		});
		panel.add(toggle);

		if (expanded)
		{
			panel.add(Box.createVerticalStrut(2));
			for (String line : explanations.getOrDefault(goalId, List.of()))
			{
				JLabel lineLabel = new JLabel(wrap(line, width));
				lineLabel.setFont(FontManager.getRunescapeSmallFont());
				lineLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
				lineLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
				panel.add(lineLabel);
			}
		}

		return panel;
	}

	private JPanel bringBackRow(String goalId, Advice advice, String buttonLabel, Consumer<String> onClick)
	{
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		JLabel label = new JLabel(nameFor(goalId, advice));
		label.setFont(FontManager.getRunescapeSmallFont());
		row.add(label, BorderLayout.CENTER);
		row.add(action(buttonLabel, () -> onClick.accept(goalId)), BorderLayout.EAST);
		return row;
	}

	/** An "Owned (manual)" section row - unlike {@link #bringBackRow}, the goal has no status in {@code advice} (a manually-owned goal is never emitted), so its name comes straight from {@link Advice#getOwnedManuallyNames()}. */
	private JPanel ownedRow(String goalId, String name)
	{
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		JLabel label = new JLabel(name);
		label.setFont(FontManager.getRunescapeSmallFont());
		row.add(label, BorderLayout.CENTER);
		row.add(action("Unmark", () -> actions.getUnmarkOwned().accept(goalId)), BorderLayout.EAST);
		return row;
	}

	private static String nameFor(String goalId, Advice advice)
	{
		for (GoalStatus status : advice.getStatuses())
		{
			if (status.getGoal().getId().equals(goalId))
			{
				return status.getGoal().getName();
			}
		}
		return goalId;
	}

	/** The "Own it"/"Done it" button label for a milestone/slayer-target/boss card or row, {@code null} for a quest/diary/skill target (never ownable by hand). */
	private static String ownableLabel(GoalCategory category)
	{
		switch (category)
		{
			case BOSS:
				return "Done it";
			case MILESTONE:
			case GEAR_UPGRADE:
			case SLAYER_TARGET:
				return "Own it";
			default:
				return null;
		}
	}

	static String categoryLabel(GoalCategory category)
	{
		switch (category)
		{
			case QUEST:
				return "Quest";
			case DIARY:
				return "Diary";
			case MILESTONE:
				return "Milestone";
			case GEAR_UPGRADE:
				return "Gear upgrade";
			case SLAYER_TARGET:
				return "Slayer";
			case BOSS:
				return "Boss";
			case SKILL_TARGET:
				return "Skill";
			default:
				return category.name();
		}
	}

	/**
	 * Truncates a milestone {@code reason} to roughly 3 lines at {@link #WRAP_WIDTH}, preferring a
	 * clean sentence boundary; only falls back to a hard word-boundary cut with a trailing "…"
	 * (which never appears anywhere but this reason text - buttons are always full labels).
	 */
	static String truncateReason(String reason)
	{
		if (reason.length() <= REASON_MAX_CHARS)
		{
			return reason;
		}
		String truncated = reason.substring(0, REASON_MAX_CHARS);
		int sentenceEnd = lastSentenceBoundary(truncated);
		if (sentenceEnd > 0)
		{
			return truncated.substring(0, sentenceEnd).trim();
		}
		int lastSpace = truncated.lastIndexOf(' ');
		String cut = lastSpace > 0 ? truncated.substring(0, lastSpace) : truncated;
		return cut.trim() + "…";
	}

	private static int lastSentenceBoundary(String s)
	{
		int idx = Math.max(s.lastIndexOf('.'), Math.max(s.lastIndexOf('!'), s.lastIndexOf('?')));
		return idx < 0 ? -1 : idx + 1;
	}

	/** A section header: a small caps-style label with a thin separator underneath, not collapsible. */
	static JPanel sectionLabel(String text)
	{
		JPanel panel = GoalDetailPanel.column();
		panel.setBorder(BorderFactory.createEmptyBorder(8, 0, 4, 0));

		JLabel label = new JLabel(text.toUpperCase());
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.add(label);
		panel.add(Box.createVerticalStrut(2));
		panel.add(separator());
		return panel;
	}

	private static JSeparator separator()
	{
		JSeparator sep = new JSeparator();
		sep.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
		sep.setAlignmentX(Component.LEFT_ALIGNMENT);
		return sep;
	}

	/** A collapsible section header: small caps-style label plus count and expand/collapse arrow, thin separator below, the whole header clickable to toggle. */
	private static final class Header extends JPanel
	{
		private final String title;
		private final JLabel label = new JLabel();

		Header(String title, Runnable onToggle)
		{
			this.title = title;
			setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
			setAlignmentX(Component.LEFT_ALIGNMENT);
			setBorder(BorderFactory.createEmptyBorder(8, 0, 4, 0));
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

			label.setFont(FontManager.getRunescapeSmallFont());
			label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			label.setAlignmentX(Component.LEFT_ALIGNMENT);
			label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			add(label);
			add(Box.createVerticalStrut(2));
			JSeparator sep = separator();
			add(sep);

			// AWT dispatches a click to the deepest component under the cursor and
			// does not bubble it to ancestors, so the label/separator - which visually cover the
			// whole clickable header - each need their own copy of the same listener, not just
			// the outer panel.
			MouseAdapter toggle = new MouseAdapter()
			{
				@Override
				public void mouseClicked(MouseEvent e)
				{
					onToggle.run();
				}
			};
			addMouseListener(toggle);
			label.addMouseListener(toggle);
			sep.addMouseListener(toggle);
		}

		void update(int count, boolean expanded)
		{
			label.setText(title.toUpperCase() + " (" + count + ")" + (expanded ? " [-]" : " [+]"));
		}
	}

	/**
	 * Lays out {@code buttons} on a single {@link GridLayout}{@code (1, n, 4, 0)} row when they
	 * fit, otherwise splits into two rows and recurses on each half - so a pair that still
	 * doesn't fit degrades again instead of being assumed safe.
	 *
	 * <p>{@link GridLayout} gives every cell the SAME width (the row's width divided
	 * evenly), not each button its own preferred width - so "fits" has to compare the row's
	 * available width against {@code n * the widest button}, never the sum of the buttons' widths.
	 * A sum-based check let a wide button ("Not now") get paired with a narrower one ("Ignore")
	 * and be squeezed below its own preferred width even though the pair's total fit.
	 */
	private static JPanel actionRow(JButton... buttons)
	{
		if (buttons.length == 1 || fitsOneRow(buttons))
		{
			return gridRow(buttons);
		}

		int splitAt = buttons.length == 3 ? 1 : buttons.length / 2;
		JPanel stack = new JPanel();
		stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
		stack.setAlignmentX(Component.LEFT_ALIGNMENT);
		stack.add(actionRow(Arrays.copyOfRange(buttons, 0, splitAt)));
		stack.add(Box.createVerticalStrut(4));
		stack.add(actionRow(Arrays.copyOfRange(buttons, splitAt, buttons.length)));
		return stack;
	}

	private static boolean fitsOneRow(JButton[] buttons)
	{
		int maxWidth = 0;
		for (JButton b : buttons)
		{
			maxWidth = Math.max(maxWidth, b.getPreferredSize().width);
		}
		return buttons.length * maxWidth + (buttons.length - 1) * 4 <= BUTTON_ROW_WIDTH;
	}

	private static JPanel gridRow(JButton[] buttons)
	{
		JPanel row = new JPanel(new GridLayout(1, buttons.length, 4, 0));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		for (JButton b : buttons)
		{
			row.add(b);
		}
		return row;
	}

	/** This panel's own buttons all count as a user action, which hides the Done strip. */
	private JButton action(String text, Runnable onClick)
	{
		return button(text, () ->
		{
			doneStrip.setVisible(false);
			onClick.run();
		});
	}

	/** Every button's factory: full margin so text always has room, no focus paint that eats into it. */
	static JButton button(String text, Runnable onClick)
	{
		JButton b = new JButton(text);
		b.setMargin(new Insets(2, 4, 2, 4));
		b.setFocusPainted(false);
		b.addActionListener(e -> onClick.run());
		return b;
	}

	/** Wraps {@code text} to the panel width via HTML, escaping the few characters that would otherwise break the markup. */
	static String wrap(String text)
	{
		return wrap(text, WRAP_WIDTH);
	}

	/** As {@link #wrap(String)}, to {@code width} pixels; a nested detail row has less room than the panel. */
	static String wrap(String text, int width)
	{
		return wrapHtml(escape(text), width);
	}

	/** As {@link #wrap(String)}, for markup that is already escaped, such as colour spans. */
	static String wrapHtml(String rawHtml)
	{
		return wrapHtml(rawHtml, WRAP_WIDTH);
	}

	/**
	 * As {@link #wrapHtml(String)}, to {@code width} pixels. The CSS unit is {@code pt}, not
	 * {@code px}: Swing's stylesheet ({@code javax.swing.text.html.CSS}) scales {@code px} by 1.3
	 * while its {@code pt} is the same pixel every other Swing size uses, so {@code width:195px}
	 * lays the text out 253 pixels wide, painting past the panel edge.
	 */
	static String wrapHtml(String rawHtml, int width)
	{
		return "<html><div style='width:" + width + "pt'>" + rawHtml + "</div></html>";
	}

	/** Already-escaped markup as a single unwrapped HTML label text (natural width). */
	static String html(String rawHtml)
	{
		return "<html>" + rawHtml + "</html>";
	}

	static String escape(String text)
	{
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	/** The plugin action methods a card/row button calls into; every mutation goes through these. */
	@Value
	public static class Actions
	{
		Consumer<String> doThis;
		Consumer<String> notNow;
		Consumer<String> ignore;
		Consumer<String> pin;
		Consumer<String> unpin;
		Consumer<String> unsnooze;
		Consumer<String> unignore;
		Runnable clearFocus;
		Consumer<String> markOwned;
		Consumer<String> unmarkOwned;
		/** The configured snooze length, for the "Not now" status line. */
		IntSupplier snoozeDays;
	}
}
