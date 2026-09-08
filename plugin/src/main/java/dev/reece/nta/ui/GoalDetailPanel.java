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
import dev.reece.nta.engine.model.PlanOffer;
import dev.reece.nta.engine.model.QuestPointsGap;
import dev.reece.nta.engine.model.QuestPrereqGap;
import dev.reece.nta.engine.model.Route;
import dev.reece.nta.engine.model.RouteStep;
import dev.reece.nta.engine.model.Shortfall;
import dev.reece.nta.engine.model.ShortfallItem;
import dev.reece.nta.engine.model.SkillLevelGap;
import dev.reece.nta.kb.GatheringAlternative;
import dev.reece.nta.kb.GatheringPlan;
import dev.reece.nta.kb.ItemQuantity;
import dev.reece.nta.kb.OwnedItem;
import dev.reece.nta.snapshot.DiaryTier;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
	private final JPanel explanationPanel = column();
	private final JPanel missingPanel = column();
	private final JPanel nextPanel = column();
	private final JPanel shortSection = column();
	private final JPanel shortPanel = column();

	private Advice currentAdvice;
	private String currentWikiUrl;
	// task 52b-2: which gathering plans' "Alternatives" list is expanded, keyed by shortfall item
	// name + plan id + plan title - never cleared, same persistence rule as SuggestPanel's Why?
	// toggle (52b-1).
	private final Set<String> expandedAlternatives = new HashSet<>();

	public GoalDetailPanel(Actions actions)
	{
		this.actions = actions;

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setAlignmentX(Component.LEFT_ALIGNMENT);

		JPanel titleRow = new NoStretchPanel();
		titleRow.setLayout(new BoxLayout(titleRow, BoxLayout.X_AXIS));
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

		explanationPanel.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
		add(explanationPanel);

		add(SuggestPanel.sectionLabel("Missing"));
		add(missingPanel);

		add(SuggestPanel.sectionLabel("Do this next"));
		add(nextPanel);

		shortSection.add(SuggestPanel.sectionLabel("Short"));
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

		explanationPanel.removeAll();
		for (String line : advice.getExplanations().getOrDefault(goal.getId(), List.of()))
		{
			explanationPanel.add(row(line, 0));
		}

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
		JPanel panel = column();

		NextStep next = focus.getNext();
		switch (next.getType())
		{
			case QUEST:
			{
				QuestPrereqGap g = next.getQuestGap();
				String verb = g.getState() == QuestState.IN_PROGRESS ? "Finish " : "Start ";
				JPanel questRow = borderRow();
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
		JPanel container = column();
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
		JPanel panel = column();
		for (ShortfallItem item : shortfall.getItems())
		{
			panel.add(shortfallItemRows(item, 0));
		}
		return panel;
	}

	private JPanel shortfallItemRows(ShortfallItem item, int indent)
	{
		JPanel container = column();
		int shortQty = Math.max(0, item.getNeed() - item.getHave());
		container.add(linkRow(item.getItem().getName() + ": have " + item.getHave() + ", need " + item.getNeed() + ", short " + shortQty,
			item.getWikiUrl(), indent));
		for (int i = 0; i < item.getPlans().size(); i++)
		{
			container.add(planOfferRows(item.getItem().getName(), item.getPlans().get(i), i == 0, indent + 1));
		}
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

	/**
	 * Task 52b-2, spec ruling 28: one {@link PlanOffer} under its shortfall item - a bold title
	 * linked to the wiki, numbered steps, the rate (when known), any unmet requirements, and a
	 * collapsed alternatives toggle. {@code first} is false for a later offer in the same item's
	 * {@link ShortfallItem#getPlans()} list, reached only through a craft-chain ingredient (e.g.
	 * Dragon scale dust's plan via Blue dragon scales) - captioned "via &lt;plan's own item&gt;".
	 */
	private JPanel planOfferRows(String shortfallItemName, PlanOffer offer, boolean first, int indent)
	{
		GatheringPlan plan = offer.getPlan();

		JPanel container = column();

		if (!first)
		{
			JLabel via = row("via " + plan.getItem(), indent);
			via.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			container.add(via);
		}

		container.add(boldLinkRow(plan.getTitle(), plan.getWikiUrl(), indent));

		int n = 1;
		for (String step : plan.getSteps())
		{
			container.add(row(n + ". " + step, indent + 1));
			n++;
		}

		if (plan.getRatePerHour() != null)
		{
			container.add(row("~" + plan.getRatePerHour() + "/h", indent + 1));
		}

		if (!offer.isMeetsRequirements())
		{
			JLabel requires = row("requires: " + String.join(", ", offer.getMissing()), indent + 1);
			requires.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			container.add(requires);
		}

		if (plan.getAlternatives() != null && !plan.getAlternatives().isEmpty())
		{
			String key = shortfallItemName + "|" + plan.getId() + "|" + plan.getTitle();
			container.add(alternativesToggle(key, plan.getAlternatives(), indent + 1));
		}

		return container;
	}

	/**
	 * Task 52b-2: a collapsed "Alternatives (n)" toggle; expanding it lists each alternative's
	 * title and numbered steps. Expansion persists per {@code key} across re-renders (same rule as
	 * {@code expandedAlternatives} above / SuggestPanel's Why? toggle) - clicking it re-renders the
	 * whole panel from {@link #currentAdvice} since, unlike the collapsible section headers, a
	 * fresh label is built on every render rather than one persistent label mutated in place.
	 */
	private JPanel alternativesToggle(String key, List<GatheringAlternative> alternatives, int indent)
	{
		JPanel panel = column();

		boolean expanded = expandedAlternatives.contains(key);
		JLabel toggle = new JLabel("Alternatives (" + alternatives.size() + ") " + (expanded ? "▼" : "▶"));
		toggle.setFont(FontManager.getRunescapeSmallFont());
		toggle.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		toggle.setAlignmentX(Component.LEFT_ALIGNMENT);
		toggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		toggle.setBorder(BorderFactory.createEmptyBorder(2, indent * 12, 2, 0));
		toggle.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (expandedAlternatives.contains(key))
				{
					expandedAlternatives.remove(key);
				}
				else
				{
					expandedAlternatives.add(key);
				}
				render(currentAdvice);
			}
		});
		panel.add(toggle);

		if (expanded)
		{
			for (GatheringAlternative alt : alternatives)
			{
				panel.add(row(alt.getTitle(), indent + 1));
				int n = 1;
				for (String step : alt.getSteps())
				{
					panel.add(row(n + ". " + step, indent + 2));
					n++;
				}
			}
		}

		return panel;
	}

	/** A {@link #row} with a "Wiki" button on the right when {@code wikiUrl} is known (ticket F3: methods and materials link to the wiki). */
	private static JPanel linkRow(String text, String wikiUrl, int indent)
	{
		JPanel row = borderRow();
		row.add(row(text, indent), BorderLayout.CENTER);
		if (wikiUrl != null)
		{
			row.add(SuggestPanel.button("Wiki", () -> LinkBrowser.browse(wikiUrl)), BorderLayout.EAST);
		}
		return row;
	}

	/** Task 52b-2: as {@link #linkRow}, but the text is a gathering plan's bold title. */
	private static JPanel boldLinkRow(String title, String wikiUrl, int indent)
	{
		JLabel titleLabel = new JLabel(SuggestPanel.wrap(title));
		titleLabel.setFont(FontManager.getRunescapeBoldFont());
		titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		titleLabel.setBorder(BorderFactory.createEmptyBorder(2, indent * 12, 2, 0));

		JPanel row = borderRow();
		row.add(titleLabel, BorderLayout.CENTER);
		if (wikiUrl != null)
		{
			row.add(SuggestPanel.button("Wiki", () -> LinkBrowser.browse(wikiUrl)), BorderLayout.EAST);
		}
		return row;
	}

	/** Task 49: a route step as two lines - the method and count on top, levels/xp below - with the "Wiki" button spanning both. */
	private static JPanel twoLineLinkRow(String line1, String line2, String wikiUrl, int indent)
	{
		JPanel text = column();
		text.add(row(line1, indent));
		text.add(row(line2, indent));

		JPanel row = borderRow();
		row.add(text, BorderLayout.CENTER);
		if (wikiUrl != null)
		{
			row.add(SuggestPanel.button("Wiki", () -> LinkBrowser.browse(wikiUrl)), BorderLayout.EAST);
		}
		return row;
	}

	/**
	 * Task 57: a panel whose maximum height is always its preferred height, so a parent
	 * {@link BoxLayout} with spare vertical room never stretches it (the whitespace between sections
	 * the user saw). {@link JLabel} already behaves this way; a plain {@link JPanel} does not.
	 */
	private static class NoStretchPanel extends JPanel
	{
		NoStretchPanel()
		{
			setAlignmentX(Component.LEFT_ALIGNMENT);
		}

		NoStretchPanel(LayoutManager layout)
		{
			super(layout);
			setAlignmentX(Component.LEFT_ALIGNMENT);
		}

		@Override
		public Dimension getMaximumSize()
		{
			return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
		}
	}

	/** A left-aligned vertical stack that never stretches. */
	static JPanel column()
	{
		JPanel panel = new NoStretchPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		return panel;
	}

	/** A left-aligned {@link BorderLayout}{@code (4, 0)} row that never stretches. */
	static JPanel borderRow()
	{
		return new NoStretchPanel(new BorderLayout(4, 0));
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
