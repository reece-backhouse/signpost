package dev.reece.nta.ui;

import dev.reece.nta.engine.model.Advice;
import dev.reece.nta.engine.model.GoalStatus;
import dev.reece.nta.snapshot.Snapshot;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Insets;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

/**
 * Header-only view of the account snapshot: account type, quest progress, total level, and bank
 * freshness, plus a manual refresh trigger. Only renders what {@link Snapshot} already computed;
 * this class does no computation of its own.
 */
public class NextTargetPanel extends PluginPanel
{
	private static final DateTimeFormatter BANK_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

	private final JLabel accountTypeLabel = new JLabel("Log in to see your account");
	private final JLabel questsLabel = new JLabel();
	private final JLabel totalLevelLabel = new JLabel();
	private final JLabel bankLabel = new JLabel();
	private final JLabel goalsLabel = new JLabel();
	private final JLabel kbFooterLabel = new JLabel("KB: not loaded");
	private final SuggestPanel suggestPanel;
	private final GoalDetailPanel goalDetailPanel;
	private final GoalSearchField goalSearchField;
	// task 57: not a CardLayout - that sizes both views to the TALLER one (the Suggest list), and
	// the detail view's BoxLayout then stretched every section to fill the gap. The active view is
	// swapped in at NORTH so it only ever takes its own preferred height.
	private final JPanel body = new JPanel(new BorderLayout());

	public NextTargetPanel(Runnable onRefresh, SuggestPanel.Actions actions, GoalDetailPanel.Actions detailActions)
	{
		suggestPanel = new SuggestPanel(actions);
		goalDetailPanel = new GoalDetailPanel(detailActions);
		goalSearchField = new GoalSearchField(detailActions.getFocus());
		show(suggestPanel);

		setLayout(new BorderLayout());

		JPanel header = new JPanel();
		header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
		header.add(accountTypeLabel);
		header.add(questsLabel);
		header.add(totalLevelLabel);
		header.add(bankLabel);
		header.add(goalsLabel);
		header.add(Box.createVerticalStrut(8));

		JButton refreshButton = new JButton("Refresh");
		refreshButton.setMargin(new Insets(2, 4, 2, 4));
		refreshButton.setFocusPainted(false);
		refreshButton.setAlignmentX(Component.LEFT_ALIGNMENT);
		refreshButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, refreshButton.getPreferredSize().height));
		refreshButton.addActionListener(e -> onRefresh.run());
		header.add(refreshButton);

		kbFooterLabel.setHorizontalAlignment(SwingConstants.CENTER);
		JPanel footer = new JPanel(new GridLayout(1, 1));
		footer.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
		footer.add(kbFooterLabel);

		JPanel north = new JPanel();
		north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
		north.add(header);
		north.add(goalSearchField);

		add(north, BorderLayout.NORTH);
		add(body, BorderLayout.CENTER);
		add(footer, BorderLayout.SOUTH);
	}

	/**
	 * Shows the knowledge base's generation dates in the footer, once loaded. Must be called on
	 * the EDT.
	 */
	public void showKbLoaded(String questsGeneratedAt, String diariesGeneratedAt)
	{
		if (!SwingUtilities.isEventDispatchThread())
		{
			throw new IllegalStateException("NextTargetPanel.showKbLoaded must run on the EDT");
		}

		kbFooterLabel.setText("KB: quests " + bareDate(questsGeneratedAt) + ", diaries " + bareDate(diariesGeneratedAt));
	}

	/** {@code generatedAt} is always ISO-8601 ("2026-09-07T07:29:49Z"); the footer shows just the date. */
	private static String bareDate(String generatedAt)
	{
		return generatedAt.length() >= 10 ? generatedAt.substring(0, 10) : generatedAt;
	}

	/** Logged out: header back to its pre-login text, every goal card/row removed so nothing stale is clickable. Must be called on the EDT. */
	public void showLoggedOut()
	{
		if (!SwingUtilities.isEventDispatchThread())
		{
			throw new IllegalStateException("NextTargetPanel.showLoggedOut must run on the EDT");
		}

		accountTypeLabel.setText("Log in to see your account");
		questsLabel.setText("");
		totalLevelLabel.setText("");
		bankLabel.setText("");
		goalsLabel.setText("");
		goalSearchField.clear();
		suggestPanel.clear();
		show(suggestPanel);
		revalidate();
		repaint();
	}

	/**
	 * Renders {@code advice}. Must be called on the EDT; the caller (the plugin) is responsible
	 * for dispatching via {@link SwingUtilities#invokeLater}.
	 */
	public void render(Advice advice)
	{
		if (!SwingUtilities.isEventDispatchThread())
		{
			throw new IllegalStateException("NextTargetPanel.render must run on the EDT");
		}

		Snapshot snapshot = advice.getSnapshot();

		accountTypeLabel.setText("Account type: " + snapshot.getAccountType() + " — Stage: " + advice.getAccountStage());

		long questsDone = snapshot.getQuests().values().stream().filter(state -> state == QuestState.FINISHED).count();
		questsLabel.setText("Quests: " + questsDone + "/" + Quest.values().length);

		int totalLevel = snapshot.getSkills().values().stream().mapToInt(skill -> skill.getLevel()).sum();
		totalLevelLabel.setText("Total level: " + totalLevel);

		if (!snapshot.isBankKnown())
		{
			bankLabel.setText("Bank: unknown — open your bank once");
			bankLabel.setForeground(ColorScheme.TEXT_COLOR);
		}
		else
		{
			String time = BANK_TIME_FORMAT.withZone(ZoneId.systemDefault()).format(snapshot.getBankAsOf());
			boolean stale = Duration.between(snapshot.getBankAsOf(), advice.getComputedAt()).toMinutes() >= 60;
			bankLabel.setText(stale ? "Bank as of " + time + " (stale, open your bank to refresh)" : "Bank as of " + time);
			bankLabel.setForeground(stale ? ColorScheme.PROGRESS_ERROR_COLOR : ColorScheme.TEXT_COLOR);
		}

		long ready = advice.getStatuses().stream().filter(GoalStatus::isReady).count();
		long withGaps = advice.getStatuses().stream().filter(s -> !s.getGaps().isEmpty()).count();
		goalsLabel.setText("Goals: " + ready + " ready, " + withGaps + " with gaps");

		goalSearchField.render(advice);

		if (advice.getFocus() != null)
		{
			goalDetailPanel.render(advice);
			show(goalDetailPanel);
		}
		else
		{
			suggestPanel.render(advice);
			show(suggestPanel);
		}
	}

	private void show(JPanel view)
	{
		if (view.getParent() == body)
		{
			return;
		}
		body.removeAll();
		body.add(view, BorderLayout.NORTH);
		body.revalidate();
		body.repaint();
	}
}
