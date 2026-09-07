package dev.reece.nta.ui;

import dev.reece.nta.engine.model.Advice;
import dev.reece.nta.engine.model.Goal;
import dev.reece.nta.engine.model.GoalCategory;
import dev.reece.nta.engine.model.GoalRef;
import dev.reece.nta.engine.model.GoalStatus;
import dev.reece.nta.engine.model.PrefsView;
import dev.reece.nta.engine.model.RankedGoal;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
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
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/**
 * Suggest-mode content, embedded by {@link NextTargetPanel} under the account header: the focus
 * banner, the "Pick one" three cards, the "Next" ranked list (10 + show more), and collapsed
 * Snoozed/Ignored sections. Renders {@link Advice} only - every button here calls back into a
 * plugin action method (S4 ruling 19: panel computes nothing, mutations go through the plugin).
 *
 * <p>Task 49: no button label is ever truncated. Buttons are always full text ("Do this", "Not
 * now", "Ignore", "Pin"/"Unpin") and {@link #actionRow} measures the buttons' own preferred widths
 * to decide whether they fit one row or need to wrap to two - never relying on the look-and-feel
 * to ellipsise.
 */
public class SuggestPanel extends JPanel
{
	private static final int PAGE_SIZE = 10;
	private static final int WRAP_WIDTH = PluginPanel.PANEL_WIDTH - 30;
	private static final int CARD_BORDER_THICKNESS = 1;
	private static final int CARD_PADDING = 6;
	// the narrowest a button row is ever actually laid out in: a card's interior (the sidebar
	// minus its scrollbar, minus the card's own border and padding on both sides). List rows have
	// no card border, so they always get at least this much room - using the card's tighter figure
	// for both callers is conservative, never wrong.
	private static final int BUTTON_ROW_WIDTH = PluginPanel.PANEL_WIDTH - PluginPanel.SCROLLBAR_WIDTH
		- 2 * (CARD_BORDER_THICKNESS + CARD_PADDING);
	// ponytail: char-count heuristic for the milestone reason's 3-line cap; tune if a real font's
	// wrapping at WRAP_WIDTH turns out to differ noticeably from this.
	private static final int REASON_MAX_CHARS = 140;

	private final Actions actions;
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

	private Advice currentAdvice;
	private Set<String> lastGoalIds;
	private int nextShown = PAGE_SIZE;
	private boolean laterExpanded;
	private boolean snoozedExpanded;
	private boolean ignoredExpanded;
	// task 52b: which goals' "Why?" explanation is expanded, keyed by goal id - never cleared on
	// re-render (same rule as laterExpanded/snoozedExpanded/ignoredExpanded above), so a re-render
	// with the same goal set keeps whatever the user had open.
	private final Set<String> expandedWhy = new HashSet<>();

	public SuggestPanel(Actions actions)
	{
		this.actions = actions;

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setAlignmentX(Component.LEFT_ALIGNMENT);

		focusBannerPanel.setLayout(new BoxLayout(focusBannerPanel, BoxLayout.X_AXIS));
		focusBannerPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		focusBannerPanel.setVisible(false);
		focusBannerPanel.add(focusLabel);
		focusBannerPanel.add(Box.createHorizontalStrut(6));
		focusBannerPanel.add(button("Clear focus", actions.getClearFocus()));
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
	}

