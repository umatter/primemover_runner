package primemover.runner;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The distinction this type encodes is the whole point: a run that lost jobs is
 * neither a success nor a total failure. Calling it either one is what recorded
 * two 204-job runs as "success / done" after they completed 8 jobs each.
 */
class JobListOutcomeTest {

	@Test
	@DisplayName("a run where everything succeeded is clean")
	void cleanRun() {
		JobListOutcome o = new JobListOutcome();
		for (int i = 0; i < 20; i++) {
			o.countSucceeded();
		}

		assertTrue(o.isClean());
		assertFalse(o.isDegraded());
		assertFalse(o.isTotalFailure());
		assertEquals(20, o.getTotal());
	}

	@Test
	@DisplayName("the real q849 shape: 8 of 204 succeeded -> degraded, not success")
	void theRunThatUsedToReportSuccess() {
		JobListOutcome o = new JobListOutcome();
		for (int i = 0; i < 8; i++) {
			o.countSucceeded();
		}
		for (int i = 0; i < 49; i++) {
			o.countFailed();
		}
		for (int i = 0; i < 147; i++) {
			o.countSkipped();
		}

		assertEquals(204, o.getTotal());
		assertFalse(o.isClean(), "8 of 204 is not a clean run");
		assertTrue(o.isDegraded());
		assertFalse(o.isTotalFailure(), "8 jobs did produce data, so this is not a total failure");
		assertEquals("8/204 jobs succeeded (0 warning, 49 failed, 147 skipped)", o.describe());
	}

	@Test
	@DisplayName("a job that finished with a warning still counts as lost cleanliness")
	void warningsMakeARunDegraded() {
		JobListOutcome o = new JobListOutcome();
		o.countSucceeded();
		o.countWarned();

		assertFalse(o.isClean());
		assertTrue(o.isDegraded());
		assertFalse(o.isTotalFailure());
	}

	@Test
	@DisplayName("nothing usable came out -> total failure, not merely degraded")
	void totalFailure() {
		JobListOutcome o = new JobListOutcome();
		o.countFailed();
		for (int i = 0; i < 5; i++) {
			o.countSkipped();
		}

		assertFalse(o.isClean());
		assertFalse(o.isDegraded(), "no job succeeded, so there is nothing to call partially good");
		assertTrue(o.isTotalFailure());
	}

	@Test
	@DisplayName("an empty run is not a failure")
	void emptyRun() {
		JobListOutcome o = new JobListOutcome();

		assertTrue(o.isClean());
		assertFalse(o.isTotalFailure(), "an empty joblist is handled by DONE_JOBLIST_EMPTY, not here");
		assertEquals(0, o.getTotal());
	}

	@Test
	@DisplayName("a warned job is not a lost job")
	void warningsAreNotLosses() {
		// Round 4 (experiment 148) reported "completed with lost jobs -
		// 283/284 jobs succeeded (1 warning, 0 failed, 0 skipped)" on nine of
		// twelve bots. Nothing was lost: those jobs ran to the end and produced
		// their data. Overstating it in the one field an analysis reads first
		// blurs the line between a session to inspect and one to discard.
		JobListOutcome o = new JobListOutcome();
		for (int i = 0; i < 283; i++) {
			o.countSucceeded();
		}
		o.countWarned();

		assertFalse(o.hasLostJobs(), "a warning is not a loss");
		// Still degraded, and still reported as such: the status_code is unchanged.
		assertTrue(o.isDegraded());
		assertFalse(o.isClean());
	}

	@Test
	@DisplayName("a failed or skipped job is a lost job")
	void failuresAndSkipsAreLosses() {
		JobListOutcome failed = new JobListOutcome();
		failed.countSucceeded();
		failed.countFailed();
		assertTrue(failed.hasLostJobs());

		JobListOutcome skipped = new JobListOutcome();
		skipped.countSucceeded();
		skipped.countSkipped();
		assertTrue(skipped.hasLostJobs(), "a job that never ran is lost too");
	}

	@Test
	@DisplayName("a clean run has lost nothing")
	void cleanRunHasNoLosses() {
		JobListOutcome o = new JobListOutcome();
		o.countSucceeded();
		assertFalse(o.hasLostJobs());
	}
}
