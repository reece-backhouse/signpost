package dev.reece.nta.engine;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

/**
 * Runs work off the client/EDT threads on a single background thread, discarding any result whose
 * generation has been superseded by a newer {@link #submit} call before the work finished.
 * Lifecycle work that must never be discarded (account/KB loads) goes through {@link #execute},
 * which shares the same FIFO thread but takes no part in the generation race.
 */
@Slf4j
public class EngineRunner
{
	private final ExecutorService executor = Executors.newSingleThreadExecutor(r ->
	{
		Thread thread = new Thread(r, "nta-engine");
		thread.setDaemon(true);
		return thread;
	});

	private final AtomicInteger generation = new AtomicInteger();
	private final Consumer<Throwable> onError;

	public EngineRunner()
	{
		this(e -> log.error("engine task failed", e));
	}

	/** Test seam: every throwable a task raises is handed to {@code onError} instead of the log. */
	EngineRunner(Consumer<Throwable> onError)
	{
		this.onError = onError;
	}

	/**
	 * Runs {@code work} on the background thread, then calls {@code onResult} with its return value
	 * only if no newer {@link #submit} call (and no {@link #shutdown()}) has happened since. A
	 * throwable from either is reported (final-review C2: never swallowed into the executor's
	 * unread Future) and does not affect later tasks.
	 */
	public <T> void submit(Supplier<T> work, Consumer<T> onResult)
	{
		int thisGeneration = generation.incrementAndGet();
		executor.execute(() -> guarded(() ->
		{
			T result = work.get();
			if (generation.get() == thisGeneration)
			{
				onResult.accept(result);
			}
		}));
	}

	/**
	 * Runs {@code task} on the background thread in FIFO order with everything else, unconditionally:
	 * no generation is taken, so a later {@link #submit} cannot cause it to be skipped (final-review
	 * I6). Use for loads whose side effects later tasks depend on.
	 */
	public void execute(Runnable task)
	{
		executor.execute(() -> guarded(task));
	}

	private void guarded(Runnable task)
	{
		try
		{
			task.run();
		}
		catch (Throwable e)
		{
			onError.accept(e);
		}
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
