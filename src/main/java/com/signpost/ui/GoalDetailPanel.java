package com.signpost.ui;

import com.signpost.engine.model.PrayerUnlockGap;
import com.signpost.engine.model.SlayerPointsGap;
import com.signpost.engine.model.SlayerUnlockGap;
import com.signpost.engine.model.Advice;
import com.signpost.engine.model.BringItemStatus;
import com.signpost.engine.model.CombatLevelGap;
import com.signpost.engine.model.DiaryTaskGap;
import com.signpost.engine.model.DiaryTierGap;
import com.signpost.engine.model.FocusDetail;
import com.signpost.engine.model.Gap;
import com.signpost.engine.model.GearGap;
import com.signpost.engine.model.Goal;
import com.signpost.engine.model.GoalStatus;
import com.signpost.engine.model.ItemGap;
import com.signpost.engine.model.ItemSource;
import com.signpost.engine.model.KudosGap;
import com.signpost.engine.model.Met;
import com.signpost.engine.model.NextStep;
import com.signpost.engine.model.PlanOffer;
import com.signpost.engine.model.PrerequisiteGap;
import com.signpost.engine.model.QuestPointsGap;
import com.signpost.engine.model.QuestPrereqGap;
import com.signpost.engine.model.Route;
import com.signpost.engine.model.QuestXp;
import com.signpost.engine.model.RouteStep;
import com.signpost.engine.model.Shortfall;
import com.signpost.engine.model.ShortfallItem;
import com.signpost.engine.model.SkillLevelGap;
import com.signpost.engine.Eta;
import com.signpost.engine.model.SkillPlan;
import com.signpost.kb.GatheringAlternative;
import com.signpost.kb.GatheringPlan;
import com.signpost.kb.GatheringRisk;
import com.signpost.kb.ItemQuantity;
import com.signpost.kb.MethodEntry;
import com.signpost.kb.OwnedItem;
import com.signpost.snapshot.DiaryTier;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
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
 * Goal detail view: shown by {@link NextTargetPanel} instead of
 * {@link SuggestPanel} whenever {@link Advice#getFocus()} is non-null - the drill-down from a
 * Suggest card's "Do this", or from the persistent search box {@link NextTargetPanel} hosts above
 * both modes (see {@link GoalSearchField}). Renders {@link Advice} only; the "Back" button
 * calls back into a plugin action ({@link Actions}), the goal's "Wiki" button and every item icon /
 * name open the system browser directly via {@link LinkBrowser} (a pure UI action, not a mutation).
 *
 * <p>Every skill gap under "Missing" is a clickable row (icon, "Herblore 61/70", a thin
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
	/** The Bucket type of a herb-cleaning method, whose name is just the herb ("Kwuarm"); rendered as "Clean Kwuarm". */
	private static final String CLEANING_TYPE = "Cleaning grimy herbs";
	/** Component-name prefix of a skill row's label ("skill-row:HERBLORE"), for tests to click. */
	public static final String SKILL_ROW_NAME = "skill-row:";
	/** Component name of a route quest step's icon, for tests. */
	public static final String QUEST_STEP_ICON_NAME = "quest-step-icon";

	private final Actions actions;
	private final Icons icons;

	private final JLabel titleLabel = new JLabel();
	private final JLabel categoryStageLabel = new JLabel();
	private final JButton wikiButton = new JButton("Wiki");
	// The title wraps to the room left beside the Wiki/Back buttons, never past the edge
	private final int titleWidth;
	private final JLabel whyLabel = new JLabel();
	private final JLabel reasonLabel = new JLabel();
	private final JPanel explanationPanel = column();
	private final JPanel missingPanel = column();
	private final JPanel nextPanel = column();
	private final JPanel guidancePanel = column();

	private Advice currentAdvice;
	private String currentWikiUrl;
	private String lastGoalId;
	// The one skill row whose route is open; reset to the next skill plan's skill whenever
	// the focused goal changes, otherwise kept across re-renders (same rule as the toggles below).
	private Skill expandedSkill;
	private boolean metExpanded;
	// Which gathering plans' "Alternatives" list is expanded, keyed by shortfall item
	// name + plan id + plan title - never cleared, same persistence rule as SuggestPanel's Why?
	// toggle.
	private final Set<String> expandedAlternatives = new HashSet<>();
	// Which routes' folded "+ N small steps" toggle is open, keyed by goal id + skill.
	private final Set<String> expandedSmallSteps = new HashSet<>();
	// Which shortfall items' "Sources (n)" toggle is open, keyed by goal id + skill + item name.
	private final Set<String> expandedSources = new HashSet<>();

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
		guidancePanel.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.LIGHT_GRAY_COLOR),
			BorderFactory.createEmptyBorder(6, 6, 6, 6)));
		guidancePanel.setVisible(false);
		add(guidancePanel);

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

	private void renderGuidance(NextStep next)
	{
		guidancePanel.removeAll();
		guidancePanel.setVisible(next.hasGuidance());
		if (!next.hasGuidance()) return;
		guidancePanel.add(SuggestPanel.sectionLabel("BRING"));
		if (next.getBringItems().isEmpty())
		{
			guidancePanel.add(label("Nothing extra", SuggestPanel.WRAP_WIDTH - 14));
		}
		addBringItems(guidancePanel, next.getBringItems(), 0, 14);
		guidancePanel.add(label("WHERE: " + next.getWhere(), SuggestPanel.WRAP_WIDTH - 14));
		guidancePanel.add(label("DO: " + next.getDoText(), SuggestPanel.WRAP_WIDTH - 14));
	}

	private void addBringItems(JPanel parent, List<BringItemStatus> items, int indent, int inset)
	{
		for (BringItemStatus item : items)
		{
			JLabel icon = icons.item(item.getItem().getId());
			JLabel text = label(item.text(), textWidth(indent, icon, null) - inset,
				item.isOwned() ? ColorScheme.PROGRESS_COMPLETE_COLOR : ColorScheme.LIGHT_GRAY_COLOR);
			text.setName("bring-item:" + item.getItem().getId());
			String wiki = Icons.itemWikiUrl(item.getItem().getName());
			Icons.linkToWiki(icon, wiki);
			Icons.linkToWiki(text, wiki);
			parent.add(iconRow(icon, text, null, indent));
		}
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
		renderGuidance(focus.getNext());

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

	// --- Missing items ---

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
		if (gap instanceof PrayerUnlockGap || gap instanceof SlayerUnlockGap)
		{
			String goalId = gap instanceof PrayerUnlockGap
				? ((PrayerUnlockGap) gap).getPrayer().getMilestoneId()
				: ((SlayerUnlockGap) gap).getMilestoneId();
			JLabel unlock = row(gapText(gap), indent, gapColor(gap));
			if (goalId != null)
			{
				unlock.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
				unlock.addMouseListener(new MouseAdapter()
				{
					@Override
					public void mouseClicked(MouseEvent event)
					{
						actions.getFocus().accept(goalId);
					}
				});
			}
			missingPanel.add(unlock);
			return;
		}
		JLabel text = row(gapText(gap), indent, gapColor(gap));
		if (gap instanceof ItemGap)
		{
			Icons.linkToWiki(text, ((ItemGap) gap).getWikiUrl());
		}
		missingPanel.add(text);
	}

	/** Red for a hard requirement, orange for a milestone's recommended profile. */
	private static Color gapColor(Gap gap)
	{
		boolean recommended = gap instanceof SkillLevelGap && ((SkillLevelGap) gap).isRecommended()
			|| gap instanceof CombatLevelGap && ((CombatLevelGap) gap).isRecommended()
			|| gap instanceof PrayerUnlockGap && ((PrayerUnlockGap) gap).isRecommended()
			|| gap instanceof GearGap;
		return recommended ? ColorScheme.PROGRESS_INPROGRESS_COLOR : ColorScheme.PROGRESS_ERROR_COLOR;
	}

	private static String recommendedTag()
	{
		return " <span style='color:" + Icons.hex(ColorScheme.BRAND_ORANGE) + "'>(recommended)</span>";
	}

	/**
	 * One skill gap as a clickable row - skill icon, "Herblore 61/70" (plus boostable /
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
		// Time left at the observed rate, over the xp the bank steps don't cover
		String eta = Eta.text(Eta.remainingXp(plan.getRoute(), plan.getToXp() - plan.getFromXp()), currentAdvice.getXpPerHour().get(g.getSkill()));
		if (eta != null)
		{
			text.append(" <span style='color:" + Icons.hex(ColorScheme.LIGHT_GRAY_COLOR) + "'>").append(eta).append("</span>");
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
		JLabel arrow = new JLabel(expanded ? "[-]" : "[+]");
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
			// Steps worth under 2% of the route's xp fold into one toggle at the end;
			// 0-xp crafts never reach here (they nest under their parent step).
			long totalXp = 0;
			for (RouteStep step : route.getSteps())
			{
				totalXp += step.getXpGained();
			}
			List<RouteStep> small = new ArrayList<>();
			for (RouteStep step : route.getSteps())
			{
				if (step.getXpGained() > 0 && step.getXpGained() * 50 < totalXp)
				{
					small.add(step);
				}
				else
				{
					panel.add(stepRows(step, "", indent));
				}
			}
			if (!small.isEmpty())
			{
				panel.add(smallStepsToggle(lastGoalId + "|" + plan.getSkill().name(), small, indent));
			}
		}
		if (plan.getShortfall() != null)
		{
			Shortfall shortfall = plan.getShortfall();
			Shortfall alternative = shortfall.getAlternative();
			panel.add(row((anySteps ? "Then still short" : "Short") + " ~" + thousands(shortfall.getXpShort()) + " xp"
				+ methodAndActions(shortfall), indent, ColorScheme.LIGHT_GRAY_COLOR));
			for (String note : shortfall.getNotes())
			{
				panel.add(row(note, indent, ColorScheme.PROGRESS_INPROGRESS_COLOR));
			}
			if (alternative != null && !shortfall.getUnobtainable().isEmpty())
			{
				panel.add(row(String.join(", ", shortfall.getUnobtainable()) + ": no gathering plan", indent, ColorScheme.LIGHT_GRAY_COLOR));
			}
			// The engine unions an ingredient's plans into its parent, so the
			// same plan reaches the tree twice; render it at its first (topmost) occurrence only.
			// The set is shared with the alternative below for the same reason.
			Set<String> shownPlans = new HashSet<>();
			String keyPrefix = lastGoalId + "|" + plan.getSkill().name() + "|";
			for (ShortfallItem item : shortfall.getItems())
			{
				panel.add(shortfallItemRows(item, indent, keyPrefix, shownPlans));
			}
			if (alternative != null)
			{
				panel.add(row("Or, everything gatherable" + methodAndActions(alternative), indent, ColorScheme.PROGRESS_COMPLETE_COLOR));
				for (ShortfallItem item : alternative.getItems())
				{
					panel.add(shortfallItemRows(item, indent, keyPrefix + "alt|", shownPlans));
				}
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

	/** The folded small steps of a route as a collapsed "+ N small steps (+X xp)" toggle; expanded, each step in full. */
	private JPanel smallStepsToggle(String key, List<RouteStep> small, int indent)
	{
		JPanel panel = column();
		boolean expanded = expandedSmallSteps.contains(key);
		long xp = 0;
		for (RouteStep step : small)
		{
			xp += step.getXpGained();
		}
		JLabel toggle = toggleLabel("+ " + small.size() + (small.size() == 1 ? " small step" : " small steps")
			+ " (+" + thousands(xp) + " xp) " + (expanded ? "[-]" : "[+]"), indent);
		clickable(() ->
		{
			flip(expandedSmallSteps, key);
			render(currentAdvice);
		}, toggle);
		panel.add(toggle);
		if (expanded)
		{
			for (RouteStep step : small)
			{
				panel.add(stepRows(step, "", indent));
			}
		}
		return panel;
	}

	/** A collapsed "Met (n)" toggle under the gaps; expanded, each satisfied requirement in green. */
	private JPanel metToggle(List<Met> met)
	{
		JPanel panel = column();
		JLabel toggle = toggleLabel("Met (" + met.size() + ") " + (metExpanded ? "[-]" : "[+]"), 0);
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
				panel.add(row(m.getLabel(), 1, ColorScheme.PROGRESS_COMPLETE_COLOR));
			}
		}
		return panel;
	}

	private static String gapText(Gap gap)
	{
		if (gap instanceof SlayerPointsGap)
		{
			SlayerPointsGap g = (SlayerPointsGap) gap;
			return "Slayer points " + g.getHave() + "/" + g.getNeed();
		}
		if (gap instanceof SlayerUnlockGap)
		{
			return ((SlayerUnlockGap) gap).getReward().getDisplayName() + ": not unlocked";
		}
		if (gap instanceof PrayerUnlockGap)
		{
			return ((PrayerUnlockGap) gap).getPrayer().getDisplayName() + ": not unlocked";
		}
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
		if (gap instanceof PrerequisiteGap)
		{
			return "Needs " + ((PrerequisiteGap) gap).getName() + " first";
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

	// --- Do this next ---

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
				// The route itself lives under the skill's row in "Missing"; this line
				// re-opens that row when clicked.
				SkillLevelGap g = next.getSkillGap();
				JLabel icon = icons.skill(g.getSkill());
				JLabel text = label("Train " + g.getSkill().getName() + " " + focus.getFromLevel() + "-" + focus.getToLevel() + " (route above)",
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
				var objective = focus.getStatus().getObjective();
				if (objective != null && objective.isAchievementPriority())
				{
					var task = objective.getTasks().get(0);
					panel.add(row("Complete combat achievement: " + task.getName(), 0));
					panel.add(row(task.getDescription(), 0));
				}
				else if (objective != null && !objective.getRewards().isEmpty())
				{
					var reward = objective.getRewards().get(0);
					panel.add(row((reward.isOwnershipUnknown() ? "Confirm whether you own: " : "Work toward: ") + reward.getName(), 0));
					panel.add(row(reward.getBenefit(), 0));
				}
				else
				{
					panel.add(row("Nothing left — ready to do", 0, ColorScheme.PROGRESS_COMPLETE_COLOR));
				}
		}
		return panel;
	}

	/**
	 * A route step: the OUTPUT item's icon on
	 * the left, "Prayer potion(3) ×340" over "61-66, +29,750 xp" (a 0-xp craft has no second
	 * line), then the materials used as a line of chips - item icon and "×N" - under the text.
	 * Icons and names open the wiki. Crafts nest one level deeper with a "craft " prefix.
	 */
	private JPanel stepRows(RouteStep step, String prefix, int indent)
	{
		JPanel container = column();
		if (step.getQuest() != null)
		{
			// "Do <quest>" with the quest icon and wiki link, its xp line under it.
			QuestXp quest = step.getQuest();
			JLabel icon = icons.quest(quest.getState());
			icon.setName(QUEST_STEP_ICON_NAME);
			Icons.linkToWiki(icon, quest.getWikiUrl());
			int width = textWidth(indent, icon, null);
			JPanel text = column();
			JLabel line1 = label("Do " + quest.getName(), width);
			Icons.linkToWiki(line1, quest.getWikiUrl());
			text.add(line1);
			JLabel line2 = label(step.getFromLevel() + "-" + step.getToLevel() + ", +" + thousands(step.getXpGained()) + " xp", width);
			line2.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			text.add(line2);
			container.add(iconRow(icon, text, null, indent));
			return container;
		}
		ItemQuantity output = firstWithId(step.getMethod().getOutputs());
		JLabel icon = icons.item(output == null ? null : output.getId());
		Icons.linkToWiki(icon, output == null ? step.getMethod().wikiUrl() : Icons.itemWikiUrl(output.getName()));

		int width = textWidth(indent, icon, null);
		JPanel text = column();
		JLabel line1 = label(prefix + stepName(step) + " ×" + thousands(step.getCount()), width);
		Icons.linkToWiki(line1, step.getMethod().wikiUrl());
		text.add(line1);
		if (step.getXpGained() > 0)
		{
			JLabel line2 = label(step.getFromLevel() + "-" + step.getToLevel() + ", +" + thousands(step.getXpGained()) + " xp", width);
			line2.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			text.add(line2);
		}
		if (!step.getMaterialsUsed().isEmpty())
		{
			text.add(materialChips(step));
			String note = groupStorageNote(step);
			if (note != null)
			{
				JLabel line3 = label(note, width);
				line3.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
				text.add(line3);
			}
		}

		container.add(iconRow(icon, text, null, indent));
		for (RouteStep craft : step.getCrafts())
		{
			container.add(stepRows(craft, "craft ", indent + 1));
		}
		return container;
	}

	/**
	 * "in group storage: Ranarr weed 200, Vial of water 100" from the step's
	 * {@link RouteStep#getFromGroupStorage()}, or null when empty. Walks the method's own material
	 * list so the order is stable.
	 */
	private static String groupStorageNote(RouteStep step)
	{
		List<String> parts = new ArrayList<>();
		for (ItemQuantity material : step.getMethod().getMaterials())
		{
			int share = material.getId() == null ? 0 : step.getFromGroupStorage().getOrDefault(material.getId(), 0);
			if (share > 0)
			{
				parts.add(material.getName() + " " + thousands(share));
			}
		}
		return parts.isEmpty() ? null : "in group storage: " + String.join(", ", parts);
	}

	private static String stepName(RouteStep step)
	{
		MethodEntry method = step.getMethod();
		return (method.getTypes().contains(CLEANING_TYPE) ? "Clean " : "") + method.getName();
	}

	/** One chip per material used - its icon (wiki-linked, tooltip = name) and a small grey "×N" - left-aligned on one line. */
	private JPanel materialChips(RouteStep step)
	{
		Map<Integer, ItemQuantity> byId = new LinkedHashMap<>();
		for (ItemQuantity material : step.getMethod().getMaterials())
		{
			if (material.getId() != null)
			{
				byId.put(material.getId(), material);
			}
		}
		// One BoxLayout line; bundled methods use at most three materials, so it never
		// needs to wrap - switch to a wrapping layout if a wider recipe ever shows up.
		JPanel strip = new NoStretchPanel();
		strip.setLayout(new BoxLayout(strip, BoxLayout.X_AXIS));
		for (Map.Entry<Integer, Integer> used : step.getMaterialsUsed().entrySet())
		{
			ItemQuantity material = byId.get(used.getKey());
			String name = material == null ? "item " + used.getKey() : material.getName();
			JLabel icon = icons.item(used.getKey());
			Icons.linkToWiki(icon, Icons.itemWikiUrl(name));
			icon.setToolTipText(name);
			JLabel count = new JLabel("×" + thousands(used.getValue()));
			count.setFont(FontManager.getRunescapeSmallFont());
			count.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			count.setToolTipText(name);
			strip.add(icon);
			strip.add(Box.createHorizontalStrut(2));
			strip.add(count);
			strip.add(Box.createHorizontalStrut(8));
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

	/** ": <method> ×<actions>" for a shortfall header, or ":" when the engine named no method. */
	private static String methodAndActions(Shortfall shortfall)
	{
		return shortfall.getMethod() == null ? ":" : ": " + shortfall.getMethod().getName() + " ×" + thousands(shortfall.getActionsNeeded());
	}

	private static String thousands(long n)
	{
		return String.format(Locale.ENGLISH, "%,d", n);
	}

	private static JLabel sourceRow(ItemSource source, int indent)
	{
		StringBuilder sb = new StringBuilder(source.getType()).append(": ").append(source.getWhere());
		if (source.getDetail() != null && !source.getDetail().isEmpty())
		{
			sb.append(' ').append(source.getDetail());
		}
		return row(sb.toString(), indent, ColorScheme.LIGHT_GRAY_COLOR);
	}

	// --- Shortfall under its skill row ---

	private JPanel shortfallItemRows(ShortfallItem item, int indent, String keyPrefix, Set<String> shownPlans)
	{
		JPanel container = column();
		int shortQty = Math.max(0, item.getNeed() - item.getHave());
		String wikiUrl = item.getWikiUrl() != null ? item.getWikiUrl() : Icons.itemWikiUrl(item.getItem().getName());

		JLabel icon = icons.item(item.getItem().getId());
		Icons.linkToWiki(icon, wikiUrl);
		String shortText = shortQty > 0
			? "<span style='color:" + Icons.hex(ColorScheme.PROGRESS_ERROR_COLOR) + "'>short " + shortQty + "</span>"
			: "<span style='color:" + Icons.hex(ColorScheme.PROGRESS_COMPLETE_COLOR) + "'>covered</span>";
		// Lead with the shortage; the label's grey foreground colours everything but the span
		int inGroupStorage = item.getInGroupStorage();
		JLabel text = htmlLabel(SuggestPanel.escape(item.getItem().getName()) + ": " + shortText
			+ " (have " + item.getHave() + ", need " + item.getNeed()
			+ (inGroupStorage > 0 ? "; in group storage: " + inGroupStorage : "") + ")", textWidth(indent, icon, null));
		text.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		Icons.linkToWiki(text, wikiUrl);
		container.add(iconRow(icon, text, null, indent));

		for (int i = 0; i < item.getPlans().size(); i++)
		{
			PlanOffer offer = item.getPlans().get(i);
			if (shownPlans.add(offer.getPlan().getId() + "|" + offer.getPlan().getTitle()))
			{
				container.add(planOfferRows(item.getItem().getName(), offer, i == 0, indent + 1));
			}
		}
		// Sources collapse under a toggle; the "craft:" line stays out of it, shown only
		// when no craft-from rows below already spell the recipe out.
		List<ItemSource> collapsed = new ArrayList<>();
		for (ItemSource source : item.getSources())
		{
			if (!"craft".equals(source.getType()))
			{
				collapsed.add(source);
			}
			else if (item.getCraftFrom().isEmpty())
			{
				container.add(sourceRow(source, indent + 1));
			}
		}
		if (!collapsed.isEmpty())
		{
			container.add(sourcesToggle(keyPrefix + item.getItem().getName(), collapsed, indent + 1));
		}
		for (ShortfallItem craftFrom : item.getCraftFrom())
		{
			container.add(shortfallItemRows(craftFrom, indent + 1, keyPrefix, shownPlans));
		}
		return container;
	}

	/** A collapsed "Sources (n)" toggle; expanded, one grey line per source. Persists per {@code key} like the other toggles. */
	private JPanel sourcesToggle(String key, List<ItemSource> sources, int indent)
	{
		JPanel panel = column();
		boolean expanded = expandedSources.contains(key);
		JLabel toggle = toggleLabel("Sources (" + sources.size() + ") " + (expanded ? "[-]" : "[+]"), indent);
		clickable(() ->
		{
			flip(expandedSources, key);
			render(currentAdvice);
		}, toggle);
		panel.add(toggle);
		if (expanded)
		{
			for (ItemSource source : sources)
			{
				panel.add(sourceRow(source, indent + 1));
			}
		}
		return panel;
	}

	/**
	 * One {@link PlanOffer} under its shortfall item - the plan item's
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
		container.add(row("Risk: " + plan.getRisk().getLabel(), indent + 1,
			plan.getRisk() == GatheringRisk.NONE ? ColorScheme.LIGHT_GRAY_COLOR : ColorScheme.PROGRESS_INPROGRESS_COLOR));
		addBringItems(container, offer.getItems(), indent + 1, 0);

		int n = 1;
		for (String step : offer.getSteps())
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
			container.add(alternativesToggle(key, plan.getAlternatives(), offer.getAlternativeSteps(), offer.getAlternativeItems(), indent + 1));
		}

		return container;
	}

	/**
	 * A collapsed "Alternatives (n)" toggle; expanding it lists each alternative's
	 * title and its offer-filtered steps, numbered after filtering. Expansion persists per {@code key} across re-renders (same rule as
	 * {@code expandedAlternatives} above / SuggestPanel's Why? toggle) - clicking it re-renders the
	 * whole panel from {@link #currentAdvice} since, unlike the collapsible section headers, a
	 * fresh label is built on every render rather than one persistent label mutated in place.
	 */
	private JPanel alternativesToggle(String key, List<GatheringAlternative> alternatives, List<List<String>> alternativeSteps,
		List<List<BringItemStatus>> alternativeItems, int indent)
	{
		JPanel panel = column();

		boolean expanded = expandedAlternatives.contains(key);
		JLabel toggle = toggleLabel("Alternatives (" + alternatives.size() + ") " + (expanded ? "[-]" : "[+]"), indent);
		clickable(() ->
		{
			flip(expandedAlternatives, key);
			render(currentAdvice);
		}, toggle);
		panel.add(toggle);

		if (expanded)
		{
			for (int i = 0; i < alternatives.size(); i++)
			{
				panel.add(row(alternatives.get(i).getTitle(), indent + 1));
				panel.add(row("Risk: " + alternatives.get(i).getRisk().getLabel(), indent + 1));
				addBringItems(panel, alternativeItems.get(i), indent + 1, 0);
				int n = 1;
				for (String step : alternativeSteps.get(i))
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

	/** Toggles {@code key}'s membership of a persistent expanded-toggle set. */
	private static void flip(Set<String> expanded, String key)
	{
		if (!expanded.remove(key))
		{
			expanded.add(key);
		}
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

	/**
	 * A panel whose maximum height is always its preferred height, so a parent
	 * {@link BoxLayout} with spare vertical room never stretches it and creates whitespace between
	 * sections. {@link JLabel} already behaves this way; a plain {@link JPanel} does not.
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
	 * The wrap width of a row's text after {@code indent} levels - the panel's wrap width
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
	 * {@link GoalSearchField} it hosts above both modes - but stays part of this shape since
	 * the plugin constructs both from the same pair of methods.
	 */
	@Value
	public static class Actions
	{
		Consumer<String> focus;
		Runnable clearFocus;
	}
}
