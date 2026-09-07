package dev.reece.nta.engine;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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
