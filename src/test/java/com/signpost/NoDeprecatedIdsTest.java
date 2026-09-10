package com.signpost;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Forbids the deprecated {@code Varbits}, {@code VarPlayer} and {@code InventoryID} enums from the
 * main source tree; every id read goes through {@code net.runelite.api.gameval}.
 */
class NoDeprecatedIdsTest
{
	private static final List<String> FORBIDDEN_IMPORTS = List.of(
		"import net.runelite.api.Varbits;",
		"import net.runelite.api.VarPlayer;",
		"import net.runelite.api.InventoryID;"
	);

	@Test
	void mainSourceUsesGamevalIdsOnly() throws IOException
	{
		Path srcMain = Paths.get(System.getProperty("user.dir"), "src", "main", "java");
		try (Stream<Path> files = Files.walk(srcMain))
		{
			List<String> violations = files
				.filter(p -> p.toString().endsWith(".java"))
				.flatMap(NoDeprecatedIdsTest::violationsIn)
				.collect(Collectors.toList());
			assertTrue(violations.isEmpty(), "Deprecated id enums imported:\n" + String.join("\n", violations));
		}
	}

	private static Stream<String> violationsIn(Path file)
	{
		try
		{
			return Files.readAllLines(file).stream()
				.map(String::trim)
				.filter(FORBIDDEN_IMPORTS::contains)
				.map(line -> file + ": " + line);
		}
		catch (IOException e)
		{
			throw new java.io.UncheckedIOException(e);
		}
	}
}
