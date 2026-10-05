package primemover.runner.joblist;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import primemover.model.Crawler;
import primemover.model.Joblist;
import primemover.model.JoblistState;
import primemover.runner.ITaskOperator;
import primemover.runner.JobListOutcome;
import primemover.service.IJobService;

import java.time.ZonedDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What a processing timeout does to the worker. {@code TaskManager} used to
 * record DONE_PROCESSING_TIMEOUT and nothing else, so on 2026-10-05 twelve jobs
 * were still running inside queues already closed as timed out, each holding one
 * of the runner's 40 slots and a browser.
 */
class JobListRunnerAbortTest {

	private final ExecutorService pool = Executors.newCachedThreadPool();

	@AfterEach
	void stopPool() {
		pool.shutdownNow();
	}

	private final ITaskOperator taskOperator = mock(ITaskOperator.class);
	private final IJobService jobService = mock(IJobService.class);
	private final Crawler crawler = new Crawler();
	private final Joblist jobs = new Joblist();

	private JobListRunner runner() {
		crawler.setId(14L);
		crawler.setName("bot_treatment_01");
		jobs.setId(2019L);
		jobs.setStartTime(ZonedDateTime.now());

		when(jobService.updateJoblistState(any(), any(), any(), any(), any(), any(), any())).thenReturn(Boolean.TRUE);
		when(taskOperator.prepareExeuction(eq(crawler))).thenReturn(Boolean.TRUE);

		JobListRunner runner = new JobListRunner(taskOperator, crawler, jobs);
		ReflectionTestUtils.setField(runner, "jobService", jobService);
		return runner;
	}

	@Test
	@DisplayName("abort ends the browser immediately and interrupts a worker stuck in the job loop")
	void abortReleasesAStuckWorker() throws Exception {
		JobListRunner runner = runner();

		CountDownLatch inJobLoop = new CountDownLatch(1);
		AtomicBoolean sawInterrupt = new AtomicBoolean(false);
		when(taskOperator.runJobList(eq(jobs))).thenAnswer(invocation -> {
			inJobLoop.countDown();
			try {
				Thread.sleep(60_000); // a job that would run for the rest of the night
			} catch (InterruptedException e) {
				sawInterrupt.set(true);
			}
			return new JobListOutcome();
		});

		Future<Boolean> worker = pool.submit(runner::get);
		assertThat(inJobLoop.await(5, TimeUnit.SECONDS)).isTrue();

		runner.abort();

		worker.get(5, TimeUnit.SECONDS); // the thread came back: the slot is free
		assertThat(sawInterrupt.get()).isTrue();
		verify(taskOperator).tearDown(true);
	}

	@Test
	@DisplayName("a cut-off worker does not overwrite DONE_PROCESSING_TIMEOUT with its own outcome")
	void abortedRunnerReportsNothingAfterwards() throws Exception {
		JobListRunner runner = runner();

		CountDownLatch inJobLoop = new CountDownLatch(1);
		CountDownLatch released = new CountDownLatch(1);
		when(taskOperator.runJobList(eq(jobs))).thenAnswer(invocation -> {
			inJobLoop.countDown();
			released.await(5, TimeUnit.SECONDS);
			JobListOutcome outcome = new JobListOutcome();
			outcome.countSucceeded();
			return outcome;
		});

		Future<Boolean> worker = pool.submit(runner::get);
		assertThat(inJobLoop.await(5, TimeUnit.SECONDS)).isTrue();
		runner.abort();
		released.countDown();
		worker.get(5, TimeUnit.SECONDS);

		verify(jobService, never()).updateJoblistState(any(), eq(JoblistState.RUNNING_JOBSDONE), any(), any(),
				any(), any(), any());
		verify(jobService, never()).updateJoblistState(any(), eq(JoblistState.DONE_SUCCESS), any(), any(), any(),
				any(), any());
		verify(jobService, never()).updateJoblistState(any(), eq(JoblistState.DONE_JOBSFAILURE), any(), any(),
				any(), any(), any());
		verify(jobService, never()).updateJoblistState(any(), eq(JoblistState.DONE_FAILURE), any(), any(), any(),
				any(), any());
	}

	@Test
	@DisplayName("abort is idempotent and harmless after the run has finished")
	void abortIsIdempotent() throws Exception {
		JobListRunner runner = runner();
		JobListOutcome clean = new JobListOutcome();
		clean.countSucceeded();
		when(taskOperator.runJobList(eq(jobs))).thenReturn(clean);

		CompletableFuture.supplyAsync(runner, pool).get(5, TimeUnit.SECONDS);

		runner.abort();
		runner.abort();

		verify(taskOperator).tearDown(true); // once, not twice
	}
}
