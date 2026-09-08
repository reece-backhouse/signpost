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
import dev.reece.nta.engine.model.Met;
import dev.reece.nta.engine.model.NextStep;
import dev.reece.nta.engine.model.PlanOffer;
import dev.reece.nta.engine.model.QuestPointsGap;
import dev.reece.nta.engine.model.QuestPrereqGap;
import dev.reece.nta.engine.model.Route;
import dev.reece.nta.engine.model.RouteStep;
import dev.reece.nta.engine.model.ShortfallItem;
import dev.reece.nta.engine.model.SkillLevelGap;
import dev.reece.nta.engine.model.SkillPlan;
import dev.reece.nta.kb.GatheringAlternative;
import dev.reece.nta.kb.GatheringPlan;
import dev.reece.nta.kb.ItemQuantity;
import dev.reece.nta.kb.OwnedItem;
import dev.reece.nta.snapshot.DiaryTier;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import lombok.Value;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.LinkBrowser;

/**
 * Goal detail view (task 39, spec ruling 26): shown by {@link NextTargetPanel} instead of
 * {@link SuggestPanel} whenever {@link Advice#getFocus()} is non-null - the drill-down from a
 * Suggest card's "Do this", or from the persistent search box {@link NextTargetPanel} hosts above
 * both modes (task 47, see {@link GoalSearchField}). Renders {@link Advice} only; the "Back" button
 * calls back into a plugin action ({@link Actions}), the goal's "Wiki" button and every item icon /
 * name open the system browser directly via {@link LinkBrowser} (a pure UI action, not a mutation).
 *
 * <p>Task 57: every skill gap under "Missing" is a clickable row (icon, "Herblore 61/70", a thin
 * progress bar, a green "materials in bank" badge when its {@link SkillPlan} is covered) that
 * expands into that skill's route - steps with their output and material icons - and, when the
 * bank doesn't cover it, the shortfall with gathering plans. One skill is expanded at a time; the
 * {@link FocusDetail#getNextSkillPlan()} starts expanded when the focused goal changes. The old
 * separate "Short" section is gone: the shortfall lives under its skill.
 */
public class GoalDetailPanel extends JPanel
{
	private static final int INDENT_PX = 12;
	/** The {@link BorderLayout} hgap between an {@link #iconRow}'s icon column and its text. */
	private static final int ICON_GAP = 4;
	/** Component-name prefix of a skill row's label ("skill-row:HERBLORE"), for tests to click. */
	public static final String SKILL_ROW_NAME = "skill-row:";

	private final Actions actions;
	private final Icons icons;

	private final JLabel titleLabel = new JLabel();
	private final JLabel categoryStageLabel = new JLabel();
	private final JButton wikiButton = new JButton("Wiki");
	// task 59: the title wraps to the room left beside the Wiki/Back buttons, never past the edge
	private final int titleWidth;
	private final JLabel whyLabel = new JLabel();
	private final JLabel reasonLabel = new JLabel();
	private final JPanel explanationPanel = column();
	private final JPanel missingPanel = column();
	private final JPanel nextPanel = column();

	private Advice currentAdvice;
	private String currentWikiUrl;
	private String lastGoalId;
	// task 57: the one skill row whose route is open; reset to the next skill plan's skill whenever
	// the focused goal changes, otherwise kept across re-renders (same rule as the toggles below).
	private Skill expandedSkill;
	private boolean metExpanded;
	// task 52b-2: which gathering plans' "Alternatives" list is expanded, keyed by shortfall item
	// name + plan id + plan title - never cleared, same persistence rule as SuggestPanel's Why?
	// toggle (52b-1).
	private final Set<String> expandedAlternatives = new HashSet<>();

