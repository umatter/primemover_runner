package primemover.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import primemover.model.IJob;
import primemover.model.JobState;
import primemover.model.jobs.LambdaSelectJob;
import primemover.runner.DecisionVerdict;
import primemover.runner.job.handler.LambdaSelectJobResult;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What happens when the API will not take the verdict.
 *
 * <p>
 * The four {@code decision_*} keys ride on the same PUT as {@code status_code}.
 * That is convenient and it is also the one asymmetry that matters: the verdict
 * is a <em>description</em> of a job, while {@code status_code} is the job's
 * terminal state, and the queue rollup, {@code runs_bad_count}, the GUI and
 * every analysis join on the latter. Losing the verdict costs a column; losing
 * the status leaves a finished job at {@code running} for ever and silently
 * skews every rollup over the window.
 *
 * <p>
 * So a rejection that looks like it is about the new keys costs the verdict
 * only: the service strips them and re-sends the status once. The condition is
 * not hypothetical -- the API source is bind-mounted into its php container, so
 * branch code is live the instant it lands on disk while {@code artisan migrate}
 * is a separate command, and in that window Laravel mass-assigns
 * {@code decision_degraded} and MySQL answers {@code Unknown column}. A rollback
 * re-creates it. The deploy order says API first; nothing enforces that, and
 * this makes it not matter.
 *
 * <p>
 * The retry is deliberately narrow. It happens <b>once</b>, only when the body
 * actually carried a verdict, and only when the API answered with a status that
 * can plausibly be about the body -- never on a transport failure and never on
 * 401/403/404/405, which are about who is asking and where.
 */
class JobServiceVerdictStripRetryTest {

	private static final String[] DECISION_KEYS = { "decision_degraded", "decision_treatment_lost",
			"decision_selection_mode", "decision_report" };

	private ScriptedRestTemplate restTemplate;
	private JobService service;

	@BeforeEach
	void setUp() {
		restTemplate = new ScriptedRestTemplate();

		service = new JobService();
		ReflectionTestUtils.setField(service, "restTemplate", restTemplate);
		ReflectionTestUtils.setField(service, "endpointPathJobs", "http://localhost:8090/api/v1/runner/jobs");
		ReflectionTestUtils.setField(service, "endpointPathQueues", "http://localhost:8090/api/v1/runner/queues");
	}

	/**
	 * A {@link RestTemplate} that answers each request from a scripted rule.
	 *
	 * <p>
	 * A subclass rather than a Mockito mock for the same reason as
	 * {@code JobServiceDecisionVerdictTest}: this build's CI runs on JDK 17
	 * <em>and</em> 25, and the inline mock maker cannot instrument a class
	 * hierarchy on 25.
	 */
	private static final class ScriptedRestTemplate extends RestTemplate {

		/** Body -> the response to give, or null to answer 200. */
		private Function<String, RuntimeException> rejection = body -> null;

		private final List<String> bodies = new ArrayList<>();

		@Override
		public <T> ResponseEntity<T> exchange(String url, HttpMethod method, HttpEntity<?> requestEntity,
				Class<T> responseType, Object... uriVariables) {
			String body = String.valueOf(requestEntity.getBody());
			bodies.add(body);

			RuntimeException refusal = rejection.apply(body);
			if (refusal != null) {
				throw refusal;
			}

			return new ResponseEntity<>(HttpStatus.OK);
		}

		private JSONObject bodyAt(int index) throws JSONException {
			return new JSONObject(bodies.get(index));
		}
	}

