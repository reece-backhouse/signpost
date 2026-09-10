package com.signpost.kb;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GearLadderTest
{
	@Test
	void rejectsUnknownProvidersAndReversedTierProgression() throws Exception
	{
		Gson gson = new Gson();
		KnowledgeBase kb = KnowledgeBase.load(gson);
		JsonObject source;
		try (InputStreamReader reader = new InputStreamReader(getClass().getResourceAsStream("/kb/gear-ladders.json"), StandardCharsets.UTF_8))
		{
			source = gson.fromJson(reader, JsonObject.class);
		}
		JsonObject invalid = source.deepCopy();
		invalid.getAsJsonArray("ladders").get(0).getAsJsonObject().getAsJsonObject("slots")
			.getAsJsonArray("body").get(0).getAsJsonObject().addProperty("provider", "milestone:missing");
		IllegalStateException missing = assertThrows(IllegalStateException.class,
			() -> GearLadder.parse(gson.fromJson(invalid, GearLadder.Data.class), kb.getQuests(), kb.getMilestones()));
		assertTrue(missing.getMessage().contains("unknown provider milestone:missing"));

		JsonArray budget = source.getAsJsonArray("ladders").get(0).getAsJsonObject().getAsJsonObject("slots").getAsJsonArray("body");
		JsonArray reversed = new JsonArray();
		reversed.add(budget.get(1));
		reversed.add(budget.get(0));
		source.getAsJsonArray("ladders").get(1).getAsJsonObject().getAsJsonObject("slots").add("body", reversed);
		IllegalStateException cycle = assertThrows(IllegalStateException.class,
			() -> GearLadder.parse(gson.fromJson(source, GearLadder.Data.class), kb.getQuests(), kb.getMilestones()));
		assertTrue(cycle.getMessage().contains("rung order"));
	}

	@Test
	void cosmeticAliasesAndCuratedReplacementsDoNotOrderIncomparableWeapons()
	{
		GearCatalog catalog = KnowledgeBase.load(new Gson()).getGearCatalog();
		assertEquals(catalog.canonicalId(25867), catalog.canonicalId(33021));
		assertEquals(catalog.canonicalId(23975), catalog.canonicalId(33023));
		assertTrue(catalog.covers(13239, 3105));
		assertTrue(catalog.covers(13235, 4097));
		assertTrue(catalog.covers(29804, 1704));
		assertTrue(catalog.covers(11832, 29022));
		assertFalse(catalog.covers(29796, 23995));
		assertFalse(catalog.covers(26219, 29796));
		assertFalse(catalog.covers(27238, 23975));
		assertFalse(catalog.covers(11832, 11834));
		assertFalse(catalog.covers(12932, 12899), "A magic fang is not an equipped trident");
	}

	@Test
	void rejectsAliasConflictsUnknownTargetsAndReplacementCycles()
	{
		Gson gson = new Gson();
		String first = "{\"id\":1,\"name\":\"First\",\"variants\":[1],\"style\":\"melee\",\"slot\":\"weapon\",\"benefit\":\"Slash\",\"replaces\":[2]}";
		String second = "{\"id\":2,\"name\":\"Second\",\"variants\":[2],\"style\":\"melee\",\"slot\":\"weapon\",\"benefit\":\"Stab\",\"replaces\":[]}";
		for (String invalid : List.of(
			second.replace("\"variants\":[2]", "\"variants\":[1,2]"),
			second.replace("\"replaces\":[]", "\"replaces\":[1]"),
			second.replace("\"replaces\":[]", "\"replaces\":[999]")))
		{
			GearLadder.Data data = gson.fromJson("{\"ladders\":[],\"equipment\":[" + first + "," + invalid + "]}", GearLadder.Data.class);
			assertThrows(IllegalStateException.class, () -> new GearCatalog(List.of(), data, List.of()));
		}
	}
}
