package dev.reece.nta.ui;

import dev.reece.nta.engine.model.GoalCategory;
import dev.reece.nta.kb.WikiUrls;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.plugins.worldmap.WorldMapPlugin;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.LinkBrowser;

/**
 * Task 57: image-only access to RuneLite's item and skill icon caches for the panels, plus the
 * wiki-link and category-colour helpers every row shares. Holds the managers for images only -
 * never the {@code Client} - and is null-safe: a missing manager (tests) renders a fixed-size
 * placeholder label named {@value #PLACEHOLDER_NAME} instead of an image. Every method is EDT-safe;
 * {@link ItemManager#getImage} returns an {@link AsyncBufferedImage} that paints itself once loaded.
 */
public class Icons
{
	public static final String ITEM_ICON_NAME = "item-icon";
	public static final String PLACEHOLDER_NAME = "item-icon-placeholder";
	private static final int ITEM_SIZE = 24;
	private static final String QUEST_GLYPH = "◆";

	private final ItemManager itemManager;
	private final SkillIconManager skillIconManager;

	public Icons(ItemManager itemManager, SkillIconManager skillIconManager)
	{
		this.itemManager = itemManager;
		this.skillIconManager = skillIconManager;
	}

	/** A {@value #ITEM_SIZE}px item icon; a fixed-size blank placeholder when {@code id} is unknown or no {@link ItemManager} was given. */
	public JLabel item(Integer id)
	{
		JLabel label = new JLabel();
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		label.setPreferredSize(new Dimension(ITEM_SIZE, ITEM_SIZE));
		label.setMinimumSize(label.getPreferredSize());
		label.setMaximumSize(label.getPreferredSize());
		AsyncBufferedImage image = itemManager == null || id == null ? null : itemManager.getImage(id);
		if (image == null)
		{
			label.setName(PLACEHOLDER_NAME);
			return label;
		}
		label.setName(ITEM_ICON_NAME);
		label.setIcon(new ScaledIcon(image, ITEM_SIZE));
		image.onLoaded(label::repaint);
		return label;
	}

	/** The small skill icon, or a same-sized blank placeholder without a {@link SkillIconManager}. */
	public JLabel skill(Skill skill)
	{
		JLabel label = new JLabel();
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		if (skillIconManager == null)
		{
			label.setName(PLACEHOLDER_NAME);
			label.setPreferredSize(new Dimension(16, 16));
		}
		else
		{
			label.setIcon(new ImageIcon(skillIconManager.getSkillImage(skill, true)));
		}
		return label;
	}

	/**
	 * A quest marker: RuneLite's world-map quest icon for {@code state} (started / not started), or
	 * a text glyph when that resource isn't loadable. Cached per state, loaded lazily.
	 */
	public JLabel quest(QuestState state)
	{
		JLabel label = new JLabel();
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		BufferedImage image = questImage(state);
		if (image != null)
		{
			label.setIcon(new ImageIcon(image));
		}
		else
		{
			label.setText(QUEST_GLYPH);
			label.setForeground(categoryColor(GoalCategory.QUEST));
		}
		return label;
	}

	private static BufferedImage questStarted;
	private static BufferedImage questNotStarted;
	private static boolean questImagesTried;

	private static synchronized BufferedImage questImage(QuestState state)
	{
		if (!questImagesTried)
		{
			questImagesTried = true;
			try
			{
				questStarted = ImageUtil.loadImageResource(WorldMapPlugin.class, "quest_started_icon.png");
				questNotStarted = ImageUtil.loadImageResource(WorldMapPlugin.class, "quest_not_started_icon.png");
			}
			catch (RuntimeException e)
			{
				questStarted = null;
				questNotStarted = null;
			}
		}
		return state == QuestState.IN_PROGRESS ? questStarted : questNotStarted;
	}

	/** Makes {@code component} open {@code wikiUrl} in the browser on click (hand cursor, tooltip); a no-op for a null url. */
	public static void linkToWiki(JComponent component, String wikiUrl)
	{
		if (wikiUrl == null)
		{
			return;
		}
		component.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		component.setToolTipText("Open wiki");
		component.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				LinkBrowser.browse(wikiUrl);
			}
		});
	}

	/** The wiki page for an item known only by name (materials, method outputs). */
	public static String itemWikiUrl(String name)
	{
		return WikiUrls.forTitle(name);
	}

	/** The accent colour for a goal category: quest blue, diary teal, boss red, milestone/slayer gold, skill target green. */
	public static Color categoryColor(GoalCategory category)
	{
		switch (category)
		{
			case QUEST:
				return new Color(0x4C8BF5);
			case DIARY:
				return new Color(0x2BB5A0);
			case BOSS:
				return ColorScheme.PROGRESS_ERROR_COLOR;
			case SKILL_TARGET:
				return ColorScheme.PROGRESS_COMPLETE_COLOR;
			case MILESTONE:
			case SLAYER_TARGET:
			default:
				return new Color(0xD4AF37);
		}
	}

	/** {@code color} as an HTML hex literal for a wrapped label's markup. */
	public static String hex(Color color)
	{
		return String.format("#%06x", color.getRGB() & 0xFFFFFF);
	}

	/** Draws an item image scaled to fit a square, reading the (possibly still loading) image at paint time. */
	private static final class ScaledIcon implements Icon
	{
		private final BufferedImage image;
		private final int size;

		ScaledIcon(BufferedImage image, int size)
		{
			this.image = image;
			this.size = size;
		}

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y)
		{
			double scale = Math.min((double) size / image.getWidth(), (double) size / image.getHeight());
			int w = (int) Math.round(image.getWidth() * scale);
			int h = (int) Math.round(image.getHeight() * scale);
			g.drawImage(image, x + (size - w) / 2, y + (size - h) / 2, w, h, null);
		}

		@Override
		public int getIconWidth()
		{
			return size;
		}

		@Override
		public int getIconHeight()
		{
			return size;
		}
	}
}
