package com.signpost.kb;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CuratedContentTest
{
	private static final List<String> BANNED = List.of(
		"choose whichever", "your best", "if you have", "as needed", "etc.");

	@Test
	void curatedInstructionsAreConcreteAndNameTheirRequirements() throws Exception
	{
		List<String> violations = new ArrayList<>();
		for (String file : List.of("milestones", "gathering", "priorities"))
		{
			try (InputStream stream = getClass().getResourceAsStream("/kb/" + file + ".json"))
			{
				assertNotNull(stream, "Missing bundled " + file + ".json");
				try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8))
				{
					scan(new JsonParser().parse(reader), file + ".json", false, violations);
				}
			}
		}
		assertTrue(violations.isEmpty(), "Replace vague instructions with concrete actions; move gates to structured requirements:\n"
			+ String.join("\n", violations));
	}

	@Test
	void scannerNamesEntryAndNestedStepForEveryBannedPhrase()
	{
		for (String phrase : BANNED)
		{
			JsonObject step = new JsonObject();
			step.addProperty("text", phrase.toUpperCase(Locale.ROOT));
			JsonObject alternative = new JsonObject();
			alternative.add("steps", new com.google.gson.JsonArray());
			alternative.getAsJsonArray("steps").add(step);
			JsonObject plan = new JsonObject();
			plan.addProperty("id", 223);
			plan.add("alternatives", new com.google.gson.JsonArray());
			plan.getAsJsonArray("alternatives").add(alternative);
			List<String> violations = new ArrayList<>();
			scan(plan, "gathering.json", false, violations);
			assertEquals(1, violations.size());
			assertTrue(violations.get(0).contains("[id=223].alternatives[0].steps[0].text"), violations.toString());
			assertTrue(violations.get(0).contains(phrase), violations.toString());
		}
	}

	@Test
	void scannerIncludesBareStepsNestedNotesAndPriorityReasonIds()
	{
		List<String> violations = new ArrayList<>();
		scan(new JsonParser().parse("{\"reasons\":{\"quest:1\":\"your best\"},"
			+ "\"plans\":[{\"id\":2,\"steps\":[\"as needed\"],\"requires\":{\"notes\":\"etc.\"}}]}"),
			"fixture", false, violations);
		assertEquals(3, violations.size());
		assertTrue(violations.stream().anyMatch(v -> v.contains("reasons.quest:1")), violations.toString());
		assertTrue(violations.stream().anyMatch(v -> v.contains("[id=2].steps[0]")), violations.toString());
		assertTrue(violations.stream().anyMatch(v -> v.contains("[id=2].requires.notes")), violations.toString());
	}

	private static void scan(JsonElement value, String path, boolean text, List<String> violations)
	{
		if (value.isJsonNull())
		{
			return;
		}
		if (value.isJsonPrimitive())
		{
			if (text && value.getAsJsonPrimitive().isString())
			{
				String normalized = value.getAsString().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
				for (String phrase : BANNED)
				{
					if (normalized.contains(phrase))
					{
						violations.add(path + ": banned phrase \"" + phrase + "\" in \"" + value.getAsString() + "\"");
					}
				}
			}
			return;
		}
		if (value.isJsonArray())
		{
			int index = 0;
			for (JsonElement element : value.getAsJsonArray())
			{
				scan(element, path + "[" + index++ + "]", text, violations);
			}
			return;
		}
		JsonObject object = value.getAsJsonObject();
		String entryPath = object.has("id") ? path + "[id=" + object.get("id").getAsString() + "]" : path;
		for (Map.Entry<String, JsonElement> field : object.entrySet())
		{
			String key = field.getKey();
			boolean curatedText = text || key.equals("reason") || key.equals("reasons")
				|| key.equals("notes") || key.equals("steps");
			scan(field.getValue(), entryPath + "." + key, curatedText, violations);
		}
	}
}
