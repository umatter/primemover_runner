package primemover.runner;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