	public GoalDetailPanel(Actions actions, Icons icons)
	{
		this.actions = actions;
		this.icons = icons;

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setAlignmentX(Component.LEFT_ALIGNMENT);

		JPanel titleRow = new NoStretchPanel();
		titleRow.setLayout(new BoxLayout(titleRow, BoxLayout.X_AXIS));
		titleLabel.setFont(FontManager.getRunescapeBoldFont());
		titleRow.add(titleLabel);
		titleRow.add(Box.createHorizontalGlue());
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
		JButton backButton = SuggestPanel.button("Back", actions.getClearFocus());
		titleRow.add(backButton);
		add(titleRow);
		titleWidth = SuggestPanel.WRAP_WIDTH - wikiButton.getPreferredSize().width - backButton.getPreferredSize().width - 2 * ICON_GAP;

		categoryStageLabel.setFont(FontManager.getRunescapeSmallFont());
		categoryStageLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		add(categoryStageLabel);
		add(Box.createVerticalStrut(4));

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

		if (!goal.getId().equals(lastGoalId))
		{
			lastGoalId = goal.getId();
			expandedSkill = focus.getNextSkillPlan() == null ? null : focus.getNextSkillPlan().getSkill();
			metExpanded = false;
		}

		titleLabel.setText(SuggestPanel.wrap(goal.getName(), titleWidth));
		categoryStageLabel.setText(SuggestPanel.categoryLabel(goal.getCategory()) + " — stage " + goal.getStage());
		categoryStageLabel.setForeground(Icons.categoryColor(goal.getCategory()));
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

		Map<Skill, SkillPlan> plans = new EnumMap<>(Skill.class);
		for (SkillPlan plan : focus.getSkillPlans())
		{
			plans.put(plan.getSkill(), plan);
		}

		missingPanel.removeAll();
		for (Gap gap : status.getGaps())
		{
			addGapRows(gap, 0, plans);
		}
		for (String note : status.getNotes())
		{
			missingPanel.add(row("Note: " + note, 0));
		}
		if (!status.getMet().isEmpty())
		{
			missingPanel.add(metToggle(status.getMet()));
		}

		nextPanel.removeAll();
		nextPanel.add(nextStepContent(focus));

		revalidate();
		repaint();
	}

	// --- Missing (ticket E2, reduced to unmet items per user ruling) ---

	private void addGapRows(Gap gap, int indent, Map<Skill, SkillPlan> plans)
	{
		if (gap instanceof DiaryTaskGap)
		{
			DiaryTaskGap g = (DiaryTaskGap) gap;
			missingPanel.add(row(g.getOrdinal() + ". " + g.getText(), indent));
			for (Gap inner : g.getGaps())
			{
				addGapRows(inner, indent + 1, plans);
			}
			for (String note : g.getNotes())
			{
				missingPanel.add(row("Note: " + note, indent + 1));
			}
			return;
		}
		if (gap instanceof SkillLevelGap)
		{
			SkillLevelGap g = (SkillLevelGap) gap;
			SkillPlan plan = plans.get(g.getSkill());
			if (plan != null)
			{
				missingPanel.add(skillRow(g, plan, indent));
				return;
			}
		}
		if (gap instanceof QuestPrereqGap)
		{
			QuestPrereqGap g = (QuestPrereqGap) gap;
			JLabel icon = icons.quest(g.getState());
			JLabel text = label(questGapText(g), textWidth(indent, icon, null), gapColor(gap));
			Icons.linkToWiki(text, g.getWikiUrl());
			missingPanel.add(iconRow(icon, text, null, indent));
			return;
		}
		if (gap instanceof GearGap)
		{
			GearGap g = (GearGap) gap;
			missingPanel.add(row("Gear: any of", indent, gapColor(gap)));
			for (OwnedItem item : g.getAcceptable())
			{
				JLabel icon = icons.item(item.getId());
				JLabel name = htmlLabel("<span style='color:" + Icons.hex(gapColor(gap)) + "'>" + SuggestPanel.escape(item.getName()) + "</span>" + recommendedTag(),
					textWidth(indent + 1, icon, null));
				Icons.linkToWiki(icon, Icons.itemWikiUrl(item.getName()));
				Icons.linkToWiki(name, Icons.itemWikiUrl(item.getName()));
				missingPanel.add(iconRow(icon, name, null, indent + 1));
			}
			return;
		}
		if (gap instanceof ItemGap && ((ItemGap) gap).getItemId() != null)
		{
			ItemGap g = (ItemGap) gap;
			JLabel icon = icons.item(g.getItemId());
			JLabel itemText = label(gapText(gap), textWidth(indent, icon, null), gapColor(gap));
			Icons.linkToWiki(icon, g.getWikiUrl());
			Icons.linkToWiki(itemText, g.getWikiUrl());
			missingPanel.add(iconRow(icon, itemText, null, indent));
			return;
		}
		JLabel text = row(gapText(gap), indent, gapColor(gap));
		if (gap instanceof ItemGap)
		{
			Icons.linkToWiki(text, ((ItemGap) gap).getWikiUrl());
		}
		missingPanel.add(text);
	}

