package dev.reece.nta.engine;

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
	 * Regression test for the user feedback that motivated S4.1 (spec ruling 27): against the
	 * bundled real knowledge base, a mid-game account (combat ~103, several dozen quests, no raid
	 * gear) must not see Theatre of Blood or the infernal cape (both stage 4) picked or "ready", and
	 * Chambers of Xeric (stage 3, recommended stats/gear far above this account's) must not be
	 * "ready" either - while the account's actual stage-2 boss (Moons of Peril, whose recommended
	 * profile this account meets) is not excluded as "later".
	 */
	@Test
	void midGameAccountDoesNotSeeEndgameBossesAsReadyOrPicked()
	{
		KnowledgeBase kb = KnowledgeBase.load(new Gson());
		SnapshotBuilder builder = new SnapshotBuilder()
			.skill(Skill.ATTACK, 88)
			.skill(Skill.STRENGTH, 88)
			.skill(Skill.DEFENCE, 75)
			.skill(Skill.HITPOINTS, 85)
			.skill(Skill.RANGED, 70)
			.skill(Skill.MAGIC, 75)
			.skill(Skill.PRAYER, 43)
			.quest(Quest.PERILOUS_MOONS, QuestState.FINISHED)
			.quest(Quest.PRIEST_IN_PERIL, QuestState.FINISHED)
			.inventoryItem(3140, "Dragon chainbody", 1); // meets Moons of Peril's recommended gearOwnedAny, not CoX/ToB's.
		Quest[] quests = Quest.values();
		for (int i = 0; i < 150; i++)
		{
			if (quests[i] != Quest.PERILOUS_MOONS && quests[i] != Quest.PRIEST_IN_PERIL)
			{
				builder.quest(quests[i], QuestState.FINISHED);
			}
		}
		Snapshot snapshot = builder.build();

		Advice advice = engine.run(snapshot, kb, AccountData.empty(), now);

		assertEquals(2, advice.getAccountStage(), "sanity check on the account fixture itself");

		Set<String> pickedIds = ids(advice.getPicked());
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
