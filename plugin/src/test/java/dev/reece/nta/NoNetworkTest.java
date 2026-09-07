package dev.reece.nta;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Forbids network I/O imports from the plugin's main source tree. The plugin
 * makes no network calls in v1 (see CLAUDE.md); this test keeps it that way.
 */
class NoNetworkTest
{
	private static final List<String> FORBIDDEN_IMPORTS = List.of(
		"java.net.http",
		"java.net.URL",
		"java.net.HttpURLConnection",
		"okhttp3",
		"java.net.Socket"
	);

	private static final Pattern IMPORT_LINE = Pattern.compile("^\\s*import\\s+(?:static\\s+)?([^;\\s]+)");

	@Test
	void mainSourceHasNoNetworkImports() throws IOException
	{
		Path srcMain = Paths.get(System.getProperty("user.dir"), "src", "main", "java");
		if (!Files.exists(srcMain))
		{
			return;
		}

		try (Stream<Path> files = Files.walk(srcMain))
		{
			List<String> violations = files
				.filter(p -> p.toString().endsWith(".java"))
				.flatMap(NoNetworkTest::violationsIn)
				.collect(Collectors.toList());

			assertTrue(violations.isEmpty(), "Forbidden network imports found:\n" + String.join("\n", violations));
		}
	}

	private static Stream<String> violationsIn(Path file)
	{
		try
		{
			List<String> lines = Files.readAllLines(file);
			return java.util.stream.IntStream.range(0, lines.size())
				.filter(i -> isForbiddenImport(lines.get(i)))
				.mapToObj(i -> file + ":" + (i + 1) + ": " + lines.get(i).trim());
		}
		catch (IOException e)
		{
			throw new UncheckedIOException(e);
		}
	}

	private static boolean isForbiddenImport(String line)
	{
		var matcher = IMPORT_LINE.matcher(line);
		if (!matcher.find())
		{
			return false;
		}
		String imported = matcher.group(1);
		boolean wildcard = imported.endsWith(".*");
		if (wildcard)
		{
			imported = imported.substring(0, imported.length() - 2);
		}
		String target = imported;
		return FORBIDDEN_IMPORTS.stream().anyMatch(f ->
			f.equals(target) || target.startsWith(f + ".") || (wildcard && f.startsWith(target + ".")));
	}
}
