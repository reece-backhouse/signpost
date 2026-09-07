package dev.reece.nta.engine;

import dev.reece.nta.engine.model.CombatLevelGap;
import dev.reece.nta.engine.model.Gap;
import dev.reece.nta.engine.model.Goal;
import dev.reece.nta.engine.model.GoalCategory;
import dev.reece.nta.engine.model.GoalStatus;
import dev.reece.nta.engine.model.ItemGap;
import dev.reece.nta.engine.model.RankedGoal;
import dev.reece.nta.engine.model.SkillLevelGap;
import dev.reece.nta.kb.KnowledgeBase;
import dev.reece.nta.kb.MilestoneCategory;
import dev.reece.nta.snapshot.Snapshot;
import java.util.List;
import net.runelite.api.Skill;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task 28: {@link WhyBuilder} builds the deterministic "why" text per ticket D3, one clause per
 * applicable rule joined with "; ", capped at 140 characters.
 */
class WhyBuilderTest
{
	private final WhyBuilder whyBuilder = new WhyBuilder();
	private final KnowledgeBase kb = new KbBuilder().build();
	private final Snapshot snap = new SnapshotBuilder().build();

	@Test
	void readyClauseWhenNoGapsAndBankKnown()
	{
		GoalStatus status = status("g1", GoalCategory.QUEST, List.of(), true, false);

		assertEquals("Ready now", whyBuilder.why(rank(status), kb, snap));
	}

	@Test
	void awayClauseCountsLeafGapsSingularAndPlural()
	{
		GoalStatus one = status("g1", GoalCategory.QUEST, List.of(new CombatLevelGap(1, 2)), false, false);
		assertEquals("1 requirement away", whyBuilder.why(rank(one), kb, snap));

		GoalStatus two = status("g2", GoalCategory.QUEST,
			List.of(new CombatLevelGap(1, 2), new ItemGap("Rune", 0, 1, List.of(), false)), false, false);
		assertEquals("2 requirements away", whyBuilder.why(rank(two), kb, snap));
	}

	@Test
	void bankUnknownClauseAppendedAndNeverLabelledReady()
	{
		GoalStatus status = status("g1", GoalCategory.QUEST, List.of(new ItemGap("Rune", null, 1, List.of(), false)), false, true);

		assertEquals("1 requirement away; bank unknown", whyBuilder.why(rank(status), kb, snap));
	}

	@Test
	void unlocksClauseListsUpToTwoEntries()
	{
		KnowledgeBase unlockKb = new KbBuilder()
			.milestone("milestone:test", MilestoneCategory.UNLOCK, "Test Unlock", 5)
			.unlocks("Ability A", "Ability B", "Ability C")
			.build();
		GoalStatus status = status("milestone:test", GoalCategory.MILESTONE, List.of(new CombatLevelGap(1, 2)), false, false);

		assertEquals("1 requirement away; unlocks Ability A, Ability B", whyBuilder.why(rank(status), unlockKb, snap));
	}

	@Test
	void gearUpgradeClauseWhenTierExceedsHighestOwnedInSubcategory()
	{
		KnowledgeBase gearKb = new KbBuilder()
			.milestone("melee:tier5", MilestoneCategory.GEAR, "Tier 5 Melee", 5)
			.ownedIf("Tier 5 Item", 100).gearTier(5).subcategory("melee")
			.milestone("melee:tier8", MilestoneCategory.GEAR, "Tier 8 Melee", 5)
			.ownedIf("Tier 8 Item", 200).gearTier(8).subcategory("melee")
			.build();
		Snapshot owning = new SnapshotBuilder().inventoryItem(100, "Tier 5 Item", 1).build();
		GoalStatus status = status("melee:tier8", GoalCategory.MILESTONE, List.of(), true, false);

		assertEquals("Ready now; biggest melee upgrade over what you own", whyBuilder.why(rank(status), gearKb, owning));
	}

	@Test
	void materialsAlreadyInBankClauseWhenEveryItemGapIsSatisfied()
	{
		GoalStatus status = status("g1", GoalCategory.QUEST, List.of(new ItemGap("Egg", 5, 5, List.of(), false)), false, false);

		assertEquals("1 requirement away; materials already in bank", whyBuilder.why(rank(status), kb, snap));
	}

	@Test
	void justLevelsClauseListsSkillsWithHaveAndNeed()
	{
		GoalStatus status = status("g1", GoalCategory.QUEST,
			List.of(new SkillLevelGap(Skill.HERBLORE, 61, 70, 500_000, false, null)), false, false);

		assertEquals("1 requirement away; just levels: Herblore 61/70", whyBuilder.why(rank(status), kb, snap));
	}

	@Test
	void truncationDropsTrailingClausesAtBoundary()
	{
		String c1 = "A".repeat(100);
		String c2 = "B".repeat(45); // "A"*100 + "; " + "B"*45 = 147 chars, over the 140 cap.

		String result = WhyBuilder.truncate(List.of(c1, c2));

		assertEquals(c1, result, "the second clause must be dropped whole, never cut mid-word: " + result);
		assertTrue(result.length() <= 140);
	}

	@Test
	void truncationCutsASingleOverlongClauseWithEllipsis()
	{
		String result = WhyBuilder.truncate(List.of("A".repeat(200)));

		assertEquals("A".repeat(139) + "…", result);
		assertEquals(140, result.length());
	}

	private static GoalStatus status(String id, GoalCategory category, List<Gap> gaps, boolean ready, boolean bankUnknown)
	{
		Goal goal = new Goal(id, category, id, "https://x", 5);
		return new GoalStatus(goal, gaps, ready, bankUnknown, List.of());
	}

	private static RankedGoal rank(GoalStatus status)
	{
		return new RankedGoal(status, 1.0, false);
	}
}