	/** Red for a hard requirement, orange for a milestone's recommended profile (spec ruling 27). */
	private static Color gapColor(Gap gap)
	{
		boolean recommended = gap instanceof SkillLevelGap && ((SkillLevelGap) gap).isRecommended()
			|| gap instanceof CombatLevelGap && ((CombatLevelGap) gap).isRecommended()
			|| gap instanceof GearGap;
		return recommended ? ColorScheme.PROGRESS_INPROGRESS_COLOR : ColorScheme.PROGRESS_ERROR_COLOR;
	}

	private static String recommendedTag()
	{
		return " <span style='color:" + Icons.hex(ColorScheme.BRAND_ORANGE) + "'>(recommended)</span>";
	}

	/**
	 * Task 57: one skill gap as a clickable row - skill icon, "Herblore 61/70" (plus boostable /
	 * recommended tags), a "materials in bank" badge when {@code plan.covered}, an expand arrow and
	 * a thin level progress bar - that toggles {@link #expandedSkill} and, when open, shows
	 * {@link #skillPlanContent}. The listener is on every child (AWT delivers a click to the
	 * deepest component under the cursor only - see SuggestPanel.Header).
	 */
	private JPanel skillRow(SkillLevelGap g, SkillPlan plan, int indent)
	{
		boolean expanded = plan.getSkill() == expandedSkill;
		Color color = plan.isCovered() ? ColorScheme.PROGRESS_COMPLETE_COLOR : gapColor(g);

		JPanel container = column();

		StringBuilder text = new StringBuilder("<span style='color:" + Icons.hex(color) + "'>")
			.append(g.getSkill().getName()).append(' ').append(g.getHave()).append('/').append(g.getNeed()).append("</span>");
		if (g.getBoostableFrom() != null)
		{
			text.append(" <span style='color:" + Icons.hex(ColorScheme.LIGHT_GRAY_COLOR) + "'>(boostable from ").append(g.getBoostableFrom()).append(")</span>");
		}
		if (g.isRecommended())
		{
			text.append(recommendedTag());
		}
		JPanel east = new NoStretchPanel();
		east.setLayout(new BoxLayout(east, BoxLayout.X_AXIS));
		if (plan.isCovered())
		{
			JLabel badge = new JLabel("materials in bank");
			badge.setFont(FontManager.getRunescapeSmallFont());
			badge.setForeground(ColorScheme.PROGRESS_COMPLETE_COLOR);
			east.add(badge);
			east.add(Box.createHorizontalStrut(6));
		}
		JLabel arrow = new JLabel(expanded ? "▼" : "▶");
		arrow.setFont(FontManager.getRunescapeSmallFont());
		arrow.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		east.add(arrow);

		JLabel icon = icons.skill(g.getSkill());
		JLabel label = htmlLabel(text.toString(), textWidth(indent, icon, east) - 4);
		label.setName(SKILL_ROW_NAME + g.getSkill().name());
		label.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 0));
		JPanel head = iconRow(icon, label, east, indent);

		ProgressBar bar = new ProgressBar(g.getHave() / (double) g.getNeed(), color);
		bar.setBorder(BorderFactory.createEmptyBorder(0, indent * INDENT_PX, 4, 0));

		Runnable toggle = () ->
		{
			expandedSkill = expanded ? null : plan.getSkill();
			render(currentAdvice);
		};
		clickable(toggle, head, icon, label, east, arrow, bar);
		for (Component child : east.getComponents())
		{
			if (child instanceof JComponent)
			{
				clickable(toggle, (JComponent) child);
			}
		}

		container.add(head);
		container.add(bar);
		if (expanded)
		{
			container.add(skillPlanContent(plan, indent + 1));
		}
		return container;
	}

	/** The open skill row's body: route steps from the bank, then the shortfall (with gathering plans) if the bank doesn't reach the target. */
	private JPanel skillPlanContent(SkillPlan plan, int indent)
	{
		JPanel panel = column();
		Route route = plan.getRoute();
		boolean anySteps = route != null && !route.getSteps().isEmpty();
		if (anySteps)
		{
			for (RouteStep step : route.getSteps())
			{
				panel.add(stepRows(step, "", indent));
			}
		}
		if (plan.getShortfall() != null)
		{
			String method = plan.getShortfall().getMethod() == null ? "" : " for " + plan.getShortfall().getMethod().getName();
			JLabel still = row((anySteps ? "Then still short" : "Short") + method + ":", indent);
			still.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			panel.add(still);
			for (ShortfallItem item : plan.getShortfall().getItems())
			{
				panel.add(shortfallItemRows(item, indent));
			}
		}
		else if (!anySteps)
		{
			JLabel none = row("No training method known — see the wiki", indent);
			none.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			panel.add(none);
		}
		return panel;
	}

	/** Task 57: a collapsed "Met (n)" toggle under the gaps; expanded, each satisfied requirement in green (spec ruling 29). */
	private JPanel metToggle(List<Met> met)
	{
		JPanel panel = column();
		JLabel toggle = toggleLabel("Met (" + met.size() + ") " + (metExpanded ? "▼" : "▶"), 0);
		clickable(() ->
		{
			metExpanded = !metExpanded;
			render(currentAdvice);
		}, toggle);
		panel.add(toggle);
		if (metExpanded)
		{
			for (Met m : met)
			{
				panel.add(row("✓ " + m.getLabel(), 1, ColorScheme.PROGRESS_COMPLETE_COLOR));
			}
		}
		return panel;
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
				JLabel icon = icons.quest(g.getState());
				JLabel text = label(verb + g.getQuest().getName(), textWidth(0, icon, null));
				Icons.linkToWiki(text, g.getWikiUrl());
				panel.add(iconRow(icon, text, null, 0));
				break;
			}
			case SKILL:
			{
				// task 57: the route itself lives under the skill's row in "Missing"; this line
				// re-opens that row when clicked.
				SkillLevelGap g = next.getSkillGap();
				JLabel icon = icons.skill(g.getSkill());
				JLabel text = label("Train " + g.getSkill().getName() + " " + focus.getFromLevel() + "→" + focus.getToLevel() + " (route above)",
					textWidth(0, icon, null));
				JPanel head = iconRow(icon, text, null, 0);
				clickable(() ->
				{
					expandedSkill = g.getSkill();
					render(currentAdvice);
				}, head, icon, text);
				panel.add(head);
				break;
			}
			case ITEM:
			{
				ItemGap g = next.getItemGap();
				JLabel text = row("Get " + g.getName() + " ×" + g.getNeed(), 0);
				Icons.linkToWiki(text, g.getWikiUrl());
				panel.add(text);
				for (ItemSource source : g.getSources())
				{
					panel.add(sourceRow(source, 1));
				}
				break;
			}
			case NONE:
			default:
				panel.add(row("Nothing left — ready to do", 0, ColorScheme.PROGRESS_COMPLETE_COLOR));
		}
		return panel;
	}

	/**
	 * A route step (task 49, now task 57 with icons): the OUTPUT item's icon on the left, "Prayer
	 * potion(3) ×340" over "61→66, +29,750 xp" (or, for a 0-xp craft, "from Ranarr weed ×340, ..."),
	 * and the materials used as a strip of icons on the right (tooltip = name and count). Icons and
	 * names open the wiki. Crafts nest one level deeper with a "craft " prefix.
	 */
	private JPanel stepRows(RouteStep step, String prefix, int indent)
	{
		JPanel container = column();
		ItemQuantity output = firstWithId(step.getMethod().getOutputs());
		JLabel icon = icons.item(output == null ? null : output.getId());
		Icons.linkToWiki(icon, output == null ? step.getMethod().wikiUrl() : Icons.itemWikiUrl(output.getName()));

		JPanel materials = materialIcons(step);
		int width = textWidth(indent, icon, materials);
		JPanel text = column();
		JLabel line1 = label(prefix + step.getMethod().getName() + " ×" + thousands(step.getCount()), width);
		Icons.linkToWiki(line1, step.getMethod().wikiUrl());
		text.add(line1);
		String line2 = step.getXpGained() == 0
			? "from " + materialsList(step)
			: step.getFromLevel() + "→" + step.getToLevel() + ", +" + thousands(step.getXpGained()) + " xp";
		JLabel line2Label = label(line2, width);
		line2Label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		text.add(line2Label);

		container.add(iconRow(icon, text, materials, indent));
		for (RouteStep craft : step.getCrafts())
		{
			container.add(stepRows(craft, "craft ", indent + 1));
		}
		return container;
	}

	private JPanel materialIcons(RouteStep step)
	{
		Map<Integer, ItemQuantity> byId = new LinkedHashMap<>();
		for (ItemQuantity material : step.getMethod().getMaterials())
		{
			if (material.getId() != null)
			{
				byId.put(material.getId(), material);
			}
		}
		JPanel strip = new NoStretchPanel();
		strip.setLayout(new BoxLayout(strip, BoxLayout.X_AXIS));
		for (Map.Entry<Integer, Integer> used : step.getMaterialsUsed().entrySet())
		{
			ItemQuantity material = byId.get(used.getKey());
			String name = material == null ? "item " + used.getKey() : material.getName();
			JLabel icon = icons.item(used.getKey());
			Icons.linkToWiki(icon, Icons.itemWikiUrl(name));
			icon.setToolTipText(name + " ×" + used.getValue());
			strip.add(icon);
		}
		return strip;
	}

	private static ItemQuantity firstWithId(List<ItemQuantity> items)
	{
		for (ItemQuantity item : items)
		{
			if (item.getId() != null)
			{
				return item;
			}
		}
		return null;
	}

	/** "29,750" - always comma-grouped, whatever the JVM's default locale. */
	private static String thousands(long n)
	{
		return String.format(Locale.ENGLISH, "%,d", n);
	}

	private static String materialsList(RouteStep step)
	{
		Map<Integer, String> names = new LinkedHashMap<>();
		for (ItemQuantity material : step.getMethod().getMaterials())
		{
			names.put(material.getId(), material.getName());
		}
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<Integer, Integer> used : step.getMaterialsUsed().entrySet())
		{
			if (sb.length() > 0)
			{
				sb.append(", ");
			}
			sb.append(names.getOrDefault(used.getKey(), "item " + used.getKey())).append(" ×").append(used.getValue());
		}
		return sb.toString();
	}

	private static JLabel sourceRow(ItemSource source, int indent)
	{
		StringBuilder sb = new StringBuilder(source.getType()).append(": ").append(source.getWhere());
		if (source.getDetail() != null && !source.getDetail().isEmpty())
		{
			sb.append(' ').append(source.getDetail());
		}
		JLabel label = row(sb.toString(), indent);
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		return label;
	}

	// --- Shortfall (ticket C7/E4), now under its skill row (task 57) ---

	private JPanel shortfallItemRows(ShortfallItem item, int indent)
	{
		JPanel container = column();
		int shortQty = Math.max(0, item.getNeed() - item.getHave());
		String wikiUrl = item.getWikiUrl() != null ? item.getWikiUrl() : Icons.itemWikiUrl(item.getItem().getName());

		JLabel icon = icons.item(item.getItem().getId());
		Icons.linkToWiki(icon, wikiUrl);
		String shortText = shortQty > 0
			? "<span style='color:" + Icons.hex(ColorScheme.PROGRESS_ERROR_COLOR) + "'>short " + shortQty + "</span>"
			: "<span style='color:" + Icons.hex(ColorScheme.PROGRESS_COMPLETE_COLOR) + "'>covered</span>";
		JLabel text = htmlLabel(SuggestPanel.escape(item.getItem().getName())
			+ ": have " + item.getHave() + ", need " + item.getNeed() + ", " + shortText, textWidth(indent, icon, null));
		Icons.linkToWiki(text, wikiUrl);
		container.add(iconRow(icon, text, null, indent));

		for (int i = 0; i < item.getPlans().size(); i++)
		{
			container.add(planOfferRows(item.getItem().getName(), item.getPlans().get(i), i == 0, indent + 1));
		}
		for (ItemSource source : item.getSources())
		{
			container.add(sourceRow(source, indent + 1));
		}
		for (ShortfallItem craftFrom : item.getCraftFrom())
		{
			container.add(shortfallItemRows(craftFrom, indent + 1));
		}
		return container;
	}

	/**
	 * Task 52b-2, spec ruling 28: one {@link PlanOffer} under its shortfall item - the plan item's
	 * icon and bold title linked to the wiki, numbered steps, the rate (when known), any unmet
	 * requirements, and a collapsed alternatives toggle. {@code first} is false for a later offer in
	 * the same item's {@link ShortfallItem#getPlans()} list, reached only through a craft-chain
	 * ingredient (e.g. Dragon scale dust's plan via Blue dragon scales) - captioned "via &lt;plan's
	 * own item&gt;".
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

		JLabel icon = icons.item(plan.getId());
		Icons.linkToWiki(icon, plan.getWikiUrl());
		JLabel title = label(plan.getTitle(), textWidth(indent, icon, null));
		title.setFont(FontManager.getRunescapeBoldFont());
		Icons.linkToWiki(title, plan.getWikiUrl());
		container.add(iconRow(icon, title, null, indent));

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
			container.add(row("requires: " + String.join(", ", offer.getMissing()), indent + 1, ColorScheme.PROGRESS_INPROGRESS_COLOR));
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
		JLabel toggle = toggleLabel("Alternatives (" + alternatives.size() + ") " + (expanded ? "▼" : "▶"), indent);
		clickable(() ->
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
		}, toggle);
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

	// --- building blocks ---

	/**
	 * An icon on the left, {@code center} filling the row, and an optional {@code east} strip;
	 * indented {@code indent} levels. Wrap {@code center}'s text to {@link #textWidth(int, JLabel,
	 * JComponent)} - the row hands it only what the indent, icon column and strip leave.
	 */
	private static JPanel iconRow(JLabel icon, JComponent center, JComponent east, int indent)
	{
		JPanel row = borderRow();
		row.setBorder(BorderFactory.createEmptyBorder(0, indent * INDENT_PX, 0, 0));
		JPanel iconBox = new NoStretchPanel(new BorderLayout());
		iconBox.add(icon, BorderLayout.NORTH);
		row.add(iconBox, BorderLayout.WEST);
		row.add(center, BorderLayout.CENTER);
		if (east != null)
		{
			row.add(east, BorderLayout.EAST);
		}
		return row;
	}

	private static JLabel toggleLabel(String text, int indent)
	{
		JLabel toggle = new JLabel(text);
		toggle.setFont(FontManager.getRunescapeSmallFont());
		toggle.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		toggle.setAlignmentX(Component.LEFT_ALIGNMENT);
		toggle.setBorder(BorderFactory.createEmptyBorder(2, indent * INDENT_PX, 2, 0));
		return toggle;
	}

	/** Runs {@code action} on a click on any of {@code targets} (hand cursor on each). */
	private static void clickable(Runnable action, JComponent... targets)
	{
		MouseAdapter listener = new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				action.run();
			}
		};
		for (JComponent target : targets)
		{
			target.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			target.addMouseListener(listener);
		}
	}

	/** Task 57: a 4px level-progress bar (has no look-and-feel dependency, unlike {@code JProgressBar}). */
	private static final class ProgressBar extends JComponent
	{
		private final double fraction;
		private final Color color;

		ProgressBar(double fraction, Color color)
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

	/**
	 * Task 59: the wrap width of a row's text after {@code indent} levels - the panel's wrap width
	 * less the indent, so a nested label's fixed HTML width never paints past the panel edge.
	 */
	private static int textWidth(int indent)
	{
		return SuggestPanel.WRAP_WIDTH - indent * INDENT_PX;
	}

	/** As {@link #textWidth(int)} for text inside an {@link #iconRow}: also less the icon column, its gap and any {@code east} strip. */
	private static int textWidth(int indent, JLabel icon, JComponent east)
	{
		return textWidth(indent) - icon.getPreferredSize().width - ICON_GAP - (east == null ? 0 : east.getPreferredSize().width + ICON_GAP);
	}

	/** A small-font label wrapped to {@code width} pixels; no indent of its own (its row provides that). */
	private static JLabel label(String text, int width)
	{
		return htmlLabel(SuggestPanel.escape(text), width);
	}

	private static JLabel label(String text, int width, Color color)
	{
		JLabel label = label(text, width);
		label.setForeground(color);
		return label;
	}

	/** As {@link #label}, but {@code rawHtml} is already-escaped markup (colour spans). */
	private static JLabel htmlLabel(String rawHtml, int width)
	{
		JLabel label = new JLabel(SuggestPanel.wrapHtml(rawHtml, width));
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	/** A standalone row indented {@code indent} levels and wrapped to the width that leaves. */
	private static JLabel row(String text, int indent)
	{
		JLabel label = label(text, textWidth(indent));
		label.setBorder(BorderFactory.createEmptyBorder(2, indent * INDENT_PX, 2, 0));
		return label;
	}

	private static JLabel row(String text, int indent, Color color)
	{
		JLabel label = row(text, indent);
		label.setForeground(color);
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
