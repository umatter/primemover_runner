package primemover.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import primemover.model.IJob;
import primemover.model.JobState;
import primemover.model.jobs.LambdaSelectJob;
import primemover.model.jobs.WaitJob;
import primemover.runner.DecisionVerdict;
import primemover.runner.JobDiagnostics;
import primemover.runner.job.handler.LambdaSelectJobResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The four decision fields on the job PUT.
 *
 * <p>
 * The decision function's verdict used to reach the API only as prose inside
 * {@code status_message} -- unqueryable, and lost entirely for jobs that were not
 * degraded. These four keys make it a column:
 *
 * <pre>
 * decision_degraded         bool    a requested filter did not apply
 * decision_treatment_lost   bool    a treated bot produced control-arm data (the alarm)
 * decision_selection_mode   string  descriptive only; never a gate
 * decision_report           object  {filters, llm, warnings, applied, requested}
 * </pre>
 *
 * <p>
 * <b>The important assertions here are the absences.</b> A legacy payload, a
 * non-lambda job and a decision function predating the report must send
 * <em>no</em> key at all, because on the API side a missing key means "unknown"
 * and a {@code false} means "checked, and fine". Sending {@code false} for
 * something nobody measured is the exact mistake that let a treatment arm turn
 * into a second control arm unnoticed.
 */
class JobServiceDecisionVerdictTest {

	private static final String[] DECISION_KEYS = { "decision_degraded", "decision_treatment_lost",
			"decision_selection_mode", "decision_report" };

	private RecordingRestTemplate restTemplate;
	private JobService service;

	@BeforeEach
	void setUp() {
		restTemplate = new RecordingRestTemplate();

		service = new JobService();
		ReflectionTestUtils.setField(service, "restTemplate", restTemplate);
		ReflectionTestUtils.setField(service, "endpointPathJobs", "http://localhost:8090/api/v1/runner/jobs");
		ReflectionTestUtils.setField(service, "endpointPathQueues", "http://localhost:8090/api/v1/runner/queues");
	}

	/**
	 * A {@link RestTemplate} that records the request instead of sending it.
	 *
	 * <p>
	 * Deliberately a subclass rather than a Mockito mock: the runner's CI builds on
	 * JDK 17 <em>and</em> 25, and Mockito's inline mock maker cannot instrument a
	 * class hierarchy on 25 with the byte-buddy this build resolves
	 * ("Java 25 (69) is not supported"). Overriding one non-final method needs no
	 * instrumentation at all and works on every JDK.
	 */
	private static final class RecordingRestTemplate extends RestTemplate {

		private final List<HttpEntity<?>> requests = new ArrayList<>();
		private final List<HttpMethod> methods = new ArrayList<>();

		@Override
		public <T> ResponseEntity<T> exchange(String url, HttpMethod method, HttpEntity<?> requestEntity,
				Class<T> responseType, Object... uriVariables) {
			methods.add(method);
			requests.add(requestEntity);
			return new ResponseEntity<>(HttpStatus.OK);
		}
	}

	private static LambdaSelectJobResult parse(String json) throws Exception {
		return new ObjectMapper().readValue(json, LambdaSelectJobResult.class);
	}

	private static IJob lambdaJob() {
		LambdaSelectJob job = new LambdaSelectJob();
		job.setId(4711L);
		job.setName("lambdaselect");
		return job;
	}

	/** The body of the single PUT the service sent. */
	private JSONObject sentBody() throws JSONException {
		assertThat(restTemplate.requests).hasSize(1);
		assertThat(restTemplate.methods).containsExactly(HttpMethod.PUT);
		return new JSONObject(String.valueOf(restTemplate.requests.get(0).getBody()));
	}

	private JSONObject reportFor(IJob job, DecisionVerdict verdict) throws JSONException {
		service.updateJobState(job, JobState.SUCCESS, null, null, null, null, null, verdict);
		return sentBody();
	}

