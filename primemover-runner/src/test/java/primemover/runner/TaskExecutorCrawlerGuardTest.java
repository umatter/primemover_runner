package primemover.runner;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import primemover.model.Crawler;
import primemover.model.Joblist;
import primemover.runner.joblist.IJobListRunner;
import primemover.runner.joblist.JobListRunnerFactory;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A crawler is one Multilogin profile, and a profile runs one browser at a time.
 * On 2026-10-05 (experiment 189) about 110 joblists failed their setup with
 * PROFILE_ALREADY_RUNNING because the executor started a crawler's next joblist
 * while its previous one was still open. The executor now runs one joblist per
 * crawler at a time; the next one waits without holding a pool thread.
 */
class TaskExecutorCrawlerGuardTest {

	private static final long WAIT = 5;
	private static final long NOT_STARTED_MS = 300;

	// orders start and finish events across runners
	private static final AtomicLong CLOCK = new AtomicLong();

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

	/** Blocks until released or aborted, as a session in progress does. */
	private static class GatedRunner implements IJobListRunner {
		private final long id;
		final CountDownLatch started = new CountDownLatch(1);
		final CountDownLatch release = new CountDownLatch(1);
		final AtomicInteger runs = new AtomicInteger();
		final AtomicInteger aborts = new AtomicInteger();
		volatile long startedAt;
		volatile long finishedAt;

		GatedRunner(long id) {
			this.id = id;
		}

