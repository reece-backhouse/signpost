package dev.reece.nta.ui;

import dev.reece.nta.engine.model.Advice;
import dev.reece.nta.engine.model.CombatLevelGap;
import dev.reece.nta.engine.model.DiaryTaskGap;
import dev.reece.nta.engine.model.DiaryTierGap;
import dev.reece.nta.engine.model.FocusDetail;
import dev.reece.nta.engine.model.Gap;
import dev.reece.nta.engine.model.GearGap;
import dev.reece.nta.engine.model.Goal;
import dev.reece.nta.engine.model.GoalStatus;
import dev.reece.nta.engine.model.ItemGap;
import dev.reece.nta.engine.model.ItemSource;
import dev.reece.nta.engine.model.KudosGap;
import dev.reece.nta.engine.model.NextStep;
import dev.reece.nta.engine.model.QuestPointsGap;
import dev.reece.nta.engine.model.QuestPrereqGap;
import dev.reece.nta.engine.model.Route;
import dev.reece.nta.engine.model.RouteStep;
import dev.reece.nta.engine.model.Shortfall;
import dev.reece.nta.engine.model.ShortfallItem;
import dev.reece.nta.engine.model.SkillLevelGap;
import dev.reece.nta.kb.ItemQuantity;
import dev.reece.nta.kb.OwnedItem;
import dev.reece.nta.snapshot.DiaryTier;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import lombok.Value;
import net.runelite.api.QuestState;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.LinkBrowser;

/**
 * Goal detail view (task 39, spec ruling 26): shown by {@link NextTargetPanel} instead of
 * {@link SuggestPanel} whenever {@link Advice#getFocus()} is non-null - the drill-down from a
 * Suggest card's "Do this", or from the persistent search box {@link NextTargetPanel} hosts above
 * both modes (task 47, see {@link GoalSearchField}). Renders {@link Advice} only; every button
 * calls back into a plugin action ({@link Actions}), except the "Wiki" buttons which open the
 * system browser directly via {@link LinkBrowser} (a pure UI action, not a mutation).
 */
public class GoalDetailPanel extends JPanel
{
	private final Actions actions;

	private final JLabel titleLabel = new JLabel();
	private final JLabel categoryStageLabel = new JLabel();
	private final JButton wikiButton = new JButton("Wiki");
	private final JLabel whyLabel = new JLabel();
	private final JLabel reasonLabel = new JLabel();
	private final JPanel missingPanel = new JPanel();
	private final JPanel nextPanel = new JPanel();
	private final JPanel shortSection = new JPanel();
	private final JPanel shortPanel = new JPanel();

	private Advice currentAdvice;
	private String currentWikiUrl;

