package com.signpost.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Insets;
import javax.swing.JComponent;
import net.runelite.client.ui.ColorScheme;

/**
 * A 4px progress bar (has no look-and-feel dependency, unlike {@code JProgressBar}).
 * Shared by {@link GoalDetailPanel}'s skill rows and {@link SuggestPanel}'s skill-target cards.
 */
public final class ProgressBar extends JComponent
{
	private final double fraction;
	private final Color color;

	public ProgressBar(double fraction, Color color)
	{
		this.fraction = Math.max(0, Math.min(1, fraction));
		this.color = color;
		setAlignmentX(Component.LEFT_ALIGNMENT);
	}

	@Override
	public Dimension getPreferredSize()
	{
		Insets in = getInsets();
		return new Dimension(40 + in.left + in.right, 4 + in.top + in.bottom);
	}

	@Override
	public Dimension getMaximumSize()
	{
		return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		Insets in = getInsets();
		int w = getWidth() - in.left - in.right;
		int h = getHeight() - in.top - in.bottom;
		g.setColor(ColorScheme.MEDIUM_GRAY_COLOR);
		g.fillRect(in.left, in.top, w, h);
		g.setColor(color);
		g.fillRect(in.left, in.top, (int) Math.round(w * fraction), h);
	}
}
