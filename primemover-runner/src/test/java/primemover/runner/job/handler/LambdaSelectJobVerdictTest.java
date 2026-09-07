package primemover.runner.job.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import primemover.runner.DecisionVerdict;
import primemover.runner.JobDiagnostics;

import java.util.LinkedHashSet;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * How the decision function's verdict gets from the handler to the job report.
 *
 * <p>
 * The handler already turns a degradation into {@code status_message} prose.
 * This is the other half: the same parsed verdict is recorded on the job so that
 * {@code TaskOperator} can put it in the PUT body and the API can store it as
 * four queryable columns. Before this, "which of last night's jobs produced
 * control-arm data despite being assigned a treatment" was answerable only by
 * reading MinIO objects one at a time.
 *
 * <p>
 * Each case runs on its own thread, because the verdict is held in the same
 * per-job {@link ThreadLocal} the warnings are, and a fresh thread is the honest
 * way to get the empty starting state a real job has.
 */
class LambdaSelectJobVerdictTest {

	private static LambdaSelectJobResult parse(String json) throws Exception {
		return new ObjectMapper().readValue(json, LambdaSelectJobResult.class);
	}

	/** Run {@code action} on a fresh thread and return the verdict it recorded. */
	private static DecisionVerdict verdictRecordedBy(ThrowingRunnable action) throws Exception {
		AtomicReference<DecisionVerdict> collected = new AtomicReference<>();
		AtomicReference<Throwable> failure = new AtomicReference<>();

		Thread thread = new Thread(() -> {
			try {
				action.run();
				collected.set(JobDiagnostics.decisionVerdict());
			} catch (Throwable t) {
				failure.set(t);
			}
		}, "lambda-verdict-test");

		thread.start();
		thread.join(30_000);

		if (failure.get() != null) {
			throw new AssertionError("the action under test threw", failure.get());
		}

		return collected.get();
	}

	private interface ThrowingRunnable {
		void run() throws Exception;
	}

	@Test
	@DisplayName("a degraded decision is recorded on the job")
	void degradedDecisionIsRecorded() throws Exception {
		DecisionVerdict verdict = verdictRecordedBy(() -> {
			LambdaSelectJobResult parsed = parse("{\"xpath\": \"//a\", \"degraded\": true,"
					+ " \"treatment_lost\": false, \"selection_mode\": \"llm\","
					+ " \"filters\": {\"similar\": \"failed: RateLimitError: Rate limit reached\","
					+ " \"political\": \"ok\"}}");
			new LambdaSelectJobHandler().reportDegradation(parsed, 4711L, new LinkedHashSet<>());
		});

		assertThat(verdict).isNotNull();
		assertThat(verdict.isDegraded()).isTrue();
		assertThat(verdict.isTreatmentLost()).isFalse();
		assertThat(verdict.getSelectionMode()).isEqualTo("llm");
		assertThat(verdict.applied()).containsExactly("political");
	}

	@Test
	@DisplayName("a payload predating the degradation report records NO verdict")
	void legacyPayloadRecordsNothing() throws Exception {
		// Unknown must stay unknown all the way to the database. If this recorded a
		// clean verdict, redeploying the jar against today's decision function would
		// mark thousands of never-measured decisions as verified-good.
		DecisionVerdict verdict = verdictRecordedBy(() -> {
			LambdaSelectJobResult parsed = parse("{\"xpath\": \"//a\", \"selected\": {\"id\": \"x\"},"
					+ " \"warnings\": [\"UserWarning: Aversion to mainstream media channels ignored\"]}");
			new LambdaSelectJobHandler().reportDegradation(parsed, 3900L, new LinkedHashSet<>());
		});

		assertThat(verdict).isNull();
	}

	@Test
	@DisplayName("a healthy control-arm decision is recorded, and attaches no warning")
	void controlArmDecisionIsRecordedWithoutWarning() throws Exception {
		AtomicReference<DecisionVerdict> recorded = new AtomicReference<>();
		AtomicReference<Integer> warningCount = new AtomicReference<>();

		Thread thread = new Thread(() -> {
			try {
				LambdaSelectJobResult parsed = parse("{\"xpath\": \"//a\", \"degraded\": false,"
						+ " \"treatment_lost\": false, \"selection_mode\": \"random\","
						+ " \"filters\": {\"similar\": \"skipped\", \"political\": \"skipped\"}}");
				new LambdaSelectJobHandler().reportDegradation(parsed, 12L, new LinkedHashSet<>());
				recorded.set(JobDiagnostics.decisionVerdict());
				warningCount.set(JobDiagnostics.warnings().size());
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}, "lambda-verdict-control-test");
		thread.start();
		thread.join(30_000);

		// selection_mode "random" with nothing requested is the correct output of a
		// control crawler and describes 82% of this host's decisions. It is recorded
		// as a measurement; it is not a problem, and nothing here may branch on it.
		assertThat(recorded.get()).isNotNull();
		assertThat(recorded.get().isDegraded()).isFalse();
		assertThat(recorded.get().isTreatmentLost()).isFalse();
		assertThat(warningCount.get()).isZero();
	}

	@Test
	@DisplayName("across a click retry the more severe decision is the one recorded")
	void theWorseOfTwoDecisionsWins() throws Exception {
		DecisionVerdict verdict = verdictRecordedBy(() -> {
			LambdaSelectJobHandler handler = new LambdaSelectJobHandler();
			LinkedHashSet<String> reported = new LinkedHashSet<>();

			// first decision lost the treatment; the click failed, so the handler
			// re-invoked and got a clean one back
			handler.reportDegradation(parse("{\"degraded\": true, \"treatment_lost\": true,"
					+ " \"selection_mode\": \"random\"}"), 1L, reported);
			handler.reportDegradation(parse("{\"degraded\": false, \"treatment_lost\": false,"
					+ " \"selection_mode\": \"llm\"}"), 1L, reported);
		});

		// the session already contains that lost decision -- recording the job as
		// clean because a retry went well would be the false-clean reading again
		assertThat(verdict).isNotNull();
		assertThat(verdict.isTreatmentLost()).isTrue();
	}

	@Test
	@DisplayName("a later decision of equal severity replaces the earlier one")
	void aLaterEquallySevereDecisionWins() throws Exception {
		DecisionVerdict verdict = verdictRecordedBy(() -> {
			LambdaSelectJobHandler handler = new LambdaSelectJobHandler();
			LinkedHashSet<String> reported = new LinkedHashSet<>();

			handler.reportDegradation(parse("{\"degraded\": false, \"selection_mode\": \"random\"}"), 1L, reported);
			handler.reportDegradation(parse("{\"degraded\": false, \"selection_mode\": \"llm\"}"), 1L, reported);
		});

		assertThat(verdict).isNotNull();
		assertThat(verdict.getSelectionMode()).isEqualTo("llm");
	}
}
