package primemover.model;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the status vocabulary the runner puts on the wire against the canonical
 * contract (orchestrator/status.py, docs/integration-contract.md).
 *
 * <p>
 * Two of these assertions describe bugs that shipped for a long time: a job
 * whose data never reached storage reported {@code success} (a green pill in the
 * GUI for a job with nothing to show), and two queue states emitted the typo
 * {@code "skpped"}.
 */
class StatusContractTest {

	private static final Set<String> JOB_CODES = new HashSet<>(
			Set.of("running", "success", "warning", "failure", "skipped"));

	private static final Set<String> QUEUE_CODES = new HashSet<>(
			Set.of("running", "success", "failure", "skipped"));

	@Test
	void everyJobStateUsesACanonicalCode() {
		for (JobState state : JobState.values()) {
			assertTrue(JOB_CODES.contains(state.getCode()),
					"JobState." + state.name() + " reports non-canonical code '" + state.getCode() + "'");
		}
	}

	@Test
	void everyJoblistStateUsesACanonicalCode() {
		for (JoblistState state : JoblistState.values()) {
			assertTrue(QUEUE_CODES.contains(state.getCode()),
					"JoblistState." + state.name() + " reports non-canonical code '" + state.getCode() + "'");
		}
	}

	@Test
	void degradedJobsAreReportedAsWarningNotSuccess() {
		assertEquals("warning", JobState.WARNING.getCode());
		assertEquals("warning", JobState.WARNING_JOBDATA.getCode());
	}

	@Test
	void skippedIsNotMisspelled() {
		assertEquals("skipped", JoblistState.SKIPPED_INACTIVE.getCode());
		assertEquals("skipped", JoblistState.SKIPPED_REVIEWED.getCode());
		assertEquals("skipped", JoblistState.SKIPPED_TIMEDOUT.getCode());
	}

	@Test
	void stateTextsAreStable() {
		// the texts are the first half of every status_message; consumers grep for
		// them, so changing one is a wire change, not a cosmetic edit
		assertEquals("error during execution", JobState.FAILURE.getText());
		assertEquals("problems writing jobresult", JobState.WARNING_JOBDATA.getText());
		assertEquals("minor error encountered", JobState.WARNING.getText());
		assertEquals("successfully finished job", JobState.SUCCESS.getText());
	}
}