	public GoalDetailPanel(Actions actions)
	{
		this.actions = actions;

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setAlignmentX(Component.LEFT_ALIGNMENT);

		JPanel titleRow = new JPanel();
		titleRow.setLayout(new BoxLayout(titleRow, BoxLayout.X_AXIS));
		titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		titleLabel.setFont(FontManager.getRunescapeBoldFont());
		titleRow.add(titleLabel);
		titleRow.add(javax.swing.Box.createHorizontalGlue());
		wikiButton.setMargin(new Insets(2, 4, 2, 4));
		wikiButton.setFocusPainted(false);
		wikiButton.addActionListener(e ->
		{
			if (currentWikiUrl != null)
			{
				LinkBrowser.browse(currentWikiUrl);
			}
		});
		titleRow.add(wikiButton);
		titleRow.add(SuggestPanel.button("Back", actions.getClearFocus()));
		add(titleRow);

		categoryStageLabel.setFont(FontManager.getRunescapeSmallFont());
		categoryStageLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		categoryStageLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		add(categoryStageLabel);
		add(javax.swing.Box.createVerticalStrut(4));

		whyLabel.setFont(FontManager.getRunescapeFont());
		whyLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		add(whyLabel);
		reasonLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.ITALIC));
		reasonLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		add(reasonLabel);

		add(SuggestPanel.sectionLabel("Missing"));
		missingPanel.setLayout(new BoxLayout(missingPanel, BoxLayout.Y_AXIS));
		missingPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		add(missingPanel);

		add(SuggestPanel.sectionLabel("Do this next"));
		nextPanel.setLayout(new BoxLayout(nextPanel, BoxLayout.Y_AXIS));
		nextPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		add(nextPanel);

		shortSection.setLayout(new BoxLayout(shortSection, BoxLayout.Y_AXIS));
		shortSection.setAlignmentX(Component.LEFT_ALIGNMENT);
		shortSection.add(SuggestPanel.sectionLabel("Short"));
		shortPanel.setLayout(new BoxLayout(shortPanel, BoxLayout.Y_AXIS));
		shortPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		shortSection.add(shortPanel);
		add(shortSection);
	}

	/** Renders {@code advice}. Must be called on the EDT. Requires {@code advice.getFocus() != null}. */
	public void render(Advice advice)
	{
		if (!SwingUtilities.isEventDispatchThread())
		{
			throw new IllegalStateException("GoalDetailPanel.render must run on the EDT");
		}
		if (advice.getFocus() == null)
		{
			throw new IllegalStateException("GoalDetailPanel.render requires advice.getFocus() != null");
		}

		this.currentAdvice = advice;
		FocusDetail focus = advice.getFocus();
		GoalStatus status = focus.getStatus();
		Goal goal = status.getGoal();

		titleLabel.setText(goal.getName());
		categoryStageLabel.setText(SuggestPanel.categoryLabel(goal.getCategory()) + " — stage " + goal.getStage());
		currentWikiUrl = goal.getWikiUrl();

		whyLabel.setText(SuggestPanel.wrap(advice.getWhys().getOrDefault(goal.getId(), "")));
		String reason = advice.getReasons().get(goal.getId());
		reasonLabel.setVisible(reason != null && !reason.isEmpty());
		reasonLabel.setText(reason == null ? "" : SuggestPanel.wrap(SuggestPanel.truncateReason(reason)));

		missingPanel.removeAll();
		for (Gap gap : status.getGaps())
		{
			addGapRows(gap, 0);
		}
		for (String note : status.getNotes())
		{
			missingPanel.add(row("Note: " + note, 0));
		}

		nextPanel.removeAll();
		nextPanel.add(nextStepContent(focus));

		shortSection.setVisible(focus.getShortfall() != null);
		shortPanel.removeAll();
		if (focus.getShortfall() != null)
		{
			shortPanel.add(shortfallContent(focus.getShortfall()));
		}

		revalidate();
		repaint();
	}

	// --- Missing (ticket E2, reduced to unmet items per user ruling) ---

	private void addGapRows(Gap gap, int indent)
	{
		if (gap instanceof DiaryTaskGap)
		{
			DiaryTaskGap g = (DiaryTaskGap) gap;
			missingPanel.add(row(g.getOrdinal() + ". " + g.getText(), indent));
			for (Gap inner : g.getGaps())
			{
				addGapRows(inner, indent + 1);
			}
			for (String note : g.getNotes())
			{
				missingPanel.add(row("Note: " + note, indent + 1));
			}
			return;
		}
		if (gap instanceof ItemGap)
		{
			missingPanel.add(linkRow(gapText(gap), ((ItemGap) gap).getWikiUrl(), indent));
			return;
		}
		missingPanel.add(row(gapText(gap), indent));
	}

	private static String gapText(Gap gap)
	{
		if (gap instanceof SkillLevelGap)
		{
			return skillGapText((SkillLevelGap) gap);
		}
		if (gap instanceof QuestPrereqGap)
		{
			return questGapText((QuestPrereqGap) gap);
		}
		if (gap instanceof ItemGap)
		{
			return itemGapText((ItemGap) gap);
		}
		if (gap instanceof QuestPointsGap)
		{
			QuestPointsGap g = (QuestPointsGap) gap;
			return "Quest points " + g.getHave() + "/" + g.getNeed();
		}
		if (gap instanceof KudosGap)
		{
			KudosGap g = (KudosGap) gap;
			return "Kudos " + g.getHave() + "/" + g.getNeed();
		}
		if (gap instanceof CombatLevelGap)
		{
			CombatLevelGap g = (CombatLevelGap) gap;
			return "Combat level " + g.getHave() + "/" + g.getNeed() + (g.isRecommended() ? " (recommended)" : "");
		}
		if (gap instanceof DiaryTierGap)
		{
			return tierName(((DiaryTierGap) gap).getTier()) + " Diary not complete";
		}
		if (gap instanceof GearGap)
		{
			return gearGapText((GearGap) gap);
		}
		return gap.getClass().getSimpleName();
	}

	private static String skillGapText(SkillLevelGap g)
	{
		StringBuilder sb = new StringBuilder(g.getSkill().getName()).append(' ').append(g.getHave()).append('/').append(g.getNeed());
		if (g.getBoostableFrom() != null)
		{
			sb.append(" (boostable from ").append(g.getBoostableFrom()).append(')');
		}
		if (g.isRecommended())
		{
			sb.append(" (recommended)");
		}
		return sb.toString();
	}

	private static String questGapText(QuestPrereqGap g)
	{
		if (g.isStartOnly())
		{
			return "start " + g.getQuest().getName();
		}
		String state = g.getState() == QuestState.IN_PROGRESS ? "in progress" : "not started";
		return g.getQuest().getName() + " — " + state + (g.isStartHere() ? " (start here)" : "");
	}

	private static String itemGapText(ItemGap g)
	{
		String have = g.getHave() == null ? "?" : String.valueOf(g.getHave());
		return g.getName() + " " + have + "/" + g.getNeed() + (g.isMustObtain() ? " (iron: must obtain — see wiki)" : "");
	}

	private static String gearGapText(GearGap g)
	{
		String names = g.getAcceptable().stream().map(OwnedItem::getName).collect(Collectors.joining(", "));
		return "Gear: any of " + names + " (recommended)";
	}

	private static String tierName(DiaryTier tier)
	{
		String[] parts = tier.name().split("_");
		StringBuilder sb = new StringBuilder();
		for (String part : parts)
		{
			if (sb.length() > 0)
			{
				sb.append(' ');
			}
			sb.append(part.charAt(0)).append(part.substring(1).toLowerCase());
		}
		return sb.toString();
	}

	// --- Do this next (ticket E3/E4/E5) ---

	private JPanel nextStepContent(FocusDetail focus)
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);

		NextStep next = focus.getNext();
		switch (next.getType())
		{
			case QUEST:
			{
				QuestPrereqGap g = next.getQuestGap();
				String verb = g.getState() == QuestState.IN_PROGRESS ? "Finish " : "Start ";
				JPanel questRow = new JPanel(new BorderLayout(4, 0));
				questRow.setAlignmentX(Component.LEFT_ALIGNMENT);
				questRow.add(new JLabel(verb + g.getQuest().getName()), BorderLayout.CENTER);
				questRow.add(SuggestPanel.button("Wiki", () -> LinkBrowser.browse(g.getWikiUrl())), BorderLayout.EAST);
				panel.add(questRow);
				break;
			}
			case SKILL:
			{
				SkillLevelGap g = next.getSkillGap();
				panel.add(row("Train " + g.getSkill().getName() + " " + focus.getFromLevel() + "→" + focus.getToLevel(), 0));
				Route route = focus.getRoute();
				if (route != null)
				{
					for (RouteStep step : route.getSteps())
					{
						panel.add(routeStepRows(step, 0));
					}
				}
				break;
			}
			case ITEM:
			{
				ItemGap g = next.getItemGap();
				panel.add(linkRow("Get " + g.getName() + " ×" + g.getNeed(), g.getWikiUrl(), 0));
				for (ItemSource source : g.getSources())
				{
					panel.add(row(sourceText(source), 1));
				}
				break;
			}
			case NONE:
			default:
				panel.add(row("Nothing left — ready to do", 0));
		}
		return panel;
	}

	private JPanel routeStepRows(RouteStep step, int indent)
	{
		JPanel container = new JPanel();
		container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
		container.setAlignmentX(Component.LEFT_ALIGNMENT);
		container.add(twoLineLinkRow(
			step.getMethod().getName() + " ×" + step.getCount(),
			step.getFromLevel() + "→" + step.getToLevel() + ", +" + step.getXpGained() + " xp",
			step.getMethod().wikiUrl(), indent));
		for (RouteStep craft : step.getCrafts())
		{
			container.add(linkRow("craft " + craft.getMethod().getName() + " ×" + craft.getCount() + " from " + materialsList(craft),
				craft.getMethod().wikiUrl(), indent + 1));
		}
		return container;
	}

	private static String materialsList(RouteStep step)
	{
		Map<Integer, String> names = new LinkedHashMap<>();
		for (ItemQuantity material : step.getMethod().getMaterials())
		{
			names.put(material.getId(), material.getName());
		}
		List<String> parts = new ArrayList<>();
		for (Map.Entry<Integer, Integer> used : step.getMaterialsUsed().entrySet())
		{
			parts.add(names.getOrDefault(used.getKey(), "item " + used.getKey()) + " ×" + used.getValue());
		}
		return String.join(", ", parts);
	}

	private static String sourceText(ItemSource source)
	{
		StringBuilder sb = new StringBuilder(source.getType()).append(": ").append(source.getWhere());
		if (source.getDetail() != null && !source.getDetail().isEmpty())
		{
			sb.append(' ').append(source.getDetail());
		}
		return sb.toString();
	}

	// --- Short (ticket C7/E4) ---

	private JPanel shortfallContent(Shortfall shortfall)
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		for (ShortfallItem item : shortfall.getItems())
		{
			panel.add(shortfallItemRows(item, 0));
		}
		return panel;
	}

	private JPanel shortfallItemRows(ShortfallItem item, int indent)
	{
		JPanel container = new JPanel();
		container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
		container.setAlignmentX(Component.LEFT_ALIGNMENT);
		int shortQty = Math.max(0, item.getNeed() - item.getHave());
		container.add(linkRow(item.getItem().getName() + ": have " + item.getHave() + ", need " + item.getNeed() + ", short " + shortQty,
			item.getWikiUrl(), indent));
		for (ItemSource source : item.getSources())
		{
			container.add(row(sourceText(source), indent, 8));
		}
		for (ShortfallItem craftFrom : item.getCraftFrom())
		{
			container.add(shortfallItemRows(craftFrom, indent + 1));
		}
		return container;
	}

	/** A {@link #row} with a "Wiki" button on the right when {@code wikiUrl} is known (ticket F3: methods and materials link to the wiki). */
	private static JPanel linkRow(String text, String wikiUrl, int indent)
	{
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.add(row(text, indent), BorderLayout.CENTER);
		if (wikiUrl != null)
		{
			row.add(SuggestPanel.button("Wiki", () -> LinkBrowser.browse(wikiUrl)), BorderLayout.EAST);
		}
		return row;
	}

	/** Task 49: a route step as two lines - the method and count on top, levels/xp below - with the "Wiki" button spanning both. */
	private static JPanel twoLineLinkRow(String line1, String line2, String wikiUrl, int indent)
	{
		JPanel text = new JPanel();
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.setAlignmentX(Component.LEFT_ALIGNMENT);
		text.add(row(line1, indent));
		text.add(row(line2, indent));

		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.add(text, BorderLayout.CENTER);
		if (wikiUrl != null)
		{
			row.add(SuggestPanel.button("Wiki", () -> LinkBrowser.browse(wikiUrl)), BorderLayout.EAST);
		}
		return row;
	}

	private static JLabel row(String text, int indent)
	{
		return row(text, indent, 0);
	}

	/** {@code extraPx}: an additional left indent beyond the usual 12px/level, e.g. the 8px shortfall sources get under their item. */
	private static JLabel row(String text, int indent, int extraPx)
	{
		JLabel label = new JLabel(SuggestPanel.wrap(text));
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		label.setBorder(BorderFactory.createEmptyBorder(2, indent * 12 + extraPx, 2, 0));
		return label;
	}

	/**
	 * The plugin action methods a button here calls into: {@code clearFocus} for "Back". {@code focus}
	 * is unused by this panel directly - {@link NextTargetPanel} reuses it to wire up the persistent
	 * {@link GoalSearchField} it hosts above both modes (task 47) - but stays part of this shape since
	 * the plugin constructs both from the same pair of methods.
	 */
	@Value
	public static class Actions
	{
		Consumer<String> focus;
		Runnable clearFocus;
	}
}
