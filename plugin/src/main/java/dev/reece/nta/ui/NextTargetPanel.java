package dev.reece.nta.ui;

import dev.reece.nta.engine.model.Advice;
import dev.reece.nta.engine.model.GoalStatus;
import dev.reece.nta.snapshot.Snapshot;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.GridLayout;
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
	private final JPanel body = new JPanel(new CardLayout());

	private static final String SUGGEST_CARD = "suggest";
	private static final String DETAIL_CARD = "detail";

	public NextTargetPanel(Runnable onRefresh, SuggestPanel.Actions actions, GoalDetailPanel.Actions detailActions)
	{
		suggestPanel = new SuggestPanel(actions);
		goalDetailPanel = new GoalDetailPanel(detailActions);
		body.add(suggestPanel, SUGGEST_CARD);
		body.add(goalDetailPanel, DETAIL_CARD);

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
		refreshButton.addActionListener(e -> onRefresh.run());
		header.add(refreshButton);

		kbFooterLabel.setHorizontalAlignment(SwingConstants.CENTER);
		JPanel footer = new JPanel(new GridLayout(1, 1));
		footer.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
		footer.add(kbFooterLabel);

		add(header, BorderLayout.NORTH);
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
		}
		else
		{
			String time = BANK_TIME_FORMAT.withZone(ZoneId.systemDefault()).format(snapshot.getBankAsOf());
			bankLabel.setText("Bank as of " + time);
		}

		long ready = advice.getStatuses().stream().filter(GoalStatus::isReady).count();
		long withGaps = advice.getStatuses().stream().filter(s -> !s.getGaps().isEmpty()).count();
		goalsLabel.setText("Goals: " + ready + " ready, " + withGaps + " with gaps");

		CardLayout cardLayout = (CardLayout) body.getLayout();
		if (advice.getFocus() != null)
		{
			goalDetailPanel.render(advice);
			cardLayout.show(body, DETAIL_CARD);
		}
		else
		{
			suggestPanel.render(advice);
			cardLayout.show(body, SUGGEST_CARD);
		}
	}
}