	@Test
	@DisplayName("a degraded payload sends all four keys, correctly")
	void degradedPayloadSendsAllFourFields() throws Exception {
		DecisionVerdict verdict = DecisionVerdict.of(parse("{\"xpath\": \"//a\", \"degraded\": true,"
				+ " \"treatment_lost\": false, \"selection_mode\": \"llm\","
				+ " \"filters\": {\"already_watched\": \"ok\","
				+ " \"similar\": \"failed: RateLimitError: Rate limit reached\","
				+ " \"political\": \"skipped\"},"
				+ " \"llm\": {\"available\": false, \"error_type\": \"rate_limit\", \"error\": \"Rate limit\"},"
				+ " \"warnings\": [\"similar filter could not be applied\"]}"));

		JSONObject body = reportFor(lambdaJob(), verdict);

		assertThat(keysOf(body)).contains(DECISION_KEYS);
		assertThat(body.getBoolean("decision_degraded")).isTrue();
		assertThat(body.getBoolean("decision_treatment_lost")).isFalse();
		assertThat(body.getString("decision_selection_mode")).isEqualTo("llm");

		JSONObject report = body.getJSONObject("decision_report");
		assertThat(keysOf(report)).containsExactlyInAnyOrder("filters", "llm", "warnings", "applied", "requested");
		assertThat(report.getJSONObject("filters").getString("similar")).startsWith("failed: RateLimitError");
		assertThat(report.getJSONObject("filters").getString("political")).isEqualTo("skipped");
		assertThat(report.getJSONObject("llm").getString("error_type")).isEqualTo("rate_limit");
		assertThat(stringsOf(report.getJSONArray("applied"))).containsExactly("already_watched");
		// "skipped" is NOT REQUESTED, so political must not appear as a casualty
		assertThat(stringsOf(report.getJSONArray("requested"))).containsExactlyInAnyOrder("already_watched", "similar");
		assertThat(stringsOf(report.getJSONArray("warnings"))).containsExactly("similar filter could not be applied");

		// and the status is untouched: this stage adds fields only
		assertThat(body.getString("status_code")).isEqualTo("success");
		assertThat(body.getString("status_message")).isEqualTo("successfully finished job");
	}

	@Test
	@DisplayName("a treatment-lost payload sends decision_treatment_lost true")
	void treatmentLostPayloadSendsTheAlarm() throws Exception {
		DecisionVerdict verdict = DecisionVerdict.of(parse("{\"degraded\": true, \"treatment_lost\": true,"
				+ " \"selection_mode\": \"random\","
				+ " \"filters\": {\"mainstream_media\": \"failed: FileNotFoundError: csv\"}}"));

		JSONObject body = reportFor(lambdaJob(), verdict);

		assertThat(body.getBoolean("decision_treatment_lost")).isTrue();
		assertThat(body.getBoolean("decision_degraded")).isTrue();
		assertThat(body.getString("decision_selection_mode")).isEqualTo("random");
		assertThat(body.getJSONObject("decision_report").getJSONObject("filters").getString("mainstream_media"))
				.contains("FileNotFoundError");
	}

	@Test
	@DisplayName("a legacy payload with no degradation report sends none of the four keys")
	void legacyPayloadSendsNoDecisionFields() throws Exception {
		// Every one of the 358 payloads recorded on this box today is this shape.
		// Absence must travel all the way to the database as absence.
		DecisionVerdict verdict = DecisionVerdict.of(parse("{\"xpath\": \"//a\", \"selected\": {\"id\": \"x\"},"
				+ " \"warnings\": [\"UserWarning: Aversion to mainstream media channels ignored\"]}"));
		assertThat(verdict).isNull();

		JSONObject body = reportFor(lambdaJob(), verdict);

		assertThat(keysOf(body)).doesNotContain(DECISION_KEYS);
		// the job still reports normally; only the verdict is missing
		assertThat(body.getString("status_code")).isEqualTo("success");
	}

	@Test
	@DisplayName("a non-lambda job sends none of the four keys")
	void nonLambdaJobSendsNoDecisionFields() throws Exception {
		WaitJob job = new WaitJob();
		job.setId(88L);

		// A job type that never invokes the decision function records no verdict.
		// Read it the way TaskOperator does, on a thread that ran no handler.
		AtomicReference<DecisionVerdict> fromAJobThatMadeNoDecision = new AtomicReference<>();
		Thread thread = new Thread(() -> fromAJobThatMadeNoDecision.set(JobDiagnostics.decisionVerdict()),
				"non-lambda-job");
		thread.start();
		thread.join(30_000);
		assertThat(fromAJobThatMadeNoDecision.get()).isNull();

		JSONObject body = reportFor(job, fromAJobThatMadeNoDecision.get());

		assertThat(keysOf(body)).doesNotContain(DECISION_KEYS);
	}

