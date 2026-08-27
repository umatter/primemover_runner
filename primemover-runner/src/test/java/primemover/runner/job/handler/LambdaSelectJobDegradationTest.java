package primemover.runner.job.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import primemover.runner.JobDiagnostics;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * How a degraded decision reaches the job's status.
 *
 * <p>
 * A degraded decision is not a job failure -- the click happened, the recorded
 * data is worth keeping -- but it must not be reported as {@code success}
 * either, because that is precisely how a treatment arm that silently became a
 * control arm stayed invisible. The handler attaches the diagnosis through
 * {@link JobDiagnostics}, which makes {@code TaskOperator} finish the job as
 * {@code warning} with these texts as the {@code status_message} detail.
 *
 * <p>
 * The gate is the decision function's own {@code treatment_lost} flag, and
 * nothing here branches on {@code selection_mode} alone: {@code "random"} is the
 * correct, expected answer for a control crawler and describes 82% of this
 * host's recorded decisions.
 *
 * <p>
 * Each case runs on its own thread: the warning list is a {@link ThreadLocal}
 * owned by {@code TaskOperator}, so a fresh thread is the honest way to get the
 * empty starting state a real job has.
 */
class LambdaSelectJobDegradationTest {

	private static LambdaSelectJobResult parse(String json) throws Exception {
		return new ObjectMapper().readValue(json, LambdaSelectJobResult.class);
	}

