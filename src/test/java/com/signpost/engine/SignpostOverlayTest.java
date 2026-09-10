package com.signpost.engine;

import com.signpost.NextTargetConfig;
import com.signpost.engine.model.Advice;
import com.signpost.engine.model.BringItemStatus;
import com.signpost.engine.model.FocusDetail;
import com.signpost.engine.model.Goal;
import com.signpost.engine.model.GoalCategory;
import com.signpost.engine.model.GoalStatus;
import com.signpost.engine.model.NextStep;
import com.signpost.engine.model.PrefsView;
import com.signpost.kb.BringItem;
import com.signpost.ui.SignpostOverlay;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignpostOverlayTest
{
	@Test
	void adviceAndConfigPublicationsUpdateTheOverlayWithoutPaintTimePolling()
	{
		OverlayConfig config = new OverlayConfig();
		SignpostOverlay overlay = new SignpostOverlay(config);
		Advice advice = advice("Farming supplies", guidance("Walk to Falador farm. Plant the seeds."));
		overlay.update(advice);
		assertNull(render(overlay, FontManager.getRunescapeFont(), null));

		config.show = true;
		overlay.update(advice);
		List<LayoutableRenderableEntity> published = List.copyOf(overlay.getPanelComponent().getChildren());
		assertEquals(4, published.size(), "goal and the three guidance lines");
		BufferedImage first = image();
		assertNotNull(render(overlay, FontManager.getRunescapeFont(), first));
		int reads = config.reads;
		render(overlay, FontManager.getRunescapeFont(), null);
		assertEquals(reads, config.reads, "rendering must not poll config or recompute advice");
		overlay.update(advice);
		for (int i = 0; i < published.size(); i++)
		{
			assertSame(published.get(i), overlay.getPanelComponent().getChildren().get(i), "same publication keeps cached content");
		}

		overlay.update(advice("Farming supplies", guidance("Walk to Falador farm. Plant the seeds.")));
		assertNotSame(published.get(0), overlay.getPanelComponent().getChildren().get(0), "a new Advice identity republishes content");
		overlay.update(advice("Prayer supplies", guidance("Enter the cave. Collect bones.")));
		BufferedImage changed = image();
		render(overlay, FontManager.getRunescapeFont(), changed);
		assertFalse(Arrays.equals(pixels(first), pixels(changed)), "a new goal/action must change the rendered guidance");

		config.show = false;
		overlay.update(advice);
		assertNull(render(overlay, FontManager.getRunescapeFont(), null));
		config.show = true;
		overlay.update(advice);
		assertNotNull(render(overlay, FontManager.getRunescapeFont(), null));
		overlay.update(advice("No immediate action", NextStep.none()));
		assertNull(render(overlay, FontManager.getRunescapeFont(), null), "old guidance must disappear");
		overlay.update(null);
		assertNull(render(overlay, FontManager.getRunescapeFont(), null), "logout/cleared advice leaves no stale overlay");
	}

	@Test
	void bufferedGraphicsUsesTheNativeOverlayFontAndKeepsTheUsersPlacement()
	{
		OverlayConfig config = new OverlayConfig();
		config.show = true;
		SignpostOverlay overlay = new SignpostOverlay(config);
		overlay.setPreferredLocation(new Point(80, 120));
		overlay.setPreferredSize(new Dimension(230, 0));
		overlay.update(advice("Prepare for the next farming trip", guidance("Walk to Falador farm. Plant seeds and return to the bank.")));
		assertTrue(overlay.isMovable(), "RuneLite must permit dragging this panel");
		BufferedImage small = image();
		Dimension smallSize = render(overlay, FontManager.getRunescapeFont(), small);
		BufferedImage large = image();
		Dimension largeSize = render(overlay, FontManager.getRunescapeFont().deriveFont(24f), large);
		assertTrue(largeSize.height > smallSize.height, "line layout must use the font supplied by RuneLite");
		assertTrue(smallSize.width <= 230 && largeSize.width <= 230, "guidance must wrap at the resized overlay width");
		assertEquals(new Point(80, 120), overlay.getPreferredLocation(), "publication/render must not reset a dragged location");
		RenderSmokeTest.capturePng(small.getSubimage(0, 0, smallSize.width, smallSize.height), "next-step-overlay.png");
		RenderSmokeTest.capturePng(large.getSubimage(0, 0, largeSize.width, largeSize.height), "next-step-overlay-large-font.png");
	}

	private static Dimension render(SignpostOverlay overlay, Font font, BufferedImage target)
	{
		Graphics2D graphics = (target == null ? image() : target).createGraphics();
		try
		{
			graphics.setFont(font);
			// PanelComponent reports the previous frame's bounds. Settle native layout before capture.
			Graphics2D layout = (Graphics2D) graphics.create();
			try
			{
				layout.setClip(0, 0, 0, 0);
				overlay.render(layout);
			}
			finally
			{
				layout.dispose();
			}
			Dimension size = overlay.render(graphics);
			assertEquals(font, graphics.getFont(), "overlay must not replace the user's chosen font");
			return size;
		}
		finally
		{
			graphics.dispose();
		}
	}

	private static BufferedImage image()
	{
		return new BufferedImage(600, 1200, BufferedImage.TYPE_INT_ARGB);
	}

	private static int[] pixels(BufferedImage image)
	{
		return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
	}

	private static NextStep guidance(String action)
	{
		return NextStep.none().withGuidance(List.of(new BringItemStatus(new BringItem("Spade", 952), 1, 1),
			new BringItemStatus(new BringItem("Coins", 995), 5, 4)), "Falador farm", action);
	}

	private static Advice advice(String name, NextStep next)
	{
		GoalStatus status = new GoalStatus(new Goal("quest:0", GoalCategory.QUEST, name, null, 1, 1),
			List.of(), true, false, List.of());
		FocusDetail focus = new FocusDetail(status, next, null, null, 0, 0, List.of(), null);
		return new Advice(new SnapshotBuilder().build(), List.of(status), Map.of(), Instant.EPOCH,
			List.of(), List.of(), List.of(), 1, List.of(), Map.of(), Map.of(), Map.of(), Map.of(),
			new PrefsView(Set.of(), List.of(), Set.of(), Set.of(), "quest:0", Set.of()), focus, List.of());
	}

	private static final class OverlayConfig implements NextTargetConfig
	{
		private boolean show;
		private int reads;

		@Override
		public boolean showNextStepOnScreen()
		{
			reads++;
			return show;
		}
	}
}
