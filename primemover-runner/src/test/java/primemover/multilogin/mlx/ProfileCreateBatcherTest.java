package primemover.multilogin.mlx;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Multilogin allows 50 API operations per minute and says what to do about it in
 * the body of its own rejection: "Try sending a single request with \"times\"
 * parameter instead". Twelve bots launching together made twelve separate
 * creates in the same second, which cost one bot its run on 2026-08-31 and four
 * on 2026-09-01.
 *
 * <p>
 * The requirement that matters most is not the batching. It is that two crawlers
 * must never end up holding the same profile id: they would interleave their
 * cookies and browsing history, and a personalisation study cannot detect that
 * after the fact.
 */
class ProfileCreateBatcherTest {

	private static List<String> ids(int n) {
		List<String> out = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			out.add("profile-" + i);
		}
		return out;
	}

	@Test
	@DisplayName("concurrent creations become one call, one id each")
	void batchesConcurrentCreations() throws Exception {
		ProfileCreateBatcher batcher = new ProfileCreateBatcher(12, 300, 10_000);
		AtomicInteger calls = new AtomicInteger();
		AtomicInteger requestedTimes = new AtomicInteger();

		ExecutorService pool = Executors.newFixedThreadPool(8);
		CountDownLatch ready = new CountDownLatch(8);
		List<String> got = new CopyOnWriteArrayList<>();
		List<Exception> failures = new CopyOnWriteArrayList<>();

		for (int i = 0; i < 8; i++) {
			pool.submit(() -> {
				ready.countDown();
				try {
					got.add(batcher.acquire("{\"times\": 1}", (template, times) -> {
						calls.incrementAndGet();
						requestedTimes.set(times);
						return ids(times);
					}));
				} catch (Exception e) {
					failures.add(e);
				}
			});
		}
		pool.shutdown();
		assertTrue(pool.awaitTermination(20, TimeUnit.SECONDS));

		assertTrue(failures.isEmpty(), () -> "unexpected failures: " + failures);
		assertEquals(8, got.size());
		// The whole point: 8 bots, one API operation.
		assertEquals(1, calls.get(), "eight concurrent creations should be one call");
		assertEquals(8, requestedTimes.get(), "times must equal the batch size");
		// And no two crawlers may share a profile.
		assertEquals(8, new java.util.HashSet<>(got).size(), "ids must be distinct");
	}

	@Test
	@DisplayName("a lone creation still works, without waiting for a full batch")
	void singleCreationIsNotBlocked() throws Exception {
		ProfileCreateBatcher batcher = new ProfileCreateBatcher(12, 150, 10_000);
		long started = System.currentTimeMillis();

		String id = batcher.acquire("{\"times\": 1}", (template, times) -> {
			assertEquals(1, times);
			return Collections.singletonList("only-one");
		});

		assertEquals("only-one", id);
		// It waits the window for company, and no longer.
		assertTrue(System.currentTimeMillis() - started < 5_000, "a lone create must not hang");
	}

	@Test
	@DisplayName("a short answer fails the remainder rather than reusing an id")
	void shortAnswerFailsRatherThanSharing() throws Exception {
		ProfileCreateBatcher batcher = new ProfileCreateBatcher(4, 300, 10_000);
		ExecutorService pool = Executors.newFixedThreadPool(4);
		List<String> got = new CopyOnWriteArrayList<>();
		List<Exception> failures = new CopyOnWriteArrayList<>();

		for (int i = 0; i < 4; i++) {
			pool.submit(() -> {
				try {
					// The API returns fewer ids than asked for.
					got.add(batcher.acquire("{\"times\": 1}", (template, times) -> ids(2)));
				} catch (Exception e) {
					failures.add(e);
				}
			});
		}
		pool.shutdown();
		assertTrue(pool.awaitTermination(20, TimeUnit.SECONDS));

		assertEquals(2, got.size(), "only the ids actually returned may be handed out");
		assertEquals(2, failures.size(), "the rest must fail");
		assertEquals(2, new java.util.HashSet<>(got).size(), "no id may be handed out twice");
	}

	@Test
	@DisplayName("a failed call fails every waiter, so the existing retry still applies")
	void failurePropagatesToEveryWaiter() throws Exception {
		ProfileCreateBatcher batcher = new ProfileCreateBatcher(4, 300, 10_000);
		ExecutorService pool = Executors.newFixedThreadPool(3);
		List<Exception> failures = new CopyOnWriteArrayList<>();

		for (int i = 0; i < 3; i++) {
			pool.submit(() -> {
				try {
					batcher.acquire("{\"times\": 1}", (template, times) -> {
						throw new IllegalStateException("429 Too Many Requests");
					});
				} catch (Exception e) {
					failures.add(e);
				}
			});
		}
		pool.shutdown();
		assertTrue(pool.awaitTermination(20, TimeUnit.SECONDS));

		assertEquals(3, failures.size());
		// The message has to survive: StartupBackoff reads it to decide whether
		// this was a rate limit and how long to wait.
		assertTrue(failures.get(0).getMessage().contains("429"), failures.get(0).getMessage());
	}

	@Test
	@DisplayName("a batch never exceeds the configured size")
	void respectsMaxBatch() throws Exception {
		ProfileCreateBatcher batcher = new ProfileCreateBatcher(3, 200, 10_000);
		List<Integer> sizes = new CopyOnWriteArrayList<>();
		ExecutorService pool = Executors.newFixedThreadPool(6);
		CountDownLatch done = new CountDownLatch(6);

		for (int i = 0; i < 6; i++) {
			pool.submit(() -> {
				try {
					batcher.acquire("{\"times\": 1}", (template, times) -> {
						sizes.add(times);
						return ids(times);
					});
				} catch (Exception ignored) {
					// counted by the latch either way
				} finally {
					done.countDown();
				}
			});
		}
		assertTrue(done.await(20, TimeUnit.SECONDS));
		pool.shutdown();

		assertFalse(sizes.isEmpty());
		for (int size : sizes) {
			assertTrue(size <= 3, "batch of " + size + " exceeds the configured maximum");
		}
	}
}
