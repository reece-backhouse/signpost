package dev.reece.nta.engine;

import dev.reece.nta.engine.model.Advice;
import dev.reece.nta.engine.model.CombatLevelGap;
import dev.reece.nta.engine.model.Gap;
import dev.reece.nta.engine.model.GearGap;
import dev.reece.nta.engine.model.Goal;
import dev.reece.nta.engine.model.GoalCategory;
import dev.reece.nta.engine.model.GoalStatus;
import dev.reece.nta.engine.model.PrefsView;
import dev.reece.nta.engine.model.RankedGoal;
import dev.reece.nta.engine.model.SkillLevelGap;
import dev.reece.nta.kb.OwnedItem;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Skill;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Task 50: {@link AdviceDiagnostics#lines(Advice)} against a hand-built {@link Advice} (no real
 * {@link dev.reece.nta.engine.Engine#run} - the log-line formatting is what's under test), covering
 * a ready pick, a rest-tier pick with gaps, a later goal that also happens to be one of the two
 * watched ids, and the other watched id entirely absent from the account's goals.
 */
class AdviceDiagnosticsTest
{
	private static final Instant NOW = Instant.parse("2026-09-07T12:00:00Z");

	@Test
	void onePickLinePerPickThenOneWatchLinePerWatchedId()
	{
		GoalStatus readyStatus = status("boss:ready-pick", "Ready Pick", 1, List.of(), true, false);
		RankedGoal readyPick = new RankedGoal(readyStatus, 8.0, false, false);

		GoalStatus restStatus = status("boss:rest-pick", "Rest Pick", 1,
			List.of(new CombatLevelGap(70, 75, false)), false, false);
		RankedGoal restPick = new RankedGoal(restStatus, 3.5, false, false);

		// The account's estimated stage is 2 (below), so a stage-3 goal with no pin is "later"
		// (spec ruling 27) - also one of AdviceDiagnostics's two always-logged watch ids.
		GoalStatus laterStatus = status("boss:god-wars-dungeon", "God Wars Dungeon", 3,
			List.of(
				new GearGap(List.of(new OwnedItem("Rune crossbow", 9185), new OwnedItem("Barrows gloves", 7462)), 2, 4, false),
				new SkillLevelGap(Skill.PRAYER, 34, 43, 50_000, false, null, false)),
			false, false);
		RankedGoal laterGoal = new RankedGoal(laterStatus, 0.1, false, true);

		Advice advice = advice(
			List.of(readyStatus, restStatus, laterStatus),
			List.of(readyPick, restPick, laterGoal),
			List.of(readyPick, restPick),
			List.of(laterGoal),
			2);

		List<String> lines = AdviceDiagnostics.lines(advice);

		assertEquals(4, lines.size(), lines.toString());
		assertEquals("pick 1: boss:ready-pick \"Ready Pick\" tier=ready score=8.00 stage=1/2 gaps=0 why=\"ready why\" explain=\"Stats met: Attack 70 | Ready now\"", lines.get(0));
		assertEquals("pick 2: boss:rest-pick \"Rest Pick\" tier=rest score=3.50 stage=1/2 gaps=1 why=\"rest why\" explain=\"Missing: combat 75 (have 70)\"", lines.get(1));
		assertEquals("watch boss:moons-of-peril: done", lines.get(2));
		assertEquals("watch boss:god-wars-dungeon: tier=later score=0.10 ready=false gaps=[gear:2/4, Prayer 34/43]", lines.get(3));
	}

	private static GoalStatus status(String id, String name, int stage, List<Gap> gaps, boolean ready, boolean bankUnknown)
	{
		Goal goal = new Goal(id, GoalCategory.BOSS, name, "https://x", 8, stage);
		return new GoalStatus(goal, gaps, ready, bankUnknown, List.of());
	}

	private static Advice advice(List<GoalStatus> statuses, List<RankedGoal> ranked, List<RankedGoal> picked, List<RankedGoal> later, int accountStage)
	{
		Map<String, String> whys = new LinkedHashMap<>();
		whys.put("boss:ready-pick", "ready why");
		whys.put("boss:rest-pick", "rest why");
		whys.put("boss:god-wars-dungeon", "later why");

		Map<String, List<String>> explanations = new LinkedHashMap<>();
		explanations.put("boss:ready-pick", List.of("Stats met: Attack 70", "Ready now"));
		explanations.put("boss:rest-pick", List.of("Missing: combat 75 (have 70)"));

		PrefsView prefs = new PrefsView(Set.of(), List.of(), Set.of(), Set.of(), null);
		return new Advice(new SnapshotBuilder().build(), statuses, Map.of(), NOW, ranked, picked, List.of(), accountStage, later, whys, explanations, Map.of(), prefs, null);
	}
}