		@Override
		public Boolean get() {
			runs.incrementAndGet();
			startedAt = CLOCK.incrementAndGet();
			started.countDown();
			try {
				release.await(10, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			body();
			finishedAt = CLOCK.incrementAndGet();
			return true;
		}

		void body() {
		}

		@Override
		public long getJobListId() {
			return id;
		}

		@Override
		public void abort() {
			aborts.incrementAndGet();
			release.countDown();
		}

		boolean hasStarted() throws InterruptedException {
			return started.await(WAIT, TimeUnit.SECONDS);
		}

		boolean staysWaiting() throws InterruptedException {
			return !started.await(NOT_STARTED_MS, TimeUnit.MILLISECONDS);
		}
	}

	/** Fails the way an uncaught error out of a session would. */
	private static final class FailingRunner extends GatedRunner {
		FailingRunner(long id) {
			super(id);
		}

		@Override
		void body() {
			throw new IllegalStateException("session blew up");
		}
	}

	@SuppressWarnings("unchecked")
	private Map<Long, ?> lanes() {
		return (Map<Long, ?>) ReflectionTestUtils.getField(executor, "lanes");
	}

	@SuppressWarnings("unchecked")
	private Map<Long, ?> running() {
		return (Map<Long, ?>) ReflectionTestUtils.getField(executor, "running");
	}

	@Test
	@DisplayName("two joblists of one crawler never overlap; the second starts after the first finished")
	void sameCrawlerRunsOneAtATime() throws Exception {
		TaskExecutor executor = executor(4);
		GatedRunner first = new GatedRunner(1L);
		GatedRunner second = new GatedRunner(2L);

		CompletableFuture<Boolean> firstDone = executor.run(7L, first);
		assertThat(first.hasStarted()).isTrue();
		CompletableFuture<Boolean> secondDone = executor.run(7L, second);

		// threads are free, yet the crawler's browser is still open
		assertThat(second.staysWaiting()).isTrue();

		first.release.countDown();
		assertThat(firstDone.get(WAIT, TimeUnit.SECONDS)).isTrue();
		assertThat(second.hasStarted()).isTrue();
		assertThat(second.startedAt).isGreaterThan(first.finishedAt);

		second.release.countDown();
		assertThat(secondDone.get(WAIT, TimeUnit.SECONDS)).isTrue();
	}

	@Test
	@DisplayName("joblists of different crawlers run at the same time")
	void differentCrawlersRunInParallel() throws Exception {
		TaskExecutor executor = executor(2);
		GatedRunner a = new GatedRunner(1L);
		GatedRunner b = new GatedRunner(2L);

		CompletableFuture<Boolean> aDone = executor.run(7L, a);
		CompletableFuture<Boolean> bDone = executor.run(8L, b);

		assertThat(a.hasStarted()).isTrue();
		assertThat(b.hasStarted()).isTrue();

		a.release.countDown();
		b.release.countDown();
		assertThat(aDone.get(WAIT, TimeUnit.SECONDS)).isTrue();
		assertThat(bDone.get(WAIT, TimeUnit.SECONDS)).isTrue();
	}

	@Test
	@DisplayName("a joblist waiting for its crawler holds no pool thread")
	void waitingJoblistHoldsNoThread() throws Exception {
		// two threads: A1 takes one. If A2 blocked a thread while it waited, B would
		// have none left until A1 finished.
		TaskExecutor executor = executor(2);
		GatedRunner a1 = new GatedRunner(1L);
		GatedRunner a2 = new GatedRunner(2L);
		GatedRunner b = new GatedRunner(3L);

		executor.run(7L, a1);
		assertThat(a1.hasStarted()).isTrue();
		executor.run(7L, a2);
		CompletableFuture<Boolean> bDone = executor.run(8L, b);

		assertThat(b.hasStarted()).isTrue();
		assertThat(a1.finishedAt).isZero();
		assertThat(a2.started.getCount()).isEqualTo(1);

		b.release.countDown();
		assertThat(bDone.get(WAIT, TimeUnit.SECONDS)).isTrue();
		a1.release.countDown();
		assertThat(a2.hasStarted()).isTrue();
		a2.release.countDown();
	}

	@Test
	@DisplayName("a pool of one thread runs a crawler's waiting joblist once the thread is free")
	void singleThreadStillDrains() throws Exception {
		TaskExecutor executor = executor(1);
		GatedRunner a1 = new GatedRunner(1L);
		GatedRunner a2 = new GatedRunner(2L);
		GatedRunner b = new GatedRunner(3L);

		executor.run(7L, a1);
		assertThat(a1.hasStarted()).isTrue();
		CompletableFuture<Boolean> a2Done = executor.run(7L, a2);
		CompletableFuture<Boolean> bDone = executor.run(8L, b);

		// B was queued for the thread before A2 was handed over; both still run
		a1.release.countDown();
		assertThat(b.hasStarted()).isTrue();
		b.release.countDown();
		assertThat(a2.hasStarted()).isTrue();
		a2.release.countDown();
		assertThat(bDone.get(WAIT, TimeUnit.SECONDS)).isTrue();
		assertThat(a2Done.get(WAIT, TimeUnit.SECONDS)).isTrue();
	}

	@Test
	@DisplayName("aborting a waiting joblist means it never starts")
	void abortWaitingJoblist() throws Exception {
		TaskExecutor executor = executor(2);
		GatedRunner running = new GatedRunner(1L);
		GatedRunner waiting = new GatedRunner(2L);

		CompletableFuture<Boolean> runningDone = executor.run(7L, running);
		assertThat(running.hasStarted()).isTrue();
		CompletableFuture<Boolean> waitingDone = executor.run(7L, waiting);

		assertThat(executor.abort(2L)).isTrue();
		assertThat(waitingDone.get(WAIT, TimeUnit.SECONDS)).isFalse();
		// a waiting runner has no browser to end: it is dropped, not cut off
		assertThat(waiting.aborts.get()).isZero();
		// the running one is not touched
		assertThat(running.aborts.get()).isZero();

		running.release.countDown();
		assertThat(runningDone.get(WAIT, TimeUnit.SECONDS)).isTrue();
		assertThat(waiting.staysWaiting()).isTrue();
		assertThat(waiting.runs.get()).isZero();
		assertThat(executor.abort(2L)).isFalse();
	}

	@Test
	@DisplayName("aborting the running joblist lets the waiting one start")
	void abortRunningJoblistFreesTheCrawler() throws Exception {
		TaskExecutor executor = executor(2);
		GatedRunner stuck = new GatedRunner(1L);
		GatedRunner next = new GatedRunner(2L);

		CompletableFuture<Boolean> stuckDone = executor.run(7L, stuck);
		assertThat(stuck.hasStarted()).isTrue();
		executor.run(7L, next);
		assertThat(next.staysWaiting()).isTrue();

		assertThat(executor.abort(1L)).isTrue();
		assertThat(stuckDone.get(WAIT, TimeUnit.SECONDS)).isTrue();
		assertThat(stuck.aborts.get()).isEqualTo(1);
		assertThat(next.hasStarted()).isTrue();
		assertThat(next.startedAt).isGreaterThan(stuck.finishedAt);
		next.release.countDown();
	}

	@Test
	@DisplayName("nothing is kept about a crawler once its joblists have finished")
	void bookkeepingIsEmptiedAfterwards() throws Exception {
		TaskExecutor executor = executor(2);
		GatedRunner a1 = new GatedRunner(1L);
		GatedRunner a2 = new GatedRunner(2L);
		GatedRunner a3 = new GatedRunner(3L);
		GatedRunner b = new GatedRunner(4L);

		CompletableFuture<Boolean> a1Done = executor.run(7L, a1);
		assertThat(a1.hasStarted()).isTrue();
		CompletableFuture<Boolean> a2Done = executor.run(7L, a2);
		CompletableFuture<Boolean> a3Done = executor.run(7L, a3);
		CompletableFuture<Boolean> bDone = executor.run(8L, b);
		assertThat(executor.abort(3L)).isTrue();
		assertThat(lanes()).containsOnlyKeys(7L, 8L);

		a1.release.countDown();
		a2.release.countDown();
		b.release.countDown();
		CompletableFuture.allOf(a1Done, a2Done, a3Done, bDone).get(WAIT, TimeUnit.SECONDS);

		assertThat(lanes()).isEmpty();
		assertThat(running()).isEmpty();
	}

	@Test
	@DisplayName("a joblist that fails still frees its crawler")
	void failureReleasesTheCrawler() throws Exception {
		TaskExecutor executor = executor(2);
		FailingRunner failing = new FailingRunner(1L);
		GatedRunner next = new GatedRunner(2L);

		CompletableFuture<Boolean> failingDone = executor.run(7L, failing);
		assertThat(failing.hasStarted()).isTrue();
		CompletableFuture<Boolean> nextDone = executor.run(7L, next);

		failing.release.countDown();
		assertThat(failingDone.get(WAIT, TimeUnit.SECONDS)).isFalse();
		assertThat(next.hasStarted()).isTrue();
		next.release.countDown();
		assertThat(nextDone.get(WAIT, TimeUnit.SECONDS)).isTrue();
		assertThat(lanes()).isEmpty();
	}

	@Test
	@DisplayName("a joblist handed over again while it waits or runs is not run twice")
	void repeatedHandOverIsIgnored() throws Exception {
		TaskExecutor executor = executor(2);
		GatedRunner running = new GatedRunner(1L);
		GatedRunner waiting = new GatedRunner(2L);
		// TaskManager builds a fresh runner for the same joblist on every poll
		GatedRunner runningAgain = new GatedRunner(1L);
		GatedRunner waitingAgain = new GatedRunner(2L);

		CompletableFuture<Boolean> runningDone = executor.run(7L, running);
		assertThat(running.hasStarted()).isTrue();
		CompletableFuture<Boolean> waitingDone = executor.run(7L, waiting);

		assertThat(executor.run(7L, runningAgain)).isSameAs(runningDone);
		assertThat(executor.run(7L, waitingAgain)).isSameAs(waitingDone);

		running.release.countDown();
		assertThat(waiting.hasStarted()).isTrue();
		waiting.release.countDown();
		assertThat(waitingDone.get(WAIT, TimeUnit.SECONDS)).isTrue();

		assertThat(running.runs.get()).isEqualTo(1);
		assertThat(waiting.runs.get()).isEqualTo(1);
		assertThat(runningAgain.runs.get()).isZero();
		assertThat(waitingAgain.runs.get()).isZero();
		assertThat(lanes()).isEmpty();
	}

	@Test
	@DisplayName("run(Crawler, Joblist), the call TaskManager makes, goes through the crawler's lane")
	void taskManagerEntryPointUsesTheLane() throws Exception {
		// Every other test here calls run(Long, IJobListRunner). Production reaches
		// the lane only because run(Crawler, Joblist) forwards the crawler's id; if
		// it called run(IJobListRunner) instead, PROFILE_ALREADY_RUNNING would be back
		// with the rest of this class still green.
		TaskExecutor executor = executor(4);
		GatedRunner first = new GatedRunner(1L);
		GatedRunner second = new GatedRunner(2L);
		Map<Long, IJobListRunner> runners = Map.of(1L, first, 2L, second);
		ReflectionTestUtils.setField(executor, "jobListRunnerFactory", new JobListRunnerFactory() {
			@Override
			public IJobListRunner createJobListRunner(Crawler crawler, Joblist jobs) {
				return runners.get(jobs.getId());
			}
		});
		Crawler crawler = new Crawler();
		crawler.setId(7L);

		CompletableFuture<Boolean> firstDone = executor.run(crawler, joblist(1L));
		assertThat(first.hasStarted()).isTrue();
		CompletableFuture<Boolean> secondDone = executor.run(crawler, joblist(2L));

		// threads are free, yet the crawler's browser is still open
		assertThat(second.staysWaiting()).isTrue();
		assertThat(lanes()).containsOnlyKeys(7L);

		first.release.countDown();
		assertThat(firstDone.get(WAIT, TimeUnit.SECONDS)).isTrue();
		assertThat(second.hasStarted()).isTrue();
		assertThat(second.startedAt).isGreaterThan(first.finishedAt);

		second.release.countDown();
		assertThat(secondDone.get(WAIT, TimeUnit.SECONDS)).isTrue();
		assertThat(lanes()).isEmpty();
	}

	private static Joblist joblist(long id) {
		Joblist joblist = new Joblist();
		joblist.setId(id);
		return joblist;
	}

	@Test
	@DisplayName("a joblist that waited for its crawler and then started can still be cut off")
	void abortPromotedJoblist() throws Exception {
		// A waiting joblist becomes reachable for abort() only when release() puts it
		// into `running` as it hands it to the pool. TaskManager's
		// DONE_PROCESSING_TIMEOUT cut-off depends on that for every joblist that
		// waited behind its crawler.
		TaskExecutor executor = executor(2);
		GatedRunner first = new GatedRunner(1L);
		GatedRunner promoted = new GatedRunner(2L);
		GatedRunner last = new GatedRunner(3L);

		CompletableFuture<Boolean> firstDone = executor.run(7L, first);
		assertThat(first.hasStarted()).isTrue();
		CompletableFuture<Boolean> promotedDone = executor.run(7L, promoted);
		CompletableFuture<Boolean> lastDone = executor.run(7L, last);
		assertThat(promoted.staysWaiting()).isTrue();

		first.release.countDown();
		assertThat(firstDone.get(WAIT, TimeUnit.SECONDS)).isTrue();
		assertThat(promoted.hasStarted()).isTrue();
		assertThat(last.staysWaiting()).isTrue();

		assertThat(executor.abort(2L)).isTrue();
		assertThat(promotedDone.get(WAIT, TimeUnit.SECONDS)).isTrue();
		assertThat(promoted.aborts.get()).isEqualTo(1);
		assertThat(promoted.runs.get()).isEqualTo(1);

		// the crawler is free again: its last joblist starts and the lane empties
		assertThat(last.hasStarted()).isTrue();
		assertThat(last.startedAt).isGreaterThan(promoted.finishedAt);
		last.release.countDown();
		assertThat(lastDone.get(WAIT, TimeUnit.SECONDS)).isTrue();
		assertThat(lanes()).isEmpty();
		assertThat(running()).isEmpty();
	}

	@Test
	@DisplayName("a crawler with one joblist at a time runs it at once, as before")
	void singleJoblistStartsImmediately() throws Exception {
		TaskExecutor executor = executor(1);
		GatedRunner only = new GatedRunner(1L);
		only.release.countDown();

		assertThat(executor.run(7L, only).get(WAIT, TimeUnit.SECONDS)).isTrue();
		assertThat(only.runs.get()).isEqualTo(1);

		// and the crawler's next joblist, after it finished, starts at once too
		GatedRunner later = new GatedRunner(2L);
		later.release.countDown();
		assertThat(executor.run(7L, later).get(WAIT, TimeUnit.SECONDS)).isTrue();
		assertThat(lanes()).isEmpty();
	}
}
