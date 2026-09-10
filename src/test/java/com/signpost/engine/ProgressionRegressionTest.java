package com.signpost.engine;

import com.google.gson.Gson;
import com.signpost.engine.model.Advice;
import com.signpost.engine.model.GoalStatus;
import com.signpost.engine.model.RewardTarget;
import com.signpost.kb.KnowledgeBase;
import com.signpost.kb.RewardValue;
import com.signpost.snapshot.Snapshot;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProgressionRegressionTest
{
	private final KnowledgeBase kb = KnowledgeBase.load(new Gson());
	private final Engine engine = new Engine(new BoostTable());

	private Advice run(Snapshot snapshot)
	{
		return engine.run(snapshot, kb, ProgressionFixture.preferences(), Instant.parse("2026-09-09T12:00:00Z"));
	}

	private static GoalStatus status(Advice advice, String id)
	{
		return advice.getStatuses().stream().filter(s -> s.getGoal().getId().equals(id)).findFirst().orElseThrow();
	}

	@Test
	void knownSuperiorEquipmentRemovesDowngradesAndSatisfiesCoherentRaidLoadouts()
	{
		Advice advice = run(ProgressionFixture.snapshot().toBuilder().rigour(true).augury(true).build());
		Set<String> obsolete = Set.of("milestone:fighter-torso", "milestone:dragon-boots", "milestone:infinity-boots",
			"milestone:amulet-of-glory", "milestone:amulet-of-magic", "milestone:avas-accumulator",
			"milestone:imbued-god-cape", "milestone:bow-of-faerdhinen", "milestone:ash-sanctifier",
			"gear:melee:feet:11840", "gear:magic:feet:6920", "gear:melee:neck:1704");
		assertTrue(advice.getStatuses().stream().noneMatch(s -> obsolete.contains(s.getGoal().getId())),
			() -> advice.getStatuses().stream().filter(s -> obsolete.contains(s.getGoal().getId())).map(s -> s.getGoal().getName()).collect(Collectors.joining(", ")));
		for (String boss : List.of("boss:chambers-of-xeric", "boss:tombs-of-amascut", "boss:theatre-of-blood"))
		{
			assertTrue(status(advice, boss).isReady(), () -> boss + ": " + status(advice, boss).getGaps());
		}
	}

	@Test
	void unseenUtilityItemsAreNotInventedNeedsButExplicitlyMissingAvernicRemainsAnObjective()
	{
		Advice advice = run(ProgressionFixture.snapshot());
		Set<String> unknownUtilities = Set.of("milestone:bonecrusher", "milestone:raiments-of-the-eye");
		assertTrue(advice.getPicked().stream().noneMatch(r -> unknownUtilities.contains(r.getStatus().getGoal().getId())));
		for (String id : unknownUtilities)
		{
			assertTrue(status(advice, id).getObjective().getRewards().stream().allMatch(RewardTarget::isOwnershipUnknown));
		}
		assertTrue(status(advice, "milestone:avernic-defender").getObjective().getRewards().stream()
			.anyMatch(r -> r.getName().contains("Avernic") && !r.isOwnershipUnknown()));
		assertTrue(advice.getStatuses().stream().noneMatch(s -> s.getGoal().getId().equals("boss:moons-of-peril")));
	}

	@Test
	void marginalBladeAndBowfaContextualMasoriDoNotDisplaceMajorRaidGains()
	{
		// A complete-bank control, distinct from the fixture's partial-bank scenario.
		Advice advice = run(ProgressionFixture.snapshot().toBuilder().bankKnown(true).build());
		var gauntlet = status(advice, "boss:corrupted-gauntlet").getObjective();
		assertEquals(List.of("Blade of Saeldor"), gauntlet.getRewards().stream().map(RewardTarget::getName).collect(Collectors.toList()));
		assertEquals(RewardValue.SITUATIONAL, gauntlet.getValue());
		assertTrue(status(advice, "milestone:masori-armour").getObjective().getRewards().stream()
			.allMatch(r -> r.getValue() == RewardValue.SITUATIONAL));
		assertEquals(RewardValue.SITUATIONAL, status(advice, "boss:barrows").getObjective().getRewards().stream()
			.filter(r -> r.getName().equals("Karil's leathertop")).findFirst().orElseThrow().getValue(),
			"magic-defence sidegrades must not compete as primary ranged upgrades against Bowfa and full crystal");
		List<String> ranked = advice.getRanked().stream().map(r -> r.getStatus().getGoal().getId()).collect(Collectors.toList());
		assertTrue(ranked.indexOf("boss:tombs-of-amascut") < ranked.indexOf("boss:corrupted-gauntlet"));
		assertTrue(ranked.indexOf("boss:chambers-of-xeric") < ranked.indexOf("boss:corrupted-gauntlet"));
	}

	@Test
	void sharedAndFortifiedCopiesOfOneMasoriSlotCannotCompleteTheSet()
	{
		Snapshot base = ProgressionFixture.snapshot().toBuilder().bankKnown(true).groupStorageKnown(true)
			.groupStorage(Map.of(27226, 1, 27235, 1, 27229, 1)).build();
		Advice before = run(base);
		assertEquals(List.of("Masori chaps"), status(before, "milestone:masori-armour").getObjective().getRewards()
			.stream().map(RewardTarget::getName).collect(Collectors.toList()));
		Map<Integer, Integer> completed = new HashMap<>(base.getGroupStorage());
		completed.put(27241, 1);
		assertTrue(run(base.toBuilder().groupStorage(completed).build()).getStatuses().stream()
			.noneMatch(s -> s.getGoal().getId().equals("milestone:masori-armour")));
	}
}