	private static HttpServerErrorException unknownColumn() {
		// what Laravel answers when the code is live and the migration is not:
		// SQLSTATE[42S22]: Unknown column 'decision_degraded' -> 500
		return HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
				HttpHeaders.EMPTY, "SQLSTATE[42S22]: Unknown column 'decision_degraded'".getBytes(), null);
	}

	private static DecisionVerdict verdict() throws Exception {
		LambdaSelectJobResult parsed = new ObjectMapper().readValue(
				"{\"xpath\": \"//a\", \"degraded\": true, \"treatment_lost\": true, \"selection_mode\": \"random\","
						+ " \"filters\": {\"already_watched\": \"not_implemented\","
						+ " \"mainstream_media\": \"failed: FileNotFoundError: csv\"}}",
				LambdaSelectJobResult.class);
		return DecisionVerdict.of(parsed);
	}

	private static IJob lambdaJob() {
		LambdaSelectJob job = new LambdaSelectJob();
		job.setId(4711L);
		job.setName("lambdaselect");
		return job;
	}

	private Boolean reportSuccess(DecisionVerdict verdict) {
		return service.updateJobState(lambdaJob(), JobState.SUCCESS, null, null, null, null, null, verdict);
	}

	private static List<String> keysOf(JSONObject object) {
		List<String> keys = new ArrayList<>();
		for (java.util.Iterator<String> it = object.keys(); it.hasNext();) {
			keys.add(it.next());
		}
		return keys;
	}

	@Test
	@DisplayName("an API that rejects the verdict still records the job's status")
	void aRejectedVerdictDoesNotCostTheStatus() throws Exception {
		// The pre-migration window: any body carrying decision_* blows up, a body
		// without them is fine.
		restTemplate.rejection = body -> body.contains("decision_degraded") ? unknownColumn() : null;

		assertThat(reportSuccess(verdict())).isTrue();

		assertThat(restTemplate.bodies).hasSize(2);

		JSONObject first = restTemplate.bodyAt(0);
		assertThat(keysOf(first)).contains(DECISION_KEYS);

		// the second PUT carries the status and nothing about the decision
		JSONObject second = restTemplate.bodyAt(1);
		assertThat(keysOf(second)).doesNotContain(DECISION_KEYS);
		assertThat(second.getString("status_code")).isEqualTo("success");
		assertThat(second.getString("status_message")).isEqualTo("successfully finished job");
	}

	@Test
	@DisplayName("a 422 over the new fields is stripped and retried too")
	void aValidationRejectionIsAlsoStrippedAndRetried() throws Exception {
		restTemplate.rejection = body -> body.contains("decision_report")
				? HttpClientErrorException.create(HttpStatus.UNPROCESSABLE_ENTITY, "Unprocessable Content",
						HttpHeaders.EMPTY, "{\"errors\":{\"decision_report\":[\"invalid\"]}}".getBytes(), null)
				: null;

		assertThat(reportSuccess(verdict())).isTrue();

		assertThat(restTemplate.bodies).hasSize(2);
		assertThat(keysOf(restTemplate.bodyAt(1))).doesNotContain(DECISION_KEYS);
		assertThat(restTemplate.bodyAt(1).getString("status_code")).isEqualTo("success");
	}

	@Test
	@DisplayName("a PUT the API accepts is sent exactly once")
	void aSuccessfulPutIsNotRetried() throws Exception {
		assertThat(reportSuccess(verdict())).isTrue();

		assertThat(restTemplate.bodies).hasSize(1);
		assertThat(keysOf(restTemplate.bodyAt(0))).contains(DECISION_KEYS);
	}

	@Test
	@DisplayName("the retry happens once, not for ever")
	void theRetryIsBounded() throws Exception {
		// An API that refuses everything: the service must give up after the single
		// stripped retry rather than trying its way through the outage.
		restTemplate.rejection = body -> unknownColumn();

		assertThat(reportSuccess(verdict())).isFalse();

		assertThat(restTemplate.bodies).hasSize(2);
		assertThat(keysOf(restTemplate.bodyAt(0))).contains(DECISION_KEYS);
		assertThat(keysOf(restTemplate.bodyAt(1))).doesNotContain(DECISION_KEYS);
	}

	@Test
	@DisplayName("a genuine status-update failure is still reported as one")
	void aGenuineFailureIsNotMasked() throws Exception {
		restTemplate.rejection = body -> unknownColumn();

		// false, not true: the status never landed, and the caller must not be told
		// it did just because a second attempt was made.
		assertThat(reportSuccess(verdict())).isFalse();
	}

	@Test
	@DisplayName("a PUT that carried no verdict is not retried at all")
	void aBodyWithoutAVerdictIsNotRetried() {
		restTemplate.rejection = body -> unknownColumn();

		// Nothing to strip. Retrying the identical body would only double the load on
		// an API that is already refusing it.
		assertThat(service.updateJobState(lambdaJob(), JobState.RUNNING, null, null, null, null, null)).isFalse();

		assertThat(restTemplate.bodies).hasSize(1);
	}

	@Test
	@DisplayName("a transport failure is not retried: the body never reached anyone")
	void aTransportFailureIsNotRetried() throws Exception {
		restTemplate.rejection = body -> new ResourceAccessException("I/O error on PUT",
				new IOException("Connection refused"));

		assertThat(reportSuccess(verdict())).isFalse();

		assertThat(restTemplate.bodies).hasSize(1);
	}

	@Test
	@DisplayName("401 / 403 / 404 / 405 are about who and where, never about the body")
	void anAuthOrRoutingRejectionIsNotRetried() throws Exception {
		for (HttpStatus status : new HttpStatus[] { HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN,
				HttpStatus.NOT_FOUND, HttpStatus.METHOD_NOT_ALLOWED }) {
			restTemplate.bodies.clear();
			restTemplate.rejection = body -> HttpClientErrorException.create(status, status.getReasonPhrase(),
					HttpHeaders.EMPTY, new byte[0], null);

			assertThat(reportSuccess(verdict())).isFalse();

			assertThat(restTemplate.bodies).as("requests sent for %s", status).hasSize(1);
		}
	}

	@Test
	@DisplayName("the stripped retry keeps every non-decision field of the original body")
	void theStrippedRetryKeepsTheRestOfTheBody() throws Exception {
		restTemplate.rejection = body -> body.contains("decision_degraded") ? unknownColumn() : null;

		java.time.ZonedDateTime start = java.time.ZonedDateTime.parse("2026-09-08T10:00:00+02:00");
		java.time.ZonedDateTime end = java.time.ZonedDateTime.parse("2026-09-08T10:04:00+02:00");

		assertThat(service.updateJobState(lambdaJob(), JobState.WARNING, "lambda treatment_lost", Boolean.TRUE,
				Boolean.FALSE, start, end, verdict())).isTrue();

		JSONObject retried = restTemplate.bodyAt(1);
		assertThat(retried.getString("status_code")).isEqualTo("warning");
		assertThat(retried.getString("status_message")).contains("lambda treatment_lost");
		assertThat(retried.getBoolean("captcha_found")).isTrue();
		assertThat(retried.getBoolean("captcha_solved")).isFalse();
		assertThat(retried.getString("started_at")).isNotEmpty();
		assertThat(retried.getString("finished_at")).isNotEmpty();
		assertThat(keysOf(retried)).doesNotContain(DECISION_KEYS);
	}
}
