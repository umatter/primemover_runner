package primemover.runner;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * When the rest of a session must not run. A frozen browser did not come back on
 * experiment 189 (3 of 116), and each further visit cost ~4 minutes of its own
 * timeout, so the queue is skipped rather than fed to it.
 */
class TaskOperatorSessionSkipTest {

	@Test
	@DisplayName("a healthy session runs on")
	void healthySessionRunsOn() {
		assertThat(TaskOperator.sessionSkipReason(false, false, false)).isNull();
	}

	@Test
	@DisplayName("a frozen browser ends the session")
	void frozenBrowserEndsTheSession() {
		assertThat(TaskOperator.sessionSkipReason(false, false, true)).isEqualTo("browser unresponsive; session ended");
	}

	@Test
	@DisplayName("a cut-off or interrupted worker ends the session, and that reason wins")
	void cutOffEndsTheSession() {
		assertThat(TaskOperator.sessionSkipReason(true, false, false)).contains("cut off");
		assertThat(TaskOperator.sessionSkipReason(false, true, false)).contains("cut off");
		assertThat(TaskOperator.sessionSkipReason(true, false, true)).contains("cut off");
	}
}
