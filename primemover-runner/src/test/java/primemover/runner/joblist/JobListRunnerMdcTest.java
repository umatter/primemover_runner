package primemover.runner.joblist;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.test.util.ReflectionTestUtils;
import primemover.model.Crawler;
import primemover.model.Joblist;
import primemover.model.JoblistState;
import primemover.runner.ITaskOperator;
import primemover.service.IJobService;

import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The logging context a joblist run establishes, and gives back.
 *
 * <p>
 * {@code run()} used to put four MDC keys and never remove them. The runner
 * executes joblists on a pooled executor, so those keys outlived the run and
 * stamped the <em>next</em> joblist's log lines -- and everything logged between
 * runs -- with the previous crawler's id, name and correlation id. Promtail
 * turns them into Loki labels, so this is not cosmetic: lines are filed under
 * the wrong crawler and a trace answers with another bot's work.
 */
class JobListRunnerMdcTest {

	private static final String[] KEYS = { "crawler.id", "crawler.name", "joblist.id", "correlationId" };

	private ITaskOperator taskOperator;
	private IJobService jobService;
	private Crawler crawler;
	private Joblist jobs;

	@BeforeEach
	void setUp() {
		MDC.clear();

		taskOperator = mock(ITaskOperator.class);
		jobService = mock(IJobService.class);

		crawler = new Crawler();
		crawler.setId(14L);
		crawler.setName("bot_treatment_01");

		jobs = new Joblist();
		jobs.setId(4711L);
		jobs.setStartTime(ZonedDateTime.now());

		when(jobService.updateJoblistState(any(), any(), any(), any(), any(), any(), any())).thenReturn(Boolean.TRUE);
		when(taskOperator.prepareExeuction(eq(crawler))).thenReturn(Boolean.TRUE);
		when(taskOperator.runJobList(eq(jobs))).thenReturn(Boolean.TRUE);
	}

	@AfterEach
	void clearMdc() {
		MDC.clear();
	}

	private JobListRunner runner() {
		JobListRunner runner = new JobListRunner(taskOperator, crawler, jobs);
		ReflectionTestUtils.setField(runner, "jobService", jobService);
		return runner;
	}

	@Test
	@DisplayName("the context is set while the joblist runs")
	void contextIsSetDuringTheRun() {
		Map<String, String> seen = new LinkedHashMap<>();
		when(taskOperator.runJobList(eq(jobs))).thenAnswer(invocation -> {
			for (String key : KEYS) {
				seen.put(key, MDC.get(key));
			}
			return Boolean.TRUE;
		});

		assertThat(runner().run()).isTrue();

		assertThat(seen).containsEntry("crawler.id", "14");
		assertThat(seen).containsEntry("crawler.name", "bot_treatment_01");
		assertThat(seen).containsEntry("joblist.id", "4711");
		// the exp- segment is omitted: the runner is never told the experiment id
		assertThat(seen).containsEntry("correlationId", "cr-14|q-4711");
	}

	@Test
	@DisplayName("every key is removed again, so the next joblist on this pooled thread starts clean")
	void contextIsRemovedAfterTheRun() {
		assertThat(runner().run()).isTrue();

		for (String key : KEYS) {
			assertThat(MDC.get(key)).as("MDC key %s leaked past the run", key).isNull();
		}
	}

	@Test
	@DisplayName("the keys are removed even when the run blows up")
	void contextIsRemovedAfterAFailedRun() {
		when(taskOperator.prepareExeuction(eq(crawler)))
				.thenThrow(new IllegalStateException("multilogin profile did not start"));

		// run() catches, reports DONE_FAILURE and tears down; it must still clean up
		assertThat(runner().run()).isFalse();

		for (String key : KEYS) {
			assertThat(MDC.get(key)).as("MDC key %s leaked past a failed run", key).isNull();
		}
	}

	@Test
	@DisplayName("a context the caller already had is restored, not wiped")
	void anExistingContextIsRestored() {
		MDC.put("crawler.id", "99");
		MDC.put("correlationId", "cr-99");

		assertThat(runner().run()).isTrue();

		assertThat(MDC.get("crawler.id")).isEqualTo("99");
		assertThat(MDC.get("correlationId")).isEqualTo("cr-99");
		// keys the caller did not have are removed rather than left behind
		assertThat(MDC.get("joblist.id")).isNull();
		assertThat(MDC.get("crawler.name")).isNull();
	}

	@Test
	@DisplayName("the run itself still does what it did before")
	void theRunIsUnchanged() {
		assertThat(runner().run()).isTrue();

		// unchanged behaviour: the queue is reported done and the crawler torn down
		org.mockito.Mockito.verify(taskOperator).tearDown(anyBoolean());
		org.mockito.Mockito.verify(jobService).updateJoblistState(eq(jobs), eq(JoblistState.DONE_SUCCESS), any(),
				any(), any(), eq(Boolean.TRUE), eq(Boolean.FALSE));
	}
}