	/**
	 * Renders {@code advice}. Must be called on the EDT. Resets the "show more" paging back to the
	 * first page only when the set of goals (ranked union later) changed since the previous render -
	 * the same rule {@link GoalSearchField} uses to keep its query - so a re-run that merely
	 * refreshed the same goals (bank close, level-up) does not collapse the list (final-review I4).
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
		rebuild();
	}

	/** Removes every card and row (logged out). Must be called on the EDT. */
	public void clear()
	{
		currentAdvice = null;
		lastGoalIds = null;
		nextShown = PAGE_SIZE;
		focusBannerPanel.setVisible(false);
		for (JPanel content : List.of(pickOnePanel, nextListPanel, laterContent, snoozedContent, ignoredContent))
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
		for (RankedGoal r : advice.getPicked())
		{
			pickOnePanel.add(buildCard(r, advice.getWhys(), advice.getReasons(), advice.getExplanations()));
			pickOnePanel.add(Box.createVerticalStrut(6));
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

		revalidate();
		repaint();
	}

	private void rebuildNextSection()
	{
		nextListPanel.removeAll();
		Advice advice = currentAdvice;
		List<RankedGoal> rest = advice.getRest();
		int shown = Math.min(nextShown, rest.size());
		for (int i = 0; i < shown; i++)
		{
			nextListPanel.add(buildRow(rest.get(i), advice.getWhys(), advice.getExplanations()));
		}
		showMoreButton.setVisible(shown < rest.size());
		revalidate();
		repaint();
	}

	private JPanel buildCard(RankedGoal r, Map<String, String> whys, Map<String, String> reasons,
		Map<String, List<String>> explanations)
	{
		Goal goal = r.getStatus().getGoal();

		JPanel card = new JPanel();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setAlignmentX(Component.LEFT_ALIGNMENT);
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR, CARD_BORDER_THICKNESS),
			BorderFactory.createEmptyBorder(CARD_PADDING, CARD_PADDING, CARD_PADDING, CARD_PADDING)));

		JLabel nameLabel = new JLabel(goal.getName() + (r.isPinned() ? " (pinned)" : ""));
		nameLabel.setFont(FontManager.getRunescapeBoldFont());
		nameLabel.setForeground(ColorScheme.TEXT_COLOR);
		nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		card.add(nameLabel);
		card.add(Box.createVerticalStrut(4));

		JLabel categoryLabel = new JLabel(categoryLabel(goal.getCategory()) + " · stage " + goal.getStage());
		categoryLabel.setFont(FontManager.getRunescapeSmallFont());
		categoryLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		categoryLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		card.add(categoryLabel);
		card.add(Box.createVerticalStrut(4));

		if (goal.getCategory() == GoalCategory.SKILL_TARGET)
		{
			card.add(skillTargetDetail(r.getStatus()));
		}

		JLabel whyLabel = new JLabel(wrap(whys.getOrDefault(goal.getId(), "")));
		whyLabel.setFont(FontManager.getRunescapeFont());
		whyLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		card.add(whyLabel);

		String reason = reasons.get(goal.getId());
		if (reason != null && !reason.isEmpty())
		{
			card.add(Box.createVerticalStrut(4));
			JLabel reasonLabel = new JLabel(wrap(truncateReason(reason)));
			reasonLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.ITALIC));
			reasonLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
			card.add(reasonLabel);
		}

		card.add(Box.createVerticalStrut(4));
		card.add(buildWhyToggle(goal.getId(), explanations));

		card.add(Box.createVerticalStrut(6));
		JButton doThis = button("Do this", () -> actions.getDoThis().accept(goal.getId()));
		JButton notNow = button("Not now", () -> actions.getNotNow().accept(goal.getId()));
		JButton ignore = button("Ignore", () -> actions.getIgnore().accept(goal.getId()));
		card.add(actionRow(doThis, notNow, ignore));

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
		row.add(buildWhyToggle(goal.getId(), explanations));
		row.add(Box.createVerticalStrut(4));

		JButton doThis = button("Do this", () -> actions.getDoThis().accept(goal.getId()));
		JButton notNow = button("Not now", () -> actions.getNotNow().accept(goal.getId()));
		JButton ignore = button("Ignore", () -> actions.getIgnore().accept(goal.getId()));
		JButton pin = r.isPinned()
			? button("Unpin", () -> actions.getUnpin().accept(goal.getId()))
			: button("Pin", () -> actions.getPin().accept(goal.getId()));
		row.add(actionRow(doThis, notNow, ignore, pin));

		return row;
	}

	/** Task 52b: a {@link GoalCategory#SKILL_TARGET} card's parent goals line and, when {@link GoalStatus#isBankCovered()}, a small "materials in bank" badge. */
	private static JPanel skillTargetDetail(GoalStatus status)
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);

		List<GoalRef> parents = status.getParents();
		if (!parents.isEmpty())
		{
			JLabel parentsLabel = new JLabel(wrap("for " + parents.stream().map(GoalRef::getName).collect(Collectors.joining(", "))));
			parentsLabel.setFont(FontManager.getRunescapeSmallFont());
			parentsLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			parentsLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
			panel.add(parentsLabel);
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
	 * Task 52b: a collapsed-by-default "Why?" toggle; expanding it lists {@code explanations}'
	 * lines for {@code goalId} in the small grey font, wrapped to the card width. Expansion state
	 * persists per goal id in {@link #expandedWhy} across re-renders (same rule as the collapsible
	 * section headers).
	 */
	private JPanel buildWhyToggle(String goalId, Map<String, List<String>> explanations)
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);

		boolean expanded = expandedWhy.contains(goalId);

		JLabel toggle = new JLabel("Why? " + (expanded ? "▼" : "▶"));
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
				JLabel lineLabel = new JLabel(wrap(line));
				lineLabel.setFont(FontManager.getRunescapeSmallFont());
				lineLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
				lineLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
				panel.add(lineLabel);
			}
		}

		return panel;
	}

	private JPanel bringBackRow(String goalId, Advice advice, String buttonLabel, Consumer<String> action)
	{
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		JLabel label = new JLabel(nameFor(goalId, advice));
		label.setFont(FontManager.getRunescapeSmallFont());
		row.add(label, BorderLayout.CENTER);
		row.add(button(buttonLabel, () -> action.accept(goalId)), BorderLayout.EAST);
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
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
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

			// fix round 1: AWT dispatches a click to the deepest component under the cursor and
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
			label.setText(title.toUpperCase() + " (" + count + ")" + (expanded ? " ▼" : " ▶"));
		}
	}

	/**
	 * Lays out {@code buttons} on a single {@link GridLayout}{@code (1, n, 4, 0)} row when they
	 * fit, otherwise splits into two rows and recurses on each half - so a pair that still
	 * doesn't fit degrades again instead of being assumed safe.
	 *
	 * <p>Fix round 1: {@link GridLayout} gives every cell the SAME width (the row's width divided
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
		String escaped = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
		return "<html><div style='width:" + WRAP_WIDTH + "px'>" + escaped + "</div></html>";
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
	}
}