	@Test
	@DisplayName("the existing overloads still send no decision fields at all")
	void theOverloadsWithoutAVerdictAreUnchanged() throws Exception {
		// RUNNING / STORINGDATA and the captcha update go through these; none of them
		// knows a verdict yet, and none of them may imply one.
		service.updateJobState(lambdaJob(), JobState.RUNNING, null, null, null, null, null);

		assertThat(keysOf(sentBody())).doesNotContain(DECISION_KEYS);
	}

	@Test
	@DisplayName("a control-arm decision sends degraded=false and treatment_lost=false, and is not a problem")
	void controlArmDecisionSendsExplicitFalses() throws Exception {
		// A control crawler (pi=psi=kappa=tau=0) requests no LLM filter, so it draws
		// at random by design -- 236 of 243 bot configurations on this box. Here the
		// falses are measurements, not defaults: the report exists and says so.
		DecisionVerdict verdict = DecisionVerdict.of(parse("{\"xpath\": \"//a\", \"degraded\": false,"
				+ " \"treatment_lost\": false, \"selection_mode\": \"random\","
				+ " \"filters\": {\"already_watched\": \"skipped\", \"mainstream_media\": \"skipped\","
				+ " \"children_entertainment\": \"skipped\", \"similar\": \"skipped\","
				+ " \"political\": \"skipped\", \"fringe\": \"skipped\"}}"));

		JSONObject body = reportFor(lambdaJob(), verdict);

		assertThat(body.getBoolean("decision_degraded")).isFalse();
		assertThat(body.getBoolean("decision_treatment_lost")).isFalse();
		// the mode is recorded, and it is emphatically not an error signal
		assertThat(body.getString("decision_selection_mode")).isEqualTo("random");
		assertThat(body.getJSONObject("decision_report").getJSONArray("requested").length()).isZero();
		// nothing about this job is bad: the status is success and stays success
		assertThat(body.getString("status_code")).isEqualTo("success");
	}

	@Test
	@DisplayName("a report present but carrying no selection_mode sends an explicit null, not a guess")
	void aReportWithoutASelectionModeSendsNull() throws Exception {
		DecisionVerdict verdict = DecisionVerdict
				.of(parse("{\"degraded\": true, \"filters\": {\"similar\": \"failed: X: y\"}}"));

		JSONObject body = reportFor(lambdaJob(), verdict);

		assertThat(body.has("decision_selection_mode")).isTrue();
		assertThat(body.isNull("decision_selection_mode")).isTrue();
	}

	@Test
	@DisplayName("a pathological payload cannot blow up the PUT")
	void aPathologicalPayloadIsBoundedOnTheWire() throws Exception {
		List<String> warnings = new ArrayList<>();
		for (int i = 0; i < 400; i++) {
			warnings.add("UserWarning (line " + i + "): filter raised\n  " + "x".repeat(3000));
		}

		LambdaSelectJobResult parsed = new LambdaSelectJobResult();
		parsed.setDegraded(Boolean.TRUE);
		parsed.setTreatmentLost(Boolean.TRUE);
		parsed.setSelectionMode("random");
		parsed.setWarnings(warnings);

		JSONObject body = reportFor(lambdaJob(), DecisionVerdict.of(parsed));

		String report = body.getJSONObject("decision_report").toString();
		assertThat(report.length()).isLessThanOrEqualTo(DecisionVerdict.MAX_REPORT_JSON_CHARS);
		assertThat(body.getJSONObject("decision_report").getJSONArray("warnings").length())
				.isLessThanOrEqualTo(DecisionVerdict.MAX_REPORT_WARNINGS + 1);
		// the verdict itself survives intact -- only the evidence is abridged
		assertThat(body.getBoolean("decision_treatment_lost")).isTrue();
	}

	/** {@code JSONObject.keySet()} is absent from the org.json version this build resolves. */
	private static List<String> keysOf(JSONObject object) {
		List<String> keys = new ArrayList<>();
		for (java.util.Iterator<String> it = object.keys(); it.hasNext();) {
			keys.add(it.next());
		}
		return keys;
	}

	private static List<String> stringsOf(JSONArray array) throws JSONException {
		List<String> values = new ArrayList<>();
		for (int i = 0; i < array.length(); i++) {
			values.add(array.getString(i));
		}
		return values;
	}
}
