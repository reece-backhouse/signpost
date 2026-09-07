package dev.reece.nta.engine;

import dev.reece.nta.engine.model.GoalCategory;
import dev.reece.nta.snapshot.AccountType;
import dev.reece.nta.engine.model.GoalStatus;
import com.google.gson.Gson;
import dev.reece.nta.engine.model.Advice;
import dev.reece.nta.engine.model.RankedGoal;
import dev.reece.nta.kb.KnowledgeBase;
import dev.reece.nta.kb.MilestoneCategory;
import dev.reece.nta.snapshot.Snapshot;
import dev.reece.nta.store.AccountData;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task 29: {@link Engine#run(Snapshot, KnowledgeBase, AccountData, Instant)} wires
 * {@link PrefsResolver}, {@link Ranker}, {@link SuggestSelector}, and {@link WhyBuilder} together
 * into the new {@link Advice} fields, consistently with each other.
 */
class EngineAdviceTest
{
	private final Engine engine = new Engine(new BoostTable());
	private final Instant now = Instant.parse("2026-09-07T12:00:00Z");

	@Test
	void pickedIsASubsetOfRankedAndRestIsRankedMinusPicked()
	{
		KnowledgeBase kb = fiveQuestKb();
		Snapshot snapshot = new SnapshotBuilder().build();

		Advice advice = engine.run(snapshot, kb, AccountData.empty(), now);

		Set<String> rankedIds = ids(advice.getRanked());
		Set<String> pickedIds = ids(advice.getPicked());
		assertTrue(rankedIds.containsAll(pickedIds), "picked must be a subset of ranked");

		List<String> expectedRest = advice.getRanked().stream()
			.map(EngineAdviceTest::id)
			.filter(id -> !pickedIds.contains(id))
			.collect(Collectors.toList());
		assertEquals(expectedRest, advice.getRest().stream().map(EngineAdviceTest::id).collect(Collectors.toList()));
	}

	@Test
	void whysAreKeyedForEveryRankedGoal()
	{
		KnowledgeBase kb = fiveQuestKb();
		Snapshot snapshot = new SnapshotBuilder().build();

		Advice advice = engine.run(snapshot, kb, AccountData.empty(), now);

		for (RankedGoal r : advice.getRanked())
		{
			assertTrue(advice.getWhys().containsKey(id(r)), "missing why for " + id(r));
		}
		assertEquals(advice.getRanked().size(), advice.getWhys().size(), "no stray why entries beyond ranked");
	}

	@Test
	void explanationsAreKeyedForEveryRankedGoalWithOneToSixShortLines()
	{
		KnowledgeBase kb = fiveQuestKb();
		Snapshot snapshot = new SnapshotBuilder().build();

		Advice advice = engine.run(snapshot, kb, AccountData.empty(), now);

		for (RankedGoal r : advice.getRanked())
		{
			List<String> lines = advice.getExplanations().get(id(r));
			assertTrue(lines != null && !lines.isEmpty() && lines.size() <= 6, "explanation for " + id(r) + ": " + lines);
			assertEquals(new WhyBuilder().explain(r, kb, snapshot, advice.getAccountStage()), lines);
		}
		assertEquals(advice.getRanked().size(), advice.getExplanations().size(), "no stray explanation entries beyond ranked");
	}

	@Test
	void ignoredGoalsAreAbsentFromRanked()
	{
		KnowledgeBase kb = fiveQuestKb();
		Snapshot snapshot = new SnapshotBuilder().build();

		AccountData data = new AccountData(new HashMap<>(), null, new HashMap<>(), new HashSet<>(Set.of("quest:0")), new ArrayList<>(), null);

		Advice advice = engine.run(snapshot, kb, data, now);

		assertFalse(ids(advice.getRanked()).contains("quest:0"));
		assertTrue(advice.getPrefs().getHidden().contains("quest:0"));
	}

	@Test
	void prefsOnAdviceMatchesWhatPrefsResolverComputesDirectly()
	{
		KnowledgeBase kb = fiveQuestKb();
		Snapshot snapshot = new SnapshotBuilder().build();
		AccountData data = AccountData.empty();

		Advice advice = engine.run(snapshot, kb, data, now);

		PrefsResolver resolver = new PrefsResolver();
		assertEquals(resolver.resolve(data, advice.getStatuses(), now), advice.getPrefs());
	}

	@Test
	void reasonsCarryTheMilestonesCuratedReasonTextAndExcludeQuestsAndDiaries()
	{
		// KbBuilder.milestone(...) always stamps reason "test" (see KbBuilder#build); quests carry no
		// reason field at all, so a mixed kb lets us assert both the presence and the absence.
		KnowledgeBase kb = new KbBuilder()
			.quest(0, "Animal Magnetism").skill(Skill.WOODCUTTING, 10)
			.milestone("m:barrows-gloves", MilestoneCategory.GEAR, "Barrows gloves", 8)
			.ownedIf("Barrows gloves", 7462)
			.skill(Skill.DEFENCE, 40)
			.build();
		Snapshot snapshot = new SnapshotBuilder().build();

		Advice advice = engine.run(snapshot, kb, AccountData.empty(), now);

		assertEquals("test", advice.getReasons().get("m:barrows-gloves"));
		assertFalse(advice.getReasons().containsKey("quest:0"), "quests have no curated reason text");
	}

	// --- Task 42: accountStage / later (spec ruling 27). ---

	@Test
	void laterGoalsAreExcludedFromPickedAndRestButAppearInLater()
	{
		KnowledgeBase kb = new KbBuilder()
			.quest(0, "Animal Magnetism").skill(Skill.WOODCUTTING, 10)
			.quest(9, "Biohazard").skill(Skill.WOODCUTTING, 20)
			.milestone("boss:stage4", MilestoneCategory.BOSS, "Endgame Boss", 10).stage(4)
			.build();
		Snapshot snapshot = new SnapshotBuilder().build(); // default account is stage 1.

		Advice advice = engine.run(snapshot, kb, AccountData.empty(), now);

		assertEquals(1, advice.getAccountStage());
		assertTrue(ids(advice.getLater()).contains("boss:stage4"), ids(advice.getLater()).toString());
		assertFalse(ids(advice.getPicked()).contains("boss:stage4"), ids(advice.getPicked()).toString());
		assertFalse(ids(advice.getRest()).contains("boss:stage4"), ids(advice.getRest()).toString());
	}

	@Test
	void rankedPartitionsExactlyIntoPickedRestAndLaterWithNoOverlap()
	{
		KnowledgeBase kb = fiveQuestKb();
		Snapshot snapshot = new SnapshotBuilder().build();

		Advice advice = engine.run(snapshot, kb, AccountData.empty(), now);

		Set<String> picked = ids(advice.getPicked());
		Set<String> rest = ids(advice.getRest());
		Set<String> later = ids(advice.getLater());

		assertTrue(java.util.Collections.disjoint(picked, later));
		assertTrue(java.util.Collections.disjoint(rest, later));
		assertTrue(java.util.Collections.disjoint(picked, rest));

		Set<String> union = new HashSet<>(picked);
		union.addAll(rest);
		union.addAll(later);
		assertEquals(ids(advice.getRanked()), union);
	}

	/**
	 * Regression test for the user feedback that motivated S4.1 (spec ruling 27) and its task-46
	 * follow-up: against the bundled real knowledge base, a mid-game group ironman account (combat
	 * ~105, total level ~1643, quest log mostly finished, owns Barrows gloves + a Rune crossbow + a
	 * Dragon defender variant but no Karil's or Bandos) must not see Theatre of Blood or the infernal
	 * cape (both stage 4) picked or "ready", and Chambers of Xeric (stage 3, recommended stats/gear
	 * far above this account's) must not be "ready" either - while the account's actual stage-2 boss
	 * (Moons of Peril) is not excluded as "later" and is picked, and God Wars Dungeon (task 46: now
	 * stage 3, and its recommended gear list is no longer satisfied by owning just Barrows gloves) is
	 * correctly not ready and never picked alongside its own drop, Bandos armour.
	 */
	@Test
	void midGameAccountDoesNotSeeEndgameBossesAsReadyOrPicked()
	{
		KnowledgeBase kb = KnowledgeBase.load(new Gson());
		Snapshot snapshot = midGameAccount().build();

		Advice advice = engine.run(snapshot, kb, AccountData.empty(), now);

		assertEquals(2, advice.getAccountStage(), "sanity check on the account fixture itself");
		assertEquals(106, snapshot.combatLevel(), "sanity check: combat ~105 as described in the user feedback");

		Set<String> pickedIds = ids(advice.getPicked());
		System.out.println("mid-game account top three (task 46): " + advice.getPicked().stream()
			.map(r -> r.getStatus().getGoal().getId() + " \"" + r.getStatus().getGoal().getName() + "\"")
			.collect(Collectors.toList()));

		assertFalse(pickedIds.contains("boss:theatre-of-blood"), pickedIds.toString());
		assertFalse(pickedIds.contains("milestone:infernal-cape"), pickedIds.toString());

		Set<String> laterIds = ids(advice.getLater());
		assertTrue(laterIds.contains("boss:theatre-of-blood"), "stage 4, more than one stage above a stage 2 account: " + laterIds);
		assertTrue(laterIds.contains("milestone:infernal-cape"), "stage 4, more than one stage above a stage 2 account: " + laterIds);

		boolean coxReady = advice.getStatuses().stream()
			.anyMatch(s -> s.getGoal().getId().equals("boss:chambers-of-xeric") && s.isReady());
		assertFalse(coxReady, "CoX's recommended stats/gear are well above this account's - must not be Ready now");

		boolean moonsIsLater = laterIds.contains("boss:moons-of-peril");
		assertFalse(moonsIsLater, "Moons of Peril is stage 2, same as the account - must not be excluded as later");

		boolean gwdReady = advice.getStatuses().stream()
			.anyMatch(s -> s.getGoal().getId().equals("boss:god-wars-dungeon") && s.isReady());
		assertFalse(gwdReady,
			"owning only 2 of GWD's 7 recommended.gearOwnedAny items (Barrows gloves + Rune crossbow) is short of the default min of 4");

		assertTrue(pickedIds.contains("boss:moons-of-peril"), "Moons of Peril must be picked: " + pickedIds);
		assertFalse(pickedIds.contains("boss:god-wars-dungeon") && pickedIds.contains("milestone:bandos-armour"),
			"GWD and its own drop (Bandos armour) must never both be picked: " + pickedIds);
	}

	@Test
	void oldTwoArgOverloadStillProducesRankedPickedRestAndWhysUsingEmptyAccountData()
	{
		KnowledgeBase kb = fiveQuestKb();
		Snapshot snapshot = new SnapshotBuilder().build();

		Advice advice = engine.run(snapshot, kb);

		assertFalse(advice.getRanked().isEmpty());
		assertFalse(advice.getPicked().isEmpty());
		assertEquals(advice.getRanked().size(), advice.getPicked().size() + advice.getRest().size());
	}

	/**
	 * Task 51 (spec ruling 28): the same mid-game account, one Herblore level short of Song of the
	 * Elves (priority 9) with a bank of Ranarr weed, Vials of water and Snape grass, gets a
	 * "70 Herblore" skill target whose parent is Song of the Elves, whose route the bank covers,
	 * and which is ranked above God Wars Dungeon (stage 3, like SotE, but not ready).
	 */
	@Test
	void midGameAccountWithRanarrInTheBankGetsABankCoveredSeventyHerbloreTargetForSongOfTheElves()
	{
		KnowledgeBase kb = KnowledgeBase.load(new Gson());
		Snapshot snapshot = midGameAccount()
			.accountType(AccountType.GROUP)
			.quest(Quest.SONG_OF_THE_ELVES, QuestState.NOT_STARTED) // among the first 186 Quest constants the fixture marks finished.
			.bankItem(257, "Ranarr weed", 1000)
			.bankItem(227, "Vial of water", 1000)
			.bankItem(231, "Snape grass", 1000)
			.build();

		Advice advice = engine.run(snapshot, kb, AccountData.empty(), now);

		GoalStatus target = advice.getStatuses().stream()
			.filter(s -> s.getGoal().getId().equals("skill:HERBLORE:70"))
			.findFirst().orElseThrow(() -> new AssertionError("no 70 Herblore target among " + advice.getStatuses().size() + " statuses"));
		assertTrue(target.getParents().stream().anyMatch(p -> p.getId().equals("quest:" + Quest.SONG_OF_THE_ELVES.getId())), target.getParents().toString());
		assertTrue(target.isBankCovered(), "1000 ranarr/vials/snape grass cover 69->70: " + target.getBankRoute());

		List<String> rankedIds = new ArrayList<>(ids(advice.getRanked()));
		int herblore = rankedIds.indexOf("skill:HERBLORE:70");
		int gwd = rankedIds.indexOf("boss:god-wars-dungeon");
		int moons = rankedIds.indexOf("boss:moons-of-peril");
		assertTrue(herblore >= 0 && gwd >= 0 && moons >= 0, rankedIds.toString());
		assertTrue(herblore < gwd, "70 Herblore (" + herblore + ") must rank above GWD (" + gwd + ")");
		assertTrue(herblore < moons, "70 Herblore (" + herblore + ", bank covered, priority 9) must rank above Moons (" + moons + ")");
		assertTrue(ids(advice.getPicked()).contains("skill:HERBLORE:70"), "top of the rest tier, and a non-quest category for slot three: " + ids(advice.getPicked()));
		System.out.println("mid-game account top three (task 51): " + advice.getPicked().stream()
			.map(r -> r.getStatus().getGoal().getId() + " \"" + r.getStatus().getGoal().getName() + "\"")
			.collect(Collectors.toList()));
		for (String line : AdviceDiagnostics.lines(advice))
		{
			System.out.println("  " + line);
		}
	}

	/** The mid-game group ironman account described on {@link #midGameAccountDoesNotSeeEndgameBossesAsReadyOrPicked}. */
	private static SnapshotBuilder midGameAccount()
	{
		SnapshotBuilder builder = new SnapshotBuilder()
			.skill(Skill.ATTACK, 92)
			.skill(Skill.STRENGTH, 92)
			.skill(Skill.DEFENCE, 78)
			.skill(Skill.HITPOINTS, 88)
			.skill(Skill.RANGED, 72)
			.skill(Skill.MAGIC, 77)
			.skill(Skill.PRAYER, 45)
			.skill(Skill.COOKING, 69).skill(Skill.WOODCUTTING, 69).skill(Skill.FLETCHING, 69).skill(Skill.FISHING, 69)
			.skill(Skill.FIREMAKING, 69).skill(Skill.CRAFTING, 69).skill(Skill.SMITHING, 69).skill(Skill.MINING, 69)
			.skill(Skill.HERBLORE, 69).skill(Skill.AGILITY, 69).skill(Skill.THIEVING, 69)
			.skill(Skill.SLAYER, 68).skill(Skill.FARMING, 68).skill(Skill.RUNECRAFT, 68).skill(Skill.HUNTER, 68).skill(Skill.CONSTRUCTION, 68)
			.quest(Quest.PERILOUS_MOONS, QuestState.FINISHED)
			.quest(Quest.PRIEST_IN_PERIL, QuestState.FINISHED)
			.inventoryItem(7462, "Barrows gloves", 1)   // in GWD/Bandos's recommended.gearOwnedAny (2 of 7 owned - short of the min 4).
			.inventoryItem(9185, "Rune crossbow", 1)    // ditto.
			.inventoryItem(27008, "Dragon defender (t)", 1) // a Dragon defender variant; not in GWD/Bandos/Moons's gear lists.
			// Common early/mid-game gear a combat-105 account has long since picked up - owned so these
			// zero/low-requirement milestones don't crowd out the GWD-vs-Moons comparison under test.
			.inventoryItem(6570, "Fire cape", 1)
			.inventoryItem(11865, "Slayer helmet (i)", 1)
			.inventoryItem(10551, "Fighter torso", 1);
		Quest[] quests = Quest.values();
		// 186 of 211 bundled quests finished (plus Perilous Moons and Priest in Peril explicitly
		// above): short of the questsFinished >= 190 stage-3 threshold, but high enough that the
		// remaining unfinished quests don't flood the ranked list with unrelated "ready" candidates
		// that would otherwise obscure the GWD-vs-Moons comparison this test is actually about.
		for (int i = 0; i < 186; i++)
		{
			builder.quest(quests[i], QuestState.FINISHED);
		}
		return builder;
	}

	/** Five unfinished quests so ranking/pick3/rest have more than three candidates to split across. Real ids/names so each resolves to a real {@link net.runelite.api.Quest} constant. */
	private static KnowledgeBase fiveQuestKb()
	{
		return new KbBuilder()
			.quest(0, "Animal Magnetism").skill(Skill.WOODCUTTING, 10)
			.quest(9, "Biohazard").skill(Skill.WOODCUTTING, 20)
			.quest(14, "Clock Tower").skill(Skill.WOODCUTTING, 30)
			.quest(15, "Cold War").skill(Skill.WOODCUTTING, 40)
			.quest(16, "Contact!").skill(Skill.WOODCUTTING, 50)
			.build();
	}

	private static Set<String> ids(List<RankedGoal> goals)
	{
		return goals.stream().map(EngineAdviceTest::id).collect(Collectors.toCollection(LinkedHashSet::new));
	}

	private static String id(RankedGoal r)
	{
		return r.getStatus().getGoal().getId();
	}
}
