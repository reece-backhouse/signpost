package dev.reece.nta.engine;

import dev.reece.nta.engine.model.ItemSource;
import dev.reece.nta.engine.model.Route;
import dev.reece.nta.engine.model.Shortfall;
import dev.reece.nta.engine.model.ShortfallItem;
import dev.reece.nta.kb.KnowledgeBase;
import dev.reece.nta.snapshot.AccountType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Skill;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Task 35: {@link ShortfallResolver}, ticket C7. */
class ShortfallResolverTest
{
	private static final int TOADFLAX_UNF = 100;
	private static final int CRUSHED_NEST = 101;
	private static final int TOADFLAX = 102;
	private static final int VIAL_OF_WATER = 103;

	@Test
	void emptyShortfallWhenTheRouteAlreadyCoversTheFullXpDelta()
	{
		KnowledgeBase kb = new KbBuilder().build();
		Route route = new Route(List.of(), 0, 1000, Map.of());

		Shortfall shortfall = ShortfallResolver.resolve(Skill.HERBLORE, route, kb, AccountType.NORMAL);

		assertNull(shortfall.getMethod());
		assertTrue(shortfall.getItems().isEmpty());
	}

	@Test
	void resolvesTheToadflaxChainFromTheTicket()
	{
		KnowledgeBase kb = new KbBuilder()
			.method(Skill.HERBLORE, "Saradomin brew(3)", 1, 180)
			.material(TOADFLAX_UNF, 1)
			.material(CRUSHED_NEST, 1)
			.material("Toadflax potion (unf)", TOADFLAX_UNF)
			.source("craft", "Toadflax potion (unf)")
			.material("Crushed nest", CRUSHED_NEST)
			.source("GE", "Grand Exchange")
			.material("Toadflax", TOADFLAX)
			.source("GE", "Grand Exchange")
			.source("drop", "Hazelmere")
			.source("craft", "Toadflax")
			.material("Vial of water", VIAL_OF_WATER)
			.source("GE", "Grand Exchange")
			.source("shop", "General Store")
			.method(Skill.HERBLORE, "Toadflax potion (unf)", 1, 0)
			.material(TOADFLAX, 1)
			.material(VIAL_OF_WATER, 1)
			.output(TOADFLAX_UNF, 1)
			.intermediate()
			.build();

		Map<Integer, Integer> simulatedBank = new HashMap<>();
		simulatedBank.put(TOADFLAX_UNF, 0);
		simulatedBank.put(CRUSHED_NEST, 1000);
		simulatedBank.put(TOADFLAX, 0);
		simulatedBank.put(VIAL_OF_WATER, 1200);
		Route route = new Route(List.of(), 540, 0, simulatedBank);

		Shortfall shortfall = ShortfallResolver.resolve(Skill.HERBLORE, route, kb, AccountType.NORMAL);

		assertEquals("Saradomin brew(3)", shortfall.getMethod().getName());
		assertEquals(2, shortfall.getItems().size());

		ShortfallItem unf = itemNamed(shortfall.getItems(), "Toadflax potion (unf)");
		assertEquals(0, unf.getHave());
		assertEquals(3, unf.getNeed());
		assertEquals(2, unf.getCraftFrom().size());

		ShortfallItem toadflax = itemNamed(unf.getCraftFrom(), "Toadflax");
		assertEquals(0, toadflax.getHave());
		assertEquals(3, toadflax.getNeed());
		assertTrue(toadflax.getCraftFrom().isEmpty());
		assertEquals(3, toadflax.getSources().size());

		ShortfallItem vial = itemNamed(unf.getCraftFrom(), "Vial of water");
		assertEquals(1200, vial.getHave());
		assertEquals(3, vial.getNeed());
		assertTrue(vial.getCraftFrom().isEmpty());

		ShortfallItem nest = itemNamed(shortfall.getItems(), "Crushed nest");
		assertEquals(1000, nest.getHave());
		assertEquals(3, nest.getNeed());
		assertTrue(nest.getCraftFrom().isEmpty(), "a fully-stocked material needs no craft recursion");
	}

	@Test
	void aFullyStockedMaterialHasNoShortfallAndNoCraftRecursionEvenWithACraftSource()
	{
		KnowledgeBase kb = new KbBuilder()
			.method(Skill.HERBLORE, "Only method", 1, 10)
			.material(1, 1)
			.material("Plenty", 1)
			.source("craft", "Plenty")
			.build();
		Map<Integer, Integer> simulatedBank = Map.of(1, 1000);
		Route route = new Route(List.of(), 10, 0, simulatedBank);

		Shortfall shortfall = ShortfallResolver.resolve(Skill.HERBLORE, route, kb, AccountType.NORMAL);

		ShortfallItem item = shortfall.getItems().get(0);
		assertEquals("https://oldschool.runescape.wiki/w/" + item.getItem().getName().replace(' ', '_'), item.getWikiUrl());
		assertEquals(1000, item.getHave());
		assertEquals(1, item.getNeed());
		assertTrue(item.getCraftFrom().isEmpty());
	}

	@Test
	void geSourcesAreDroppedForIronAccountsButKeptForNormal()
	{
		KnowledgeBase kb = new KbBuilder()
			.method(Skill.HERBLORE, "Only method", 1, 10)
			.material(1, 1)
			.material("Scarce", 1)
			.source("GE", "Grand Exchange")
			.source("drop", "Some Boss")
			.build();
		Map<Integer, Integer> simulatedBank = Map.of();
		Route route = new Route(List.of(), 10, 0, simulatedBank);

		Shortfall normal = ShortfallResolver.resolve(Skill.HERBLORE, route, kb, AccountType.NORMAL);
		Shortfall iron = ShortfallResolver.resolve(Skill.HERBLORE, route, kb, AccountType.IRONMAN);

		assertEquals(2, normal.getItems().get(0).getSources().size());
		assertEquals(1, iron.getItems().get(0).getSources().size());
		assertEquals("drop", iron.getItems().get(0).getSources().get(0).getType());
	}

	private static ShortfallItem itemNamed(List<ShortfallItem> items, String name)
	{
		return items.stream().filter(i -> i.getItem().getName().equals(name)).findFirst()
			.orElseThrow(() -> new AssertionError("no shortfall item named " + name));
	}
}
