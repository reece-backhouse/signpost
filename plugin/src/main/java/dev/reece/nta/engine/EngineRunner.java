package dev.reece.nta.engine;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Runs work off the client/EDT threads on a single background thread, discarding any result whose
 * generation has been superseded by a newer {@link #submit} call before the work finished.
 */
public class EngineRunner
{
	private final ExecutorService executor = Executors.newSingleThreadExecutor(r ->
	{
		Thread thread = new Thread(r, "nta-engine");
		thread.setDaemon(true);
		return thread;
	});

	private final AtomicInteger generation = new AtomicInteger();

	/**
	 * Runs {@code work} on the background thread, then calls {@code onResult} with its return value
	 * only if no newer {@link #submit} call (and no {@link #shutdown()}) has happened since.
	 */
	public <T> void submit(Supplier<T> work, Consumer<T> onResult)
	{
		int thisGeneration = generation.incrementAndGet();
		executor.submit(() ->
		{
			T result = work.get();
			if (generation.get() == thisGeneration)
			{
				onResult.accept(result);
			}
		});
	}

	/**
	 * Invalidates any in-flight work so its result cannot be delivered, then stops the background
	 * thread.
	 */
	public void shutdown()
	{
		generation.incrementAndGet();
		executor.shutdown();
	}
}
