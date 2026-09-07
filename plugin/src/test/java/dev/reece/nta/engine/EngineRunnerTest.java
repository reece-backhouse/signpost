package dev.reece.nta.engine;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngineRunnerTest
{
	@Test
	void staleResultIsDiscardedWhenNewerWorkWasSubmitted() throws InterruptedException
	{
		EngineRunner runner = new EngineRunner();
		CountDownLatch firstStarted = new CountDownLatch(1);
		CountDownLatch releaseFirst = new CountDownLatch(1);
		AtomicReference<String> delivered = new AtomicReference<>();
		AtomicInteger deliveries = new AtomicInteger();

		runner.submit(() ->
		{
			firstStarted.countDown();
			await(releaseFirst);
			return "first";
		}, result ->
		{
			delivered.set(result);
			deliveries.incrementAndGet();
		});

		assertTrue(firstStarted.await(5, TimeUnit.SECONDS), "first work did not start");

		// The executor is single-threaded, so this queues behind the still-blocked first task
		// rather than running immediately - that's fine, we only need it submitted before release.
		CountDownLatch secondDelivered = new CountDownLatch(1);
		runner.submit(() -> "second", result ->
		{
			delivered.set(result);
			deliveries.incrementAndGet();
			secondDelivered.countDown();
		});

		releaseFirst.countDown();

		assertTrue(secondDelivered.await(5, TimeUnit.SECONDS), "second result was not delivered");
		assertEquals("second", delivered.get());
		assertEquals(1, deliveries.get(), "stale first result must not be delivered");

		runner.shutdown();
	}

	@Test
	void onResultIsNotCalledAfterShutdown() throws InterruptedException
	{
		EngineRunner runner = new EngineRunner();
		CountDownLatch workStarted = new CountDownLatch(1);
		CountDownLatch releaseWork = new CountDownLatch(1);
		AtomicReference<String> delivered = new AtomicReference<>();

		runner.submit(() ->
		{
			workStarted.countDown();
			await(releaseWork);
			return "result";
		}, delivered::set);

		assertTrue(workStarted.await(5, TimeUnit.SECONDS), "work did not start");
		runner.shutdown();
		releaseWork.countDown();

		Thread.sleep(200);

		assertNull(delivered.get(), "onResult must not be called after shutdown");
	}

	@Test
	void aThrowingTaskIsReportedAndDoesNotStopTheNextSubmission() throws InterruptedException
	{
		List<Throwable> reported = new CopyOnWriteArrayList<>();
		EngineRunner runner = new EngineRunner(reported::add);
		CountDownLatch delivered = new CountDownLatch(1);
		AtomicReference<String> result = new AtomicReference<>();

		runner.submit(() ->
		{
			throw new IllegalStateException("work boom");
		}, r -> result.set("must not be delivered"));
		runner.submit(() -> "ok", r ->
		{
			throw new IllegalStateException("onResult boom");
		});
		runner.submit(() -> "second", r ->
		{
			result.set(r);
			delivered.countDown();
		});

		assertTrue(delivered.await(5, TimeUnit.SECONDS), "the submission after a failure must still deliver");
		assertEquals("second", result.get());
		assertEquals(List.of("work boom", "onResult boom"), reported.stream().map(Throwable::getMessage).collect(Collectors.toList()));

		runner.shutdown();
	}

	@Test
	void executeRunsEvenWhenANewerSubmitLandsBeforeIt() throws InterruptedException
	{
		EngineRunner runner = new EngineRunner();
		CountDownLatch firstStarted = new CountDownLatch(1);
		CountDownLatch releaseFirst = new CountDownLatch(1);
		CountDownLatch executed = new CountDownLatch(1);
		AtomicInteger deliveries = new AtomicInteger();

		runner.submit(() ->
		{
			firstStarted.countDown();
			await(releaseFirst);
			return "blocker";
		}, r -> deliveries.incrementAndGet());
		assertTrue(firstStarted.await(5, TimeUnit.SECONDS));

		runner.execute(executed::countDown);
		runner.submit(() -> "newer", r -> deliveries.incrementAndGet());
		releaseFirst.countDown();

		assertTrue(executed.await(5, TimeUnit.SECONDS), "execute must run regardless of the generation race");
		runner.shutdown();
	}

	private static void await(CountDownLatch latch)
	{
		try
		{
			latch.await(5, TimeUnit.SECONDS);
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
		}
	}
}
