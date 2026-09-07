package dev.reece.nta.store;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;

/**
 * Reads and writes {@link AccountData} as JSON under a per-account file: {@code <dir>/<accountHash>.json}.
 * Saves are atomic (write to a temp file, then move); a missing or corrupt file loads as
 * {@link AccountData#empty()} rather than failing.
 */
@Slf4j
public class AccountStore
{
	private final Path dir;
	private final Gson gson;

	public AccountStore(Path dir, Gson gson)
	{
		this.dir = dir;
		this.gson = gson.newBuilder()
			.registerTypeAdapter(Instant.class, new InstantTypeAdapter())
			.create();
	}

	public AccountData load(long accountHash)
	{
		Path file = fileFor(accountHash);
		if (!Files.exists(file))
		{
			return AccountData.empty();
		}

		try
		{
			String json = Files.readString(file);
			AccountData data = gson.fromJson(json, AccountData.class);
			return data == null ? AccountData.empty() : data;
		}
		catch (IOException | JsonParseException e)
		{
			log.warn("Failed to load account data from {}: {}", file, e.getMessage());
			return AccountData.empty();
		}
	}

	public void save(long accountHash, AccountData data)
	{
		Path file = fileFor(accountHash);
		try
		{
			Files.createDirectories(dir);
			Path tmp = Files.createTempFile(dir, file.getFileName().toString(), ".tmp");
			Files.writeString(tmp, gson.toJson(data));
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		}
		catch (IOException e)
		{
			throw new UncheckedIOException("Failed to save account data to " + file, e);
		}
	}

	private Path fileFor(long accountHash)
	{
		return dir.resolve(accountHash + ".json");
	}
}
