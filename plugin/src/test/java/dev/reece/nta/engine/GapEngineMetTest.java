package dev.reece.nta.engine;

import dev.reece.nta.engine.model.GoalStatus;
import dev.reece.nta.engine.model.Met;
import dev.reece.nta.kb.KnowledgeBase;
import dev.reece.nta.kb.MilestoneCategory;
import dev.reece.nta.snapshot.DiaryTier;
import dev.reece.nta.snapshot.Snapshot;
import java.util.List;
import java.util.stream.Collectors;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Task 51 (spec ruling 29): {@link GapEngine} records met requirements alongside gaps, one {@link Met} per satisfied requirement. */
class GapEngineMetTest
{
	private final GapEngine engine = new GapEngine(new BoostTable());

	@Test
	void questRecordsMetSkillPrereqItemQuestPointsKudosAndCombat()
	{
		KnowledgeBase kb = new KbBuilder()
			.quest(9, "Biohazard")
			.quest(0, "Animal Magnetism")
			.skill(Skill.HERBLORE, 70).skill(Skill.AGILITY, 80)
			.prereq("Biohazard")
			.item("Rope", 2)
			.questPoints(30)
			.kudos(50)
			.combat(60)
			.build();
		Snapshot snapshot = new SnapshotBuilder()
			.skill(Skill.HERBLORE, 74)
			.skill(Skill.ATTACK, 70).skill(Skill.STRENGTH, 70).skill(Skill.DEFENCE, 70).skill(Skill.HITPOINTS, 70)
			.quest(Quest.BIOHAZARD, QuestState.FINISHED)
			.bankItem(954, "Rope", 3)
			.questPoints(45)
			.kudos(60)
			.build();

		GoalStatus status = find(engine.evaluate(snapshot, kb), "quest:0");

		assertEquals(List.of(
			"SKILL Herblore 70 (have 74)",
			"QUEST Biohazard",
			"ITEM Rope ×2",
			"QUEST_POINTS Quest points 30 (have 45)",
			"KUDOS Kudos 50 (have 60)",
			"COMBAT Combat 60 (have " + snapshot.combatLevel() + ")"), labels(status));
		assertEquals(1, status.getGaps().size(), "Agility 80 is still a gap: " + status.getGaps());
	}

	@Test
	void diaryTierRecordsTasksDoneCount()
	{
		KnowledgeBase kb = new KbBuilder()
			.diary(DiaryTier.VARROCK_EASY)
			.task(1, "one").completion(1176, 0)
			.task(2, "two").completion(1176, 1)
			.task(3, "three").completion(1176, 2)
			.build();
		Snapshot snapshot = new SnapshotBuilder().diaryVarp(1176, 0b011).build();

		GoalStatus status = find(engine.evaluate(snapshot, kb), "diary:VARROCK_EASY");

		assertEquals(1, status.getMet().size(), labels(status).toString());
		Met met = status.getMet().get(0);
		assertEquals(Met.Kind.DIARY, met.getKind());
		assertEquals("2 of 3 tasks done", met.getLabel());
		assertEquals(2, met.getCount());
		assertEquals(3, met.getRequired());
	}

	@Test
	void milestoneRecordsEntryAndRecommendedMetIncludingOwnedGearNamesBelowTheMinimum()
	{
		KnowledgeBase kb = new KbBuilder()
			.quest(9, "Biohazard")
			.milestone("boss:test", MilestoneCategory.BOSS, "Test Boss", 8)
			.skill(Skill.SLAYER, 50)
			.quest("Biohazard")
			.diary(DiaryTier.VARROCK_EASY)
			.recommendedSkill(Skill.ATTACK, 70)
			.recommendedSkill(Skill.RANGED, 85)
			.recommendedCombat(90)
			.recommendedGear("Rune crossbow", 9185)
			.recommendedGear("Karil's coif", 4732)
			.recommendedGear("Bandos chestplate", 11832)
			.recommendedGear("Barrows gloves", 7462)
			.recommendedGearOwnedMin(3)
			.build();
		Snapshot snapshot = new SnapshotBuilder()
			.skill(Skill.SLAYER, 60)
			.skill(Skill.ATTACK, 92).skill(Skill.STRENGTH, 92).skill(Skill.DEFENCE, 92).skill(Skill.HITPOINTS, 92)
			.skill(Skill.RANGED, 70)
			.quest(Quest.BIOHAZARD, QuestState.FINISHED)
			.diaryTier(DiaryTier.VARROCK_EASY, true)
			.inventoryItem(9185, "Rune crossbow", 1)
			.bankItem(7462, "Barrows gloves", 1)
			.build();

		GoalStatus status = find(engine.evaluate(snapshot, kb), "boss:test");

		assertEquals(List.of(
			"SKILL Slayer 50 (have 60)",
			"QUEST Biohazard",
			"DIARY Varrock Easy Diary",
			"RECOMMENDED_SKILL Attack 70 (have 92)",
			"COMBAT Combat 90 (have " + snapshot.combatLevel() + ")",
			"RECOMMENDED_GEAR Rune crossbow",
			"RECOMMENDED_GEAR Barrows gloves"), labels(status));
		List<Met> gear = status.getMet().stream().filter(m -> m.getKind() == Met.Kind.RECOMMENDED_GEAR).collect(Collectors.toList());
		assertEquals(2, gear.get(0).getCount(), "owned count on every gear entry");
		assertEquals(3, gear.get(0).getRequired(), "effective minimum on every gear entry");
		assertTrue(status.getGaps().stream().anyMatch(g -> g instanceof dev.reece.nta.engine.model.GearGap), "2 of min 3 owned is still a gear gap");
	}

	private static List<String> labels(GoalStatus status)
	{
		return status.getMet().stream().map(m -> m.getKind() + " " + m.getLabel()).collect(Collectors.toList());
	}

	private static GoalStatus find(List<GoalStatus> statuses, String id)
	{
		return statuses.stream().filter(s -> s.getGoal().getId().equals(id)).findFirst().orElseThrow();
	}
}
