package dev.reece.nta.engine;

import dev.reece.nta.engine.model.Route;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** RL-012 AC3/AC4 (spec ruling 34): the "about N min at Rk/h" suffix and which xp it counts. */
class EtaTest
{
	@Test
	void textRoundsMinutesUpAndRateToThousands()
	{
		assertEquals("about 45 min at 38k/h", Eta.text(28_400, 38_250L));
		assertEquals("about 1 min at 900/h", Eta.text(1, 900L));
		assertEquals("about 2 h 30 min at 100k/h", Eta.text(250_000, 100_000L));
		assertEquals("about 2 h at 100k/h", Eta.text(200_000, 100_000L));
	}

	@Test
	void noTextWithoutARateOrWithNothingLeft()
	{
		assertNull(Eta.text(28_400, null));
		assertNull(Eta.text(28_400, 0L));
		assertNull(Eta.text(0, 38_000L));
	}

	@Test
	void remainingXpIsTheRouteUncoveredXpWhenARouteExistsElseTheGap()
	{
		assertEquals(12_000L, Eta.remainingXp(new Route(List.of(), 12_000L, 0L, Map.of()), 50_000L));
		assertEquals(50_000L, Eta.remainingXp(null, 50_000L));
	}
}
