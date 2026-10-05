package primemover.runner;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import primemover.runner.joblist.IJobListRunner;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code TaskManager} can only cut off a joblist it can find. Cancelling the
 * {@code CompletableFuture} would not stop the worker thread, so the executor
 * keeps the runner itself, by joblist id, for as long as it is queued or
 * running.
 */
class TaskExecutorAbortTest {

	private TaskExecutor executor;

	private TaskExecutor executor(int threads) {
		executor = new TaskExecutor();
		ReflectionTestUtils.setField(executor, "maxThreads", threads);
		executor.setup();
		return executor;
	}

	@AfterEach
	void shutdown() {
		if (executor != null) {
			executor.shutdown();
		}
	}

	/** A runner that blocks until aborted, as a hung session does. */
	private static final class BlockingRunner implements IJobListRunner {
		private final long id;
		final CountDownLatch started = new CountDownLatch(1);
		final CountDownLatch abortCalled = new CountDownLatch(1);
		final AtomicInteger aborts = new AtomicInteger();

		BlockingRunner(long id) {
			this.id = id;
		}

		@Override
		public Boolean get() {
			started.countDown();
			try {
				abortCalled.await(10, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			return true;
		}

		@Override
		public long getJobListId() {
			return id;
		}

		@Override
		public void abort() {
			aborts.incrementAndGet();
			abortCalled.countDown();
		}
	}

	@Test
	@DisplayName("aborting a running joblist reaches its runner and frees the slot")
	void abortReachesTheRunner() throws Exception {
		TaskExecutor executor = executor(1);
		BlockingRunner stuck = new BlockingRunner(2019L);
		CompletableFuture<Boolean> done = executor.run(stuck);
		assertThat(stuck.started.await(5, TimeUnit.SECONDS)).isTrue();

		assertThat(executor.abort(2019L)).isTrue();

		assertThat(done.get(5, TimeUnit.SECONDS)).isTrue();
		assertThat(stuck.aborts.get()).isEqualTo(1);

		// the single slot is free: the next queue starts
		BlockingRunner next = new BlockingRunner(2020L);
		executor.run(next);
		assertThat(next.started.await(5, TimeUnit.SECONDS)).isTrue();
		executor.abort(2020L);
	}

	@Test
	@DisplayName("a joblist this executor does not hold is not aborted")
	void unknownJoblistIsLeftAlone() {
		TaskExecutor executor = executor(1);

		assertThat(executor.abort(404L)).isFalse();
	}

	@Test
	@DisplayName("a finished joblist is forgotten, so a late abort is a no-op")
	void finishedJoblistIsForgotten() throws Exception {
		TaskExecutor executor = executor(1);
		BlockingRunner quick = new BlockingRunner(7L);
		quick.abortCalled.countDown(); // returns at once
		executor.run(quick).get(5, TimeUnit.SECONDS);

		// whenComplete runs on the completing thread; allow it a moment
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
		while (executor.abort(7L) && System.nanoTime() < deadline) {
			Thread.sleep(10);
		}

		assertThat(executor.abort(7L)).isFalse();
	}
}
