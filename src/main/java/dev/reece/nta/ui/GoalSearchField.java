package dev.reece.nta.ui;

import dev.reece.nta.engine.model.Advice;
import dev.reece.nta.engine.model.Goal;
import dev.reece.nta.engine.model.RankedGoal;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.OverlayLayout;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Task 47: a goal search box that is always visible, regardless of Suggest/Detail mode - hosted by
 * {@link NextTargetPanel} directly under the header (the user feedback that prompted this: "I don't
 * see a search box" from Suggest mode, where {@link GoalDetailPanel}'s old, detail-only search was
 * unreachable). Up to 8 matching goals from {@link Advice#getRanked()} union
 * {@link Advice#getLater()}, case-insensitive substring on the goal name; clicking a result calls
 * the {@code focus} action passed in at construction. Renders {@link Advice} only; the typed query
 * survives a re-render unless the underlying goal set changed.
 */
public class GoalSearchField extends JPanel
{
	private static final int MAX_RESULTS = 8;
	private static final String PLACEHOLDER = "Search goals…";

	private final Consumer<String> focus;
	private final JTextField searchField = new JTextField();
	// ponytail: plain-Swing placeholder overlay (JTextField has no built-in prompt text) instead of a
	// dependency; the label just sits behind the field and is hidden once any text is typed.
	private final JLabel placeholderLabel = new JLabel(PLACEHOLDER);
	private final JPanel resultsPanel = new JPanel();

	private Advice currentAdvice;
	private Set<String> lastGoalIds;

	public GoalSearchField(Consumer<String> focus)
	{
		this.focus = focus;

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setAlignmentX(Component.LEFT_ALIGNMENT);

		placeholderLabel.setForeground(Color.GRAY);
		placeholderLabel.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));

		JPanel fieldStack = new JPanel();
		fieldStack.setLayout(new OverlayLayout(fieldStack));
		fieldStack.setAlignmentX(Component.LEFT_ALIGNMENT);
		fieldStack.setMaximumSize(new Dimension(Integer.MAX_VALUE, searchField.getPreferredSize().height));
		fieldStack.add(placeholderLabel);
		fieldStack.add(searchField);
		add(fieldStack);

		searchField.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				changed();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				changed();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				changed();
			}

			private void changed()
			{
				placeholderLabel.setVisible(searchField.getText().isEmpty());
				updateResults();
			}
		});

		resultsPanel.setLayout(new BoxLayout(resultsPanel, BoxLayout.Y_AXIS));
		resultsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		add(resultsPanel);
	}

	/**
	 * Renders {@code advice}. Must be called on the EDT. Clears the typed query only when the pool
	 * of searchable goal ids (ranked union later) has changed since the previous render; otherwise
	 * the query is kept and the results are simply recomputed against the new {@code advice}.
	 */
	public void render(Advice advice)
	{
		if (!SwingUtilities.isEventDispatchThread())
		{
			throw new IllegalStateException("GoalSearchField.render must run on the EDT");
		}

		this.currentAdvice = advice;

		Set<String> goalIds = goalIds(advice);
		boolean goalSetChanged = lastGoalIds != null && !lastGoalIds.equals(goalIds);
		lastGoalIds = goalIds;

		if (goalSetChanged)
		{
			searchField.setText("");
		}

		placeholderLabel.setVisible(searchField.getText().isEmpty());
		updateResults();
	}

	private void updateResults()
	{
		resultsPanel.removeAll();

		Advice advice = currentAdvice;
		String query = searchField.getText().trim().toLowerCase();
		if (advice != null && !query.isEmpty())
		{
			int shown = 0;
			for (RankedGoal r : pool(advice))
			{
				if (shown >= MAX_RESULTS)
				{
					break;
				}
				Goal goal = r.getStatus().getGoal();
				if (goal.getName().toLowerCase().contains(query))
				{
					String goalId = goal.getId();
					resultsPanel.add(SuggestPanel.button(goal.getName(), () -> focus.accept(goalId)));
					shown++;
				}
			}
		}

		resultsPanel.revalidate();
		resultsPanel.repaint();
	}

	/** Drops the query and results (logged out). Must be called on the EDT. */
	public void clear()
	{
		currentAdvice = null;
		lastGoalIds = null;
		searchField.setText("");
		placeholderLabel.setVisible(true);
		updateResults();
	}

	/** The ids of {@link #pool}: the "same goal set" key shared with {@link SuggestPanel}'s paging. */
	static Set<String> goalIds(Advice advice)
	{
		Set<String> goalIds = new LinkedHashSet<>();
		for (RankedGoal r : pool(advice))
		{
			goalIds.add(r.getStatus().getGoal().getId());
		}
		return goalIds;
	}

	/** {@link Advice#getRanked()} union {@link Advice#getLater()}, deduplicated by goal id, in that order. */
	private static List<RankedGoal> pool(Advice advice)
	{
		Set<String> seen = new LinkedHashSet<>();
		List<RankedGoal> pool = new ArrayList<>();
		for (RankedGoal r : advice.getRanked())
		{
			if (seen.add(r.getStatus().getGoal().getId()))
			{
				pool.add(r);
			}
		}
		for (RankedGoal r : advice.getLater())
		{
			if (seen.add(r.getStatus().getGoal().getId()))
			{
				pool.add(r);
			}
		}
		return pool;
	}
}