	/** Run {@code action} on a fresh thread and return the warnings it attached. */
	private static List<String> warningsAttachedBy(ThrowingRunnable action) throws Exception {
		AtomicReference<List<String>> collected = new AtomicReference<>(new ArrayList<>());
		AtomicReference<Throwable> failure = new AtomicReference<>();

		Thread thread = new Thread(() -> {
			try {
				action.run();
				collected.set(JobDiagnostics.warnings());
			} catch (Throwable t) {
				failure.set(t);
			}
		}, "lambda-degradation-test");

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
	@DisplayName("treatment_lost=true is attached to the job, naming the llm failure")
	void treatmentLostBecomesAJobWarning() throws Exception {
		List<String> warnings = warningsAttachedBy(() -> {
			LambdaSelectJobResult parsed = parse("{\"xpath\": \"//a\", \"degraded\": true,"
					+ " \"treatment_lost\": true, \"selection_mode\": \"random\","
					+ " \"filters\": {\"similar\": \"failed: RateLimitError: Rate limit reached\","
					+ " \"political\": \"skipped\"},"
					+ " \"llm\": {\"available\": false, \"error_type\": \"rate_limit\","
					+ " \"error\": \"Rate limit reached for gpt-4o\"}}");
			new LambdaSelectJobHandler().reportDegradation(parsed, 3828L, new LinkedHashSet<>());
		});

		assertThat(warnings).anySatisfy(w -> assertThat(w)
				.contains("treatment_lost")
				.contains("control-arm data")
				.contains("rate_limit")
				.doesNotContain("\n"));
	}

	@Test
	@DisplayName("each failed filter is attached by name")
	void failedFiltersBecomeJobWarnings() throws Exception {
		List<String> warnings = warningsAttachedBy(() -> {
			LambdaSelectJobResult parsed = parse("{\"xpath\": \"//a\", \"degraded\": true,"
					+ " \"treatment_lost\": false, \"selection_mode\": \"llm\","
					+ " \"filters\": {\"already_watched\": \"ok\","
					+ " \"mainstream_media\": \"failed: KeyError: 'channel'\","
					+ " \"fringe\": \"failed: APITimeoutError: Request timed out\"}}");
			new LambdaSelectJobHandler().reportDegradation(parsed, 1L, new LinkedHashSet<>());
		});

		assertThat(warnings).hasSize(2);
		assertThat(warnings).anySatisfy(w -> assertThat(w).contains("mainstream_media").contains("KeyError"));
		assertThat(warnings).anySatisfy(w -> assertThat(w).contains("fringe").contains("APITimeoutError"));
		assertThat(warnings).noneSatisfy(w -> assertThat(w).contains("treatment_lost"));
	}

	@Test
	@DisplayName("a clean decision attaches nothing, so the job still finishes as success")
	void cleanDecisionAttachesNothing() throws Exception {
		List<String> warnings = warningsAttachedBy(() -> {
			LambdaSelectJobResult parsed = parse("{\"xpath\": \"//a\", \"degraded\": false,"
					+ " \"treatment_lost\": false, \"selection_mode\": \"llm\", \"warnings\": [],"
					+ " \"filters\": {\"similar\": \"ok\", \"political\": \"skipped\"},"
					+ " \"llm\": {\"available\": true, \"error_type\": null, \"error\": null}}");
			new LambdaSelectJobHandler().reportDegradation(parsed, 1L, new LinkedHashSet<>());
		});

		assertThat(warnings).isEmpty();
	}

	@Test
	@DisplayName("a control crawler draws at random without anything being degraded")
	void controlArmIsRandomButNotAWarning() throws Exception {
		// The exact body the decision function returns for pi=psi=kappa=tau=0: no LLM
		// rating filter was requested, so the draw is uniform and selection_mode is
		// "random" -- correctly, with degraded and treatment_lost both false.
		// Reporting that as a job warning would flag the whole control arm of every
		// factorial design: 295 of the 358 lambda payloads in this host's rotated
		// runner logs look like this, and 236 of the 243 live bot configurations are
		// control-arm.
		List<String> warnings = warningsAttachedBy(() -> {
			LambdaSelectJobResult parsed = parse("{\"xpath\": \"//a\", \"degraded\": false,"
					+ " \"treatment_lost\": false, \"selection_mode\": \"random\", \"warnings\": [],"
					+ " \"filters\": {\"already_watched\": \"ok\", \"children_entertainment\": \"ok\","
					+ " \"similar\": \"skipped\", \"political\": \"skipped\","
					+ " \"fringe\": \"skipped\", \"mainstream_media\": \"skipped\"},"
					+ " \"llm\": {\"available\": true, \"error_type\": null, \"error\": null}}");
			new LambdaSelectJobHandler().reportDegradation(parsed, 4242L, new LinkedHashSet<>());
		});

		assertThat(warnings).isEmpty();
	}

	@Test
	@DisplayName("degraded but treatment_lost=false is a warning, but NOT the treatment-lost alarm")
	void partiallyDegradedTreatmentIsNotTreatmentLost() throws Exception {
		// Two filters requested, one applied and one raised: the session still carries
		// a treatment, so its data is not void. degraded says "look at this",
		// treatment_lost says "this is scientifically void" -- and only the source can
		// tell them apart, which is why the flag is not recomputed here whenever it is
		// present. Note selection_mode is "random" and the answer is still "no alarm".
		List<String> warnings = warningsAttachedBy(() -> {
			LambdaSelectJobResult parsed = parse("{\"xpath\": \"//a\", \"degraded\": true,"
					+ " \"treatment_lost\": false, \"selection_mode\": \"random\","
					+ " \"filters\": {\"similar\": \"ok\","
					+ " \"political\": \"failed: APITimeoutError: Request timed out\"}}");
			new LambdaSelectJobHandler().reportDegradation(parsed, 4244L, new LinkedHashSet<>());
		});

		assertThat(warnings).hasSize(1);
		assertThat(warnings.get(0)).contains("political").contains("APITimeoutError");
		assertThat(warnings).noneSatisfy(w -> assertThat(w).contains("treatment_lost"));
	}

	@Test
	@DisplayName("treatment_lost=true is the alarm even when the mode is not random")
	void treatmentLostGovernsWhateverTheModeSays() throws Exception {
		// Selection failed outright, or the LLM answered without the requested rating:
		// the flag decides, not the mode.
		List<String> warnings = warningsAttachedBy(() -> {
			LambdaSelectJobResult parsed = parse("{\"xpath\": \"//a\", \"degraded\": true,"
					+ " \"treatment_lost\": true, \"selection_mode\": \"llm\","
					+ " \"filters\": {\"fringe\": \"failed: KeyError: 'rating'\"}}");
			new LambdaSelectJobHandler().reportDegradation(parsed, 4245L, new LinkedHashSet<>());
		});

		assertThat(warnings).anySatisfy(w -> assertThat(w).contains("treatment_lost"));
	}

	@Test
	@DisplayName("a report without the flag falls back to the two-term rule")
	void treatmentLostIsRecomputedWhenAbsent() throws Exception {
		// A decision function new enough to report degradation but not new enough to
		// compute the flag: degraded AND random still means the treatment was lost.
		List<String> warnings = warningsAttachedBy(() -> {
			LambdaSelectJobResult parsed = parse("{\"xpath\": \"//a\", \"degraded\": true,"
					+ " \"selection_mode\": \"random\","
					+ " \"filters\": {\"similar\": \"failed: APITimeoutError: Request timed out\"},"
					+ " \"llm\": {\"available\": false, \"error_type\": \"timeout\","
					+ " \"error\": \"Request timed out\"}}");
			new LambdaSelectJobHandler().reportDegradation(parsed, 4243L, new LinkedHashSet<>());
		});

		assertThat(warnings).anySatisfy(w -> assertThat(w).contains("treatment_lost"));
	}

	@Test
	@DisplayName("a payload predating the report attaches nothing either")
	void legacyPayloadAttachesNothing() throws Exception {
		// Otherwise pointing lambda.mode at the older AWS image would turn every single
		// job into a warning, and a status that is always on says nothing.
		List<String> warnings = warningsAttachedBy(() -> {
			LambdaSelectJobResult parsed = parse("{\"xpath\": \"//a\", \"selected\": {\"id\": \"x\"}}");
			new LambdaSelectJobHandler().reportDegradation(parsed, 1L, new LinkedHashSet<>());
		});

		assertThat(warnings).isEmpty();
	}

	@Test
	@DisplayName("a legacy payload's own warnings are logged but do NOT change the job status")
	void legacyWarningsDoNotFlipTheJobToWarning() throws Exception {
		// The decision function deployed TODAY emits a non-empty warnings[] for about
		// 5% of decisions (19 of 358 measured), several of them benign -- a numeric
		// coercion, a filter noting it had nothing to do. Attaching those would flip
		// those jobs from success to warning on the day this jar is deployed, with no
		// other component having changed and nothing actually wrong. They belong in
		// the log (reportDegradation logs them at WARN) and in the recorded payload,
		// not in the job's status.
		List<String> warnings = warningsAttachedBy(() -> {
			LambdaSelectJobResult parsed = parse("{\"xpath\": \"//a\", \"selected\": {\"id\": \"x\"},"
					+ " \"warnings\": [\"UserWarning (line 512): Aversion to mainstream media channels ignored\","
					+ " \"FutureWarning (line 88): Downcasting object dtype arrays is deprecated\"]}");
			new LambdaSelectJobHandler().reportDegradation(parsed, 3900L, new LinkedHashSet<>());
		});

		assertThat(warnings).isEmpty();
	}

	@Test
	@DisplayName("a re-invocation on a click retry does not attach the same warning twice")
	void retriesDoNotDuplicateWarnings() throws Exception {
		List<String> warnings = warningsAttachedBy(() -> {
			String payload = "{\"xpath\": \"//a\", \"degraded\": true, \"treatment_lost\": true,"
					+ " \"selection_mode\": \"random\"}";
			LambdaSelectJobHandler handler = new LambdaSelectJobHandler();
			Set<String> reported = new LinkedHashSet<>();

			// the handler re-invokes the decision function up to twice more while
			// retrying the click; the job must not accumulate three copies of one fact
			handler.reportDegradation(parse(payload), 1L, reported);
			handler.reportDegradation(parse(payload), 1L, reported);
			handler.reportDegradation(parse(payload), 1L, reported);
		});

		assertThat(warnings).hasSize(1);
	}

	@Test
	@DisplayName("the decision function's own warnings ride along with a degradation, capped and single-line")
	void lambdaWarningsAreAttachedButBounded() {
		List<String> warnings = new ArrayList<>();
		for (int i = 0; i < 12; i++) {
			warnings.add("UserWarning (line " + i + "): something\n  went wrong");
		}

		LambdaSelectJobResult parsed = new LambdaSelectJobResult();
		parsed.setWarnings(warnings);
		// a report IS present and says the decision degraded, so the free-text
		// warnings are the detail of a warning the job is getting anyway
		parsed.setDegraded(Boolean.TRUE);
		parsed.setTreatmentLost(Boolean.FALSE);

		List<String> described = LambdaSelectJobHandler.describeDegradation(parsed);

		// one "degraded, no filter named" line, then the capped warnings plus the
		// "(+N more)" line
		assertThat(described).hasSize(1 + LambdaSelectJobHandler.MAX_LAMBDA_WARNINGS_REPORTED + 1);
		assertThat(described).allSatisfy(w -> assertThat(w).doesNotContain("\n"));
		assertThat(described.get(described.size() - 1)).contains("more warnings");
	}

	@Test
	@DisplayName("a healthy decision's own warnings are informational and attach nothing")
	void warningsWithoutADegradationAttachNothing() {
		LambdaSelectJobResult parsed = new LambdaSelectJobResult();
		parsed.setWarnings(List.of("UserWarning (line 512): nothing to filter"));
		parsed.setDegraded(Boolean.FALSE);
		parsed.setTreatmentLost(Boolean.FALSE);
		parsed.setSelectionMode("random");

		assertThat(LambdaSelectJobHandler.describeDegradation(parsed)).isEmpty();
	}

	@Test
	@DisplayName("a long detail is truncated, so one warning cannot crowd out the rest")
	void longDetailsAreTruncated() {
		LambdaSelectJobResult parsed = new LambdaSelectJobResult();
		parsed.setWarnings(List.of("x".repeat(5000)));
		parsed.setDegraded(Boolean.TRUE);

		List<String> described = LambdaSelectJobHandler.describeDegradation(parsed);

		assertThat(described).hasSize(2);
		assertThat(described.get(1).length())
				.isLessThanOrEqualTo(LambdaSelectJobHandler.MAX_WARNING_DETAIL_CHARS + "lambda: ".length());
		assertThat(described.get(1)).endsWith("...");
	}

	@Test
	@DisplayName("a null result is not a degradation")
	void nullResultIsHandled() {
		assertThat(LambdaSelectJobHandler.describeDegradation(null)).isEmpty();
	}
}
