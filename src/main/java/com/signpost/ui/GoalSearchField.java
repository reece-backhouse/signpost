package com.signpost.ui;

import com.signpost.engine.model.Advice;
import com.signpost.engine.model.Goal;
import com.signpost.engine.model.RankedGoal;
import java.awt.Component;
import java.awt.Dimension;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * A goal search box that is always visible, regardless of Suggest/Detail mode - hosted by
 * {@link NextTargetPanel} directly under the header.
 * Up to 8 matching goals from {@link Advice#getRanked()} (including Later goals),
 * case-insensitive substring on the goal name; clicking a result calls
 * the {@code focus} action passed in at construction. Renders {@link Advice} only; the typed query
 * survives a re-render unless the underlying goal set changed.
 */
public class GoalSearchField extends JPanel
{
	private static final int MAX_RESULTS = 8;
	private static final String PLACEHOLDER = "Search goals…";

	private final Consumer<String> focus;
	private final JTextField searchField = new JTextField();
	private final JPanel resultsPanel = new JPanel();

	private Advice currentAdvice;
	private Set<String> lastGoalIds;

	public GoalSearchField(Consumer<String> focus)
	{
		this.focus = focus;

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setAlignmentX(Component.LEFT_ALIGNMENT);

		searchField.putClientProperty("JTextField.placeholderText", PLACEHOLDER);
		searchField.setAlignmentX(Component.LEFT_ALIGNMENT);
		searchField.setMaximumSize(new Dimension(Integer.MAX_VALUE, searchField.getPreferredSize().height));
		add(searchField);

		searchField.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				updateResults();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				updateResults();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				updateResults();
			}
		});

		resultsPanel.setLayout(new BoxLayout(resultsPanel, BoxLayout.Y_AXIS));
		resultsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		add(resultsPanel);
	}

	/**
	 * Renders {@code advice}. Must be called on the EDT. Clears the typed query only when the pool
	 * of ranked goal ids has changed since the previous render; otherwise
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
			for (RankedGoal r : advice.getRanked())
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
		updateResults();
	}

	/** The ranked goal ids: the "same goal set" key shared with {@link SuggestPanel}'s paging. */
	static Set<String> goalIds(Advice advice)
	{
		Set<String> goalIds = new LinkedHashSet<>();
		for (RankedGoal r : advice.getRanked())
		{
			goalIds.add(r.getStatus().getGoal().getId());
		}
		return goalIds;
	}
}
