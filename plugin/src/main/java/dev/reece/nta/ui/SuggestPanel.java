package dev.reece.nta.ui;

import dev.reece.nta.engine.model.Advice;
import dev.reece.nta.engine.model.Goal;
import dev.reece.nta.engine.model.GoalCategory;
import dev.reece.nta.engine.model.GoalStatus;
import dev.reece.nta.engine.model.PrefsView;
import dev.reece.nta.engine.model.RankedGoal;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
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
 */
public class SuggestPanel extends JPanel
{
	private static final int PAGE_SIZE = 10;
	private static final int WRAP_WIDTH = PluginPanel.PANEL_WIDTH - 30;

	private final Actions actions;
	private final JPanel focusBannerPanel = new JPanel();
	private final JLabel focusLabel = new JLabel();
	private final JPanel pickOnePanel = new JPanel();
	private final JPanel nextListPanel = new JPanel();
	private final JButton showMoreButton = new JButton("Show more");
	private final JButton laterHeaderButton = new JButton("Later (0)");
	private final JPanel laterContent = new JPanel();
	private final JButton snoozedHeaderButton = new JButton("Snoozed (0)");
	private final JPanel snoozedContent = new JPanel();
	private final JButton ignoredHeaderButton = new JButton("Ignored (0)");
	private final JPanel ignoredContent = new JPanel();

	private Advice currentAdvice;
	private int nextShown = PAGE_SIZE;
	private boolean laterExpanded;
	private boolean snoozedExpanded;
	private boolean ignoredExpanded;

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

		laterHeaderButton.setAlignmentX(Component.LEFT_ALIGNMENT);
		laterHeaderButton.addActionListener(e ->
		{
			laterExpanded = !laterExpanded;
			rebuild();
		});
		add(laterHeaderButton);
		laterContent.setLayout(new BoxLayout(laterContent, BoxLayout.Y_AXIS));
		laterContent.setAlignmentX(Component.LEFT_ALIGNMENT);
		laterContent.setVisible(false);
		add(laterContent);

		snoozedHeaderButton.setAlignmentX(Component.LEFT_ALIGNMENT);
		snoozedHeaderButton.addActionListener(e ->
		{
			snoozedExpanded = !snoozedExpanded;
			rebuild();
		});
		add(snoozedHeaderButton);
		snoozedContent.setLayout(new BoxLayout(snoozedContent, BoxLayout.Y_AXIS));
		snoozedContent.setAlignmentX(Component.LEFT_ALIGNMENT);
		snoozedContent.setVisible(false);
		add(snoozedContent);

