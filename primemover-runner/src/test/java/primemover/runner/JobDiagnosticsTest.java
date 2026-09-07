package primemover.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import primemover.runner.job.handler.LambdaSelectJobResult;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobDiagnosticsTest {

	@AfterEach
	void clear() {
		JobDiagnostics.endJob();
	}

	@Test
	void warningsAreCollectedInOrder() {
		JobDiagnostics.beginJob();

		JobDiagnostics.addWarning("lambda selection_mode=random");
		JobDiagnostics.addWarning("mainstream_media filter skipped");

		assertTrue(JobDiagnostics.hasWarnings());
		assertEquals(List.of("lambda selection_mode=random", "mainstream_media filter skipped"),
				JobDiagnostics.warnings());
	}

	@Test
	void blankAndNullWarningsAreIgnored() {
		JobDiagnostics.beginJob();

		JobDiagnostics.addWarning(null);
		JobDiagnostics.addWarning("");
		JobDiagnostics.addWarning("   ");

		assertFalse(JobDiagnostics.hasWarnings());
		assertEquals(List.of(), JobDiagnostics.warnings());
	}

	@Test
	void warningsAreCappedSoAHandlerInALoopCannotGrowThemWithoutBound() {
		JobDiagnostics.beginJob();

		for (int i = 0; i < JobDiagnostics.MAX_WARNINGS + 25; i++) {
			JobDiagnostics.addWarning("warning " + i);
		}

		assertEquals(JobDiagnostics.MAX_WARNINGS, JobDiagnostics.warnings().size());
	}

	@Test
	void beginJobDiscardsResidueFromAnEarlierJobOnTheSameThread() {
		JobDiagnostics.beginJob();
		JobDiagnostics.addWarning("from the previous job");

		JobDiagnostics.beginJob();

		assertFalse(JobDiagnostics.hasWarnings(), "a pooled thread must not carry warnings into the next job");
	}

	@Test
	void endJobReturnsAndClears() {
		JobDiagnostics.beginJob();
		JobDiagnostics.addWarning("degraded");

		assertEquals(List.of("degraded"), JobDiagnostics.endJob());
		assertFalse(JobDiagnostics.hasWarnings());
		assertEquals(List.of(), JobDiagnostics.warnings());
	}

	// ------------------------------------------------------------------
	// The decision verdict shares the per-job lifecycle above, and gets its own
	// cases because it fails differently. A leaked *warning* adds noise to one
	// job's status message; a leaked *verdict* writes four columns of false
	// science -- job B recorded as "treatment lost" because job A lost its
	// treatment on the same pooled thread, or recorded "clean" from a decision
	// that was never made for it. That is exactly the false-clean/false-alarm
	// reading docs/integration-contract.md 7.3 exists to prevent, so the
	// clearing at both ends of the job is asserted rather than assumed.
	// ------------------------------------------------------------------

	/**
	 * A payload carrying a degradation report, i.e. one {@code of()} maps.
	 *
	 * <p>
	 * The filter state and the selection mode are kept consistent with the flags
	 * so the fixture is a payload the decision function could actually emit: a
	 * degraded one lost its {@code similar} filter and fell back to a random draw,
	 * a clean one applied it and picked on the LLM's rating.
	 */
	private static DecisionVerdict verdict(boolean degraded, boolean treatmentLost) throws Exception {
		String similar = degraded ? "failed: RateLimitError: Rate limit reached" : "ok";
		String mode = degraded ? "random" : "llm";

		LambdaSelectJobResult parsed = new ObjectMapper()
				.readValue("{\"xpath\": \"//a\", \"degraded\": " + degraded + ", \"treatment_lost\": "
						+ treatmentLost + ", \"selection_mode\": \"" + mode + "\","
						+ " \"filters\": {\"similar\": \"" + similar + "\"}}",
						LambdaSelectJobResult.class);

		DecisionVerdict built = DecisionVerdict.of(parsed);
		assertNotNull(built, "the fixture must carry a degradation report, or it tests nothing");
		return built;
	}

	@Test
	@DisplayName("beginJob discards a verdict left by an earlier job on the same thread")
	void beginJobDiscardsADecisionVerdictFromAnEarlierJobOnTheSameThread() throws Exception {
		JobDiagnostics.beginJob();
		JobDiagnostics.recordDecisionVerdict(verdict(true, true));

		JobDiagnostics.beginJob();

		assertNull(JobDiagnostics.decisionVerdict(),
				"a pooled thread must not carry one job's decision verdict into the next");
	}

	@Test
	@DisplayName("endJob clears the verdict as well as the warnings")
	void endJobClearsTheDecisionVerdict() throws Exception {
		JobDiagnostics.beginJob();
		JobDiagnostics.recordDecisionVerdict(verdict(true, false));

		JobDiagnostics.endJob();

		assertNull(JobDiagnostics.decisionVerdict());
	}

	@Test
	@DisplayName("a second, legacy payload does not wipe the verdict already recorded")
	void aNullVerdictDoesNotWipeWhatWasAlreadyRecorded() throws Exception {
		// The handler re-invokes the decision function on a click retry, and
		// DecisionVerdict.of() returns null for a payload with no degradation
		// report. Null means "we cannot tell", which must never overwrite a
		// measurement that was made.
		JobDiagnostics.beginJob();
		DecisionVerdict lost = verdict(true, true);
		JobDiagnostics.recordDecisionVerdict(lost);

		JobDiagnostics.recordDecisionVerdict(null);

		assertSame(lost, JobDiagnostics.decisionVerdict());
	}

	@Test
	@DisplayName("the worse of two verdicts is the one the job is reported with")
	void theMoreSevereVerdictSurvives() throws Exception {
		JobDiagnostics.beginJob();
		DecisionVerdict lost = verdict(true, true);
		JobDiagnostics.recordDecisionVerdict(lost);

		// a retry that came back fine must not launder the lost treatment: the
		// recorded session already contains the degraded decision
		JobDiagnostics.recordDecisionVerdict(verdict(false, false));

		assertSame(lost, JobDiagnostics.decisionVerdict());
	}

	@Test
	@DisplayName("a job that never asked the decision function has no verdict, not a clean one")
	void aJobWithNoDecisionHasNoVerdict() {
		// every visitjob and waitjob takes this path: nothing recorded, so
		// JobService sends none of the four keys and the API stores NULL = unknown
		JobDiagnostics.beginJob();

		assertNull(JobDiagnostics.decisionVerdict());
	}

	@Test
	void theReturnedSnapshotIsImmutable() {
		JobDiagnostics.beginJob();
		JobDiagnostics.addWarning("degraded");

		List<String> snapshot = JobDiagnostics.warnings();
		assertThrows(UnsupportedOperationException.class, () -> snapshot.add("nope"));
	}

	@Test
	void concurrentJobsDoNotSeeEachOthersWarnings() throws Exception {
		// the runner runs up to runner.MaxParallelExecution jobs on a pooled
		// executor; warnings must never bleed from one job into another
		int threads = 8;
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch started = new CountDownLatch(threads);
		CountDownLatch release = new CountDownLatch(1);
		AtomicReference<String> mismatch = new AtomicReference<>();

		try {
			for (int i = 0; i < threads; i++) {
				final int jobId = i;
				pool.submit(() -> {
					JobDiagnostics.beginJob();
					JobDiagnostics.addWarning("job-" + jobId);
					started.countDown();
					try {
						release.await(10, TimeUnit.SECONDS);
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}

					List<String> seen = JobDiagnostics.endJob();
					if (!List.of("job-" + jobId).equals(seen)) {
						mismatch.compareAndSet(null, "job-" + jobId + " saw " + seen);
					}
					return null;
				});
			}

			assertTrue(started.await(10, TimeUnit.SECONDS), "workers did not start");
			release.countDown();
			pool.shutdown();
			assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS), "workers did not finish");
		} finally {
			pool.shutdownNow();
		}

		assertNull(mismatch.get(), () -> "warnings leaked between concurrent jobs: " + mismatch.get());
	}

	@Test
	void describeRendersClassAndMessage() {
		assertEquals("IllegalStateException: tab crashed",
				JobDiagnostics.describe(new IllegalStateException("tab crashed")));
	}

	@Test
	void describeFallsBackToTheClassNameWhenThereIsNoMessage() {
		assertEquals("NullPointerException", JobDiagnostics.describe(new NullPointerException()));
	}

	@Test
	void describeAppendsTheRootCauseWhenItDiffers() {
		Exception root = new IllegalArgumentException("bad selector");
		Exception wrapper = new IllegalStateException("could not run job", root);

		assertEquals("IllegalStateException: could not run job | caused by: IllegalArgumentException: bad selector",
				JobDiagnostics.describe(wrapper));
	}

	@Test
	void describeDoesNotRepeatARootCauseAlreadyQuotedInTheMessage() {
		Exception root = new IllegalArgumentException("bad selector");
		Exception wrapper = new IllegalStateException("IllegalArgumentException: bad selector", root);

		assertEquals("IllegalStateException: IllegalArgumentException: bad selector",
				JobDiagnostics.describe(wrapper));
	}

	@Test
	void describeNeverContainsAStackTrace() {
		Exception boom = new IllegalStateException("boom");
		boom.fillInStackTrace();

		String described = JobDiagnostics.describe(boom);

		assertFalse(described.contains("\n"), described);
		assertFalse(described.contains("\tat "), described);
	}

	@Test
	void describeCollapsesAMultiLineExceptionMessage() {
		// The real case, and the reason this assertion is not redundant with the one
		// above: a Selenium WebDriverException appends its own Build/System/Driver
		// info block to getMessage(), so the message alone is already nine lines. A
		// single-line exception ("boom") could never have caught that.
		Exception crashed = new IllegalStateException("tab crashed\n  (Session info: chrome=139.0.7258.66)\n"
				+ "Build info: version: '4.27.0'\nDriver info: org.openqa.selenium.chrome.ChromeDriver");

		String described = JobDiagnostics.describe(crashed);

		assertFalse(described.contains("\n"), described);
		assertTrue(described.startsWith("IllegalStateException: tab crashed (Session info:"), described);
	}

	@Test
	void describeTolerantOfNull() {
		assertNull(JobDiagnostics.describe(null));
	}
}
