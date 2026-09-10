package com.signpost.ui;

import com.signpost.NextTargetConfig;
import com.signpost.engine.model.Advice;
import com.signpost.engine.model.NextStep;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;

/** Content is rebuilt on Advice/config publication, never by a client tick or paint. */
public final class SignpostOverlay extends OverlayPanel
{
	private final NextTargetConfig config;
	private Advice lastAdvice;
	private boolean enabled;

	public SignpostOverlay(NextTargetConfig config)
	{
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
		setMovable(true);
		setClearChildren(false);
		panelComponent.setPreferredSize(new Dimension(260, 0));
	}

	/** Publication and painting share the panel's mutable layout components. */
	public synchronized void update(Advice advice)
	{
		boolean show = config.showNextStepOnScreen();
		if (lastAdvice == advice && enabled == show) return;
		lastAdvice = advice;
		enabled = show;
		panelComponent.getChildren().clear();
		if (!show || advice == null || advice.getFocus() == null) return;
		NextStep next = advice.getFocus().getNext();
		if (!next.hasGuidance()) return;
		panelComponent.getChildren().add(LineComponent.builder()
			.left(advice.getFocus().getStatus().getGoal().getName()).leftColor(Color.YELLOW).build());
		panelComponent.getChildren().add(LineComponent.builder().left("BRING: " + next.getBring()).build());
		panelComponent.getChildren().add(LineComponent.builder().left("WHERE: " + next.getWhere()).build());
		panelComponent.getChildren().add(LineComponent.builder().left("DO: " + next.getDoText()).build());
	}

	@Override
	public synchronized Dimension render(Graphics2D graphics)
	{
		return panelComponent.getChildren().isEmpty() ? null : super.render(graphics);
	}
}