		ignoredHeaderButton.setAlignmentX(Component.LEFT_ALIGNMENT);
		ignoredHeaderButton.addActionListener(e ->
		{
			ignoredExpanded = !ignoredExpanded;
			rebuild();
		});
		add(ignoredHeaderButton);
		ignoredContent.setLayout(new BoxLayout(ignoredContent, BoxLayout.Y_AXIS));
		ignoredContent.setAlignmentX(Component.LEFT_ALIGNMENT);
		ignoredContent.setVisible(false);
		add(ignoredContent);
	}

	/**
	 * Renders {@code advice}. Must be called on the EDT. Resets the "show more" paging back to the
	 * first page, per spec (panel-local state, reset on new Advice).
	 */
	public void render(Advice advice)
	{
		if (!SwingUtilities.isEventDispatchThread())
		{
			throw new IllegalStateException("SuggestPanel.render must run on the EDT");
		}

		this.currentAdvice = advice;
		this.nextShown = PAGE_SIZE;
		rebuild();
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
			pickOnePanel.add(buildCard(r, advice.getWhys(), advice.getReasons()));
			pickOnePanel.add(Box.createVerticalStrut(6));
		}

		rebuildNextSection();

		laterHeaderButton.setText("Later (" + advice.getLater().size() + ")" + (laterExpanded ? " ▼" : " ▶"));
		laterContent.removeAll();
		laterContent.setVisible(laterExpanded);
		if (laterExpanded)
		{
			for (RankedGoal r : advice.getLater())
			{
				laterContent.add(buildRow(r, advice.getWhys()));
			}
		}

		Set<String> ignoredIds = new LinkedHashSet<>(prefs.getHidden());
		ignoredIds.removeAll(prefs.getSnoozedActive());

		snoozedHeaderButton.setText("Snoozed (" + prefs.getSnoozedActive().size() + ")" + (snoozedExpanded ? " ▼" : " ▶"));
		snoozedContent.removeAll();
		snoozedContent.setVisible(snoozedExpanded);
		if (snoozedExpanded)
		{
			for (String goalId : prefs.getSnoozedActive())
			{
				snoozedContent.add(bringBackRow(goalId, advice, "Bring back", actions.getUnsnooze()));
			}
		}

		ignoredHeaderButton.setText("Ignored (" + ignoredIds.size() + ")" + (ignoredExpanded ? " ▼" : " ▶"));
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
			nextListPanel.add(buildRow(rest.get(i), advice.getWhys()));
		}
		showMoreButton.setVisible(shown < rest.size());
		revalidate();
		repaint();
	}

	private JPanel buildCard(RankedGoal r, Map<String, String> whys, Map<String, String> reasons)
	{
		Goal goal = r.getStatus().getGoal();

		JPanel card = new JPanel();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setAlignmentX(Component.LEFT_ALIGNMENT);
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
			BorderFactory.createEmptyBorder(6, 6, 6, 6)));

		JLabel nameLabel = new JLabel(goal.getName() + (r.isPinned() ? " (pinned)" : ""));
		nameLabel.setFont(FontManager.getRunescapeBoldFont());
		nameLabel.setForeground(ColorScheme.TEXT_COLOR);
		card.add(nameLabel);

		JLabel categoryLabel = new JLabel(categoryLabel(goal.getCategory()));
		categoryLabel.setFont(FontManager.getRunescapeSmallFont());
		card.add(categoryLabel);

		JLabel whyLabel = new JLabel(wrap(whys.getOrDefault(goal.getId(), "")));
		whyLabel.setFont(FontManager.getRunescapeSmallFont());
		card.add(whyLabel);

		String reason = reasons.get(goal.getId());
		if (reason != null && !reason.isEmpty())
		{
			JLabel reasonLabel = new JLabel(wrap(reason));
			reasonLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.ITALIC));
			card.add(reasonLabel);
		}

		JPanel buttons = new JPanel(new GridLayout(1, 3, 4, 0));
		buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
		buttons.add(button("Do this", () -> actions.getDoThis().accept(goal.getId())));
		buttons.add(button("Not now", () -> actions.getNotNow().accept(goal.getId())));
		buttons.add(button("Ignore", () -> actions.getIgnore().accept(goal.getId())));
		card.add(buttons);

		return card;
	}

	private JPanel buildRow(RankedGoal r, Map<String, String> whys)
	{
		Goal goal = r.getStatus().getGoal();
		String why = whys.getOrDefault(goal.getId(), "");

		JPanel row = new JPanel(new BorderLayout(4, 2));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));

		JLabel label = new JLabel(wrap(goal.getName() + (r.isPinned() ? " (pinned)" : "") + " — " + why));
		label.setFont(FontManager.getRunescapeSmallFont());
		row.add(label, BorderLayout.CENTER);

		JPanel buttons = new JPanel(new GridLayout(1, 4, 2, 0));
		buttons.add(button("Do", () -> actions.getDoThis().accept(goal.getId())));
		buttons.add(button("Not now", () -> actions.getNotNow().accept(goal.getId())));
		buttons.add(button("Ignore", () -> actions.getIgnore().accept(goal.getId())));
		buttons.add(r.isPinned()
			? button("Unpin", () -> actions.getUnpin().accept(goal.getId()))
			: button("Pin", () -> actions.getPin().accept(goal.getId())));
		row.add(buttons, BorderLayout.SOUTH);

		return row;
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
			default:
				return category.name();
		}
	}

	static JLabel sectionLabel(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeBoldFont());
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		label.setBorder(BorderFactory.createEmptyBorder(8, 0, 2, 0));
		return label;
	}

	static JButton button(String text, Runnable onClick)
	{
		JButton b = new JButton(text);
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
