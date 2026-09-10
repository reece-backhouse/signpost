package com.signpost.engine;

import com.signpost.engine.model.GoalStatus;
import com.signpost.engine.model.Met;
import com.signpost.kb.KnowledgeBase;
import com.signpost.kb.MilestoneCategory;
import com.signpost.snapshot.DiaryTier;
import com.signpost.snapshot.Snapshot;
import java.util.List;
import java.util.stream.Collectors;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link GapEngine} records met requirements alongside gaps, one {@link Met} per satisfied requirement. */
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


	private static List<String> labels(GoalStatus status)
	{
		return status.getMet().stream().map(m -> m.getKind() + " " + m.getLabel()).collect(Collectors.toList());
	}

	private static GoalStatus find(List<GoalStatus> statuses, String id)
	{
		return statuses.stream().filter(s -> s.getGoal().getId().equals(id)).findFirst().orElseThrow();
	}
}
