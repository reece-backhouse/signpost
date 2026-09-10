package com.signpost.engine;

import com.signpost.engine.model.CombatLevelGap;
import com.signpost.engine.model.Gap;
import com.signpost.engine.model.GearGap;
import com.signpost.engine.model.Goal;
import com.signpost.engine.model.GoalCategory;
import com.signpost.engine.model.GoalStatus;
import com.signpost.engine.model.ItemGap;
import com.signpost.engine.model.PrerequisiteGap;
import com.signpost.engine.model.RankedGoal;
import com.signpost.engine.model.SkillLevelGap;
import com.signpost.kb.KnowledgeBase;
import com.signpost.kb.MilestoneCategory;
import com.signpost.kb.OwnedItem;
import com.signpost.snapshot.AccountType;
import com.signpost.snapshot.Snapshot;
import java.util.List;
import java.util.Set;
import net.runelite.api.Skill;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link WhyBuilder} builds the deterministic "why" text, one clause per
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
		GoalStatus one = status("g1", GoalCategory.QUEST, List.of(new CombatLevelGap(1, 2, false)), false, false);
		assertEquals("1 requirement away", whyBuilder.why(rank(one), kb, snap));

		GoalStatus two = status("g2", GoalCategory.QUEST,
			List.of(new CombatLevelGap(1, 2, false), new ItemGap("Rune", 0, 1, List.of(), false)), false, false);
		assertEquals("2 requirements away", whyBuilder.why(rank(two), kb, snap));
	}

	@Test
	void bankUnknownClauseAppendedAndNeverLabelledReady()
	{
		// A lone unknown-have ItemGap counts as 0 unmet, so the away-clause reads "0"
		// even though the goal is still barred from "Ready now" by bankUnknown.
		GoalStatus status = status("g1", GoalCategory.QUEST, List.of(new ItemGap("Rune", null, 1, List.of(), false)), false, true);

		assertEquals("0 requirements away; bank unknown", whyBuilder.why(rank(status), kb, snap));
	}

	/** A group ironman whose shared storage has not been seen is told so; readiness is unaffected. */
	@Test
	void groupStorageNotSeenClauseOnlyForGroupIronmanWithUnseenStorage()
	{
		GoalStatus status = status("g1", GoalCategory.QUEST, List.of(), true, false);
		Snapshot unseen = new SnapshotBuilder().accountType(AccountType.GROUP).build();
		Snapshot seen = new SnapshotBuilder().accountType(AccountType.GROUP).groupStorageKnown().build();

		assertEquals("Ready now; group storage not seen", whyBuilder.why(rank(status), kb, unseen));
		assertEquals("Ready now", whyBuilder.why(rank(status), kb, seen));
		assertEquals("Ready now", whyBuilder.why(rank(status), kb, snap), "a non-group account has no group storage to see");
		Snapshot off = new SnapshotBuilder().accountType(AccountType.GROUP).groupStorageOff().build();
		assertEquals("Ready now", whyBuilder.why(rank(status), kb, off), "toggle off: nothing to open, so no clause");
	}

	@Test
	void unlocksClauseListsUpToTwoEntries()
	{
		KnowledgeBase unlockKb = new KbBuilder()
			.milestone("milestone:test", MilestoneCategory.UNLOCK, "Test Unlock", 5)
			.unlocks("Ability A", "Ability B", "Ability C")
			.build();
		GoalStatus status = status("milestone:test", GoalCategory.MILESTONE, List.of(new CombatLevelGap(1, 2, false)), false, false);

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
			List.of(new SkillLevelGap(Skill.HERBLORE, 61, 70, 500_000, false, null, false)), false, false);

		assertEquals("1 requirement away; just levels: Herblore 61/70", whyBuilder.why(rank(status), kb, snap));
	}

	@Test
	void laterGoalNeverSaysReadyNowEvenWithNoGaps()
	{
		GoalStatus status = status("boss:test", GoalCategory.BOSS, List.of(), true, false);
		RankedGoal later = new RankedGoal(status, 0.25, false, true);

		String why = whyBuilder.why(later, kb, snap);

		assertFalse(why.contains("Ready now"), why);
		assertTrue(why.startsWith("later: stage " + status.getGoal().getStage()), why);
	}


	@Test
	void nonRecommendedGapsDoNotTriggerTheRecommendedClause()
	{
		GoalStatus status = status("g1", GoalCategory.QUEST,
			List.of(new SkillLevelGap(Skill.RANGED, 70, 85, 1_000_000, false, null, false)), false, false);

		String why = whyBuilder.why(rank(status), kb, snap);

		assertFalse(why.contains("recommended:"), why);
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

	/** A partly owned outfit says how many pieces are held. */
	@Test
	void piecesClauseCountsHeldOutfitPiecesAgainstOwnedIfMin()
	{
		KnowledgeBase outfitKb = new KbBuilder()
			.milestone("milestone:prospector-outfit", MilestoneCategory.GEAR, "Prospector outfit", 6)
			.ownedIf("Prospector helmet", 12013)
			.ownedIf("Prospector jacket", 12014)
			.ownedIf("Prospector legs", 12015)
			.ownedIf("Prospector boots", 12016)
			.ownedIfMin(4)
			.build();
		Snapshot twoPieces = new SnapshotBuilder().bankItem(12013, "Prospector helmet", 1).bankItem(12016, "Prospector boots", 1).build();
		GoalStatus status = status("milestone:prospector-outfit", GoalCategory.MILESTONE, List.of(), true, false);

		assertEquals("Ready now; 2/4 pieces", whyBuilder.why(rank(status), outfitKb, twoPieces));
		assertEquals("Ready now", whyBuilder.why(rank(status), outfitKb, snap), "no pieces held: no clause");
	}

	/** A method-linked untradeable names the skill it speeds up, and the skill target for it when one is ranked. */
	@Test
	void speedsUpClauseNamesTheSkillAndItsNextTargetWhenOneExists()
	{
		KnowledgeBase bagKb = new KbBuilder()
			.milestone("milestone:coal-bag", MilestoneCategory.GEAR, "Coal bag", 7)
			.ownedIf("Coal bag", 12019)
			.speedsUp(Skill.MINING)
			.build();
		GoalStatus status = status("milestone:coal-bag", GoalCategory.MILESTONE, List.of(), true, false);

		assertEquals("Ready now; speeds up Mining (your next Mining target)", whyBuilder.why(rank(status), bagKb, snap, Set.of(Skill.MINING)));
		assertEquals("Ready now; speeds up Mining", whyBuilder.why(rank(status), bagKb, snap, Set.of(Skill.HERBLORE)));
	}

	/** A prerequisite gap counts as one requirement away and is named in the Missing line. */
	@Test
	void prerequisiteGapIsOneRequirementAwayAndNamedAsMissing()
	{
		GoalStatus status = status("poh:portal-nexus", GoalCategory.MILESTONE, List.of(new PrerequisiteGap("poh:portal-chamber", "Portal chamber")), false, false);

		assertEquals("1 requirement away", whyBuilder.why(rank(status), kb, snap));
		assertEquals(List.of("Missing: Portal chamber first"), whyBuilder.explain(rank(status), kb, snap, 2));
	}

	private static GoalStatus status(String id, GoalCategory category, List<Gap> gaps, boolean ready, boolean bankUnknown)
	{
		Goal goal = new Goal(id, category, id, "https://x", 5, 1);
		return new GoalStatus(goal, gaps, ready, bankUnknown, List.of());
	}

	private static RankedGoal rank(GoalStatus status)
	{
		return new RankedGoal(status, 1.0, false, false);
	}
}
