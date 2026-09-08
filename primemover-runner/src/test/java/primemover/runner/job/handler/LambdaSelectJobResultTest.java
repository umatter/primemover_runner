package primemover.runner.job.handler;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the runner now keeps from the decision function's answer.
 *
 * <p>
 * The field this class exists for is {@code selection_mode}. When it reads
 * {@code "random"}, no LLM filter applied and the video was picked at random --
 * the crawler produced control-arm data whatever treatment it was assigned. That
 * used to be dropped at deserialization, so a whole degraded run was
 * indistinguishable from a clean one in the runner log, in the API and in the
 * GUI.
 *
 * <p>
 * The other half of the contract is backwards compatibility in both directions:
 * a payload from a decision function that predates the report must still parse
 * and must read as "nothing wrong" (otherwise every job against an older
 * function becomes a warning), and an unknown field from a newer one must still
 * be ignored rather than failing the job.
 */
class LambdaSelectJobResultTest {

	/** The mapper configured exactly as {@link LambdaSelectJobHandler} configures its own. */
	private static ObjectMapper handlerMapper() {
		ObjectMapper mapper = JsonMapper.builder()
				.enable(JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS)
				.build();
		mapper.setVisibility(PropertyAccessor.FIELD, Visibility.ANY);
		return mapper;
	}

	private static final String DEGRADED_PAYLOAD = "{"
			+ "\"xpath\": \"//a[@id='thumbnail']\","
			+ "\"selected\": {\"id\": \"abc123\", \"title\": \"A video\"},"
			+ "\"warnings\": [\"UserWarning (line 512): Aversion to mainstream media channels ignored\"],"
			+ "\"degraded\": true,"
			+ "\"treatment_lost\": true,"
			+ "\"selection_mode\": \"random\","
			// "not_implemented" is the state the deployed decision function writes for
			// already_watched on EVERY invocation -- never "ok", never "skipped".
			+ "\"filters\": {\"already_watched\": \"not_implemented\","
			+ " \"mainstream_media\": \"failed: KeyError: 'channel'\","
			+ " \"children_entertainment\": \"skipped\", \"similar\": \"failed: RateLimitError: quota\","
			+ " \"political\": \"skipped\", \"fringe\": \"skipped\"},"
			+ "\"llm\": {\"available\": false, \"error_type\": \"insufficient_quota\","
			+ " \"error\": \"You exceeded your current quota\"}"
			+ "}";

	@Nested
	@DisplayName("a payload carrying the degradation report")
	class DegradedPayload {

		@Test
		@DisplayName("keeps every field of the report, not only xpath and selected")
		void parsesTheReport() throws Exception {
			LambdaSelectJobResult result = handlerMapper().readValue(DEGRADED_PAYLOAD, LambdaSelectJobResult.class);

			assertThat(result.getXpath()).isEqualTo("//a[@id='thumbnail']");
			assertThat(result.getSelected().getId()).isEqualTo("abc123");
			assertThat(result.isDegraded()).isTrue();
			assertThat(result.isTreatmentLost()).isTrue();
			assertThat(result.hasTreatmentLostFlag()).isTrue();
			assertThat(result.getSelectionMode()).isEqualTo("random");
			assertThat(result.isRandomSelection()).isTrue();
			assertThat(result.hasDegradationReport()).isTrue();
			assertThat(result.getWarnings()).hasSize(1);
			assertThat(result.getFilters()).hasSize(6);
		}

		@Test
		@DisplayName("maps the snake_case selection_mode onto selectionMode")
		void parsesSnakeCaseSelectionMode() throws Exception {
			// The JSON name and the Java name differ; without the explicit @JsonProperty
			// the field would silently stay null and every random selection would look
			// like a clean one -- the exact bug this change is here to remove.
			LambdaSelectJobResult result = handlerMapper()
					.readValue("{\"selection_mode\": \"random\"}", LambdaSelectJobResult.class);

			assertThat(result.getSelectionMode()).isEqualTo("random");
			assertThat(result.isRandomSelection()).isTrue();
		}

		@Test
		@DisplayName("parses with a default mapper too, not only the handler's")
		void parsesWithADefaultMapper() throws Exception {
			// The handler raises field visibility for the AWS invocation result; the
			// contract must not depend on that.
			LambdaSelectJobResult result = new ObjectMapper()
					.readValue(DEGRADED_PAYLOAD, LambdaSelectJobResult.class);

			assertThat(result.isRandomSelection()).isTrue();
			assertThat(result.failedFilters()).hasSize(2);
			assertThat(result.llmErrorType()).isEqualTo("insufficient_quota");
		}

		@Test
		@DisplayName("names the filters that failed, and only those")
		void separatesFailedFromSkippedFilters() throws Exception {
			LambdaSelectJobResult result = handlerMapper().readValue(DEGRADED_PAYLOAD, LambdaSelectJobResult.class);

			// "skipped" means the crawler never requested the filter -- normal, not a
			// failure. Reporting it as one would make every control crawler noisy.
			assertThat(result.failedFilters())
					.containsExactlyInAnyOrder("mainstream_media failed: KeyError: 'channel'",
							"similar failed: RateLimitError: quota");
			// Two treatments were requested (both raised) and none applied. The hygiene
			// filter is in neither list whatever it reports about itself.
			assertThat(result.requestedFilters()).containsExactlyInAnyOrder("mainstream_media", "similar");
			assertThat(result.appliedFilters()).isEmpty();
		}

		@Test
		@DisplayName("renders the llm block as one line")
		void describesTheLlmProblem() throws Exception {
			LambdaSelectJobResult result = handlerMapper().readValue(DEGRADED_PAYLOAD, LambdaSelectJobResult.class);

			assertThat(result.isLlmAvailable()).isFalse();
			assertThat(result.describeLlmProblem()).isEqualTo("insufficient_quota: You exceeded your current quota");
		}
	}

	@Nested
	@DisplayName("a payload without the report (an older decision function)")
	class LegacyPayload {

		private static final String LEGACY = "{\"xpath\": \"//a\", \"selected\": {\"id\": \"x\", \"title\": \"t\"},"
				+ " \"present\": [], \"procedure\": {}, \"timestamps\": {}}";

		@Test
		@DisplayName("still parses, and reads as clean rather than as degraded")
		void readsAsClean() throws Exception {
			LambdaSelectJobResult result = handlerMapper().readValue(LEGACY, LambdaSelectJobResult.class);

			assertThat(result.getXpath()).isEqualTo("//a");
			assertThat(result.isDegraded()).isFalse();
			// absence is "unknown", never an alarm
			assertThat(result.isTreatmentLost()).isFalse();
			assertThat(result.hasTreatmentLostFlag()).isFalse();
			assertThat(result.isRandomSelection()).isFalse();
			assertThat(result.hasDegradationReport()).isFalse();
			// absence of an llm block is "not reported", never "the llm was down"
			assertThat(result.isLlmAvailable()).isTrue();
			assertThat(result.describeLlmProblem()).isNull();
		}

		@Test
		@DisplayName("returns empty collections rather than null")
		void accessorsAreNullSafe() throws Exception {
			LambdaSelectJobResult result = handlerMapper().readValue(LEGACY, LambdaSelectJobResult.class);

			assertThat(result.getWarnings()).isEmpty();
			assertThat(result.getFilters()).isEmpty();
			assertThat(result.getLlm()).isEmpty();
			assertThat(result.failedFilters()).isEmpty();
			assertThat(result.appliedFilters()).isEmpty();
			assertThat(result.requestedFilters()).isEmpty();
			assertThat(result.filtersSnapshot()).isEmpty();
			assertThat(result.llmErrorType()).isNull();
			assertThat(result.llmError()).isNull();
		}
	}

	/**
	 * What "the crawler requested this treatment" is derived from.
	 *
	 * <p>
	 * The decision function does not put its own {@code requested} set on the wire,
	 * so the runner reconstructs it from {@code filters} -- and the reconstruction
	 * has to land on the same set the function used as the denominator for
	 * {@code treatment_lost}, or the job row and the alarm disagree about whether a
	 * session was scientifically void.
	 *
	 * <p>
	 * The two states that are <em>not</em> requests and are easy to mistake for
	 * one:
	 * <ul>
	 * <li>{@code not_implemented} -- what the deployed function writes for
	 * {@code already_watched} on <b>every single invocation</b>.</li>
	 * <li>anything else it has not defined -- unknown, and unknown is never a
	 * request.</li>
	 * </ul>
	 */
	@Nested
	@DisplayName("requested / applied, the denominator behind treatment_lost")
	class RequestedFilters {

		/** The filters block of a real control-crawler response, verbatim. */
		private static final String CONTROL_FILTERS = "\"filters\": {\"already_watched\": \"not_implemented\","
				+ " \"mainstream_media\": \"skipped\", \"children_entertainment\": \"skipped\","
				+ " \"similar\": \"skipped\", \"political\": \"skipped\", \"fringe\": \"skipped\"}";

		@Test
		@DisplayName("not_implemented is not a request, so a control job requests nothing")
		void notImplementedIsNotARequest() throws Exception {
			// 236 of 243 live bot configurations are control-arm and every one of them
			// gets this exact filters block. Counting already_watched as requested made
			// each of them persist requested=[already_watched] with applied=[] -- a
			// treatment asked for and not applied, on a job where nothing was asked for
			// at all.
			LambdaSelectJobResult result = handlerMapper()
					.readValue("{\"xpath\": \"//a\", \"degraded\": false, \"treatment_lost\": false,"
							+ " \"selection_mode\": \"random\", " + CONTROL_FILTERS + "}",
							LambdaSelectJobResult.class);

			assertThat(result.requestedFilters()).isEmpty();
			assertThat(result.appliedFilters()).isEmpty();
			assertThat(result.failedFilters()).isEmpty();
			// and the job is not an alarm on any reading
			assertThat(result.isDegraded()).isFalse();
			assertThat(result.isTreatmentLost()).isFalse();
		}

		@Test
		@DisplayName("already_watched is never requested, in any state it can report")
		void theHygieneFilterIsNeverRequested() throws Exception {
			for (String state : new String[] { "not_implemented", "ok", "skipped", "failed: KeyError: 'id'" }) {
				LambdaSelectJobResult result = handlerMapper().readValue(
						"{\"degraded\": true, \"filters\": {\"already_watched\": \"" + state + "\"}}",
						LambdaSelectJobResult.class);

				assertThat(result.requestedFilters()).as("requested for state %s", state).isEmpty();
				assertThat(result.appliedFilters()).as("applied for state %s", state).isEmpty();
			}
		}

		@Test
		@DisplayName("a treatment that was asked for is counted, applied or failed")
		void aRealTreatmentIsCounted() throws Exception {
			// The correction must not go the other way: under-counting requested would
			// hide a genuine casualty. pi=1 with the CSV broken is the incident this
			// whole contract was written for.
			LambdaSelectJobResult applied = handlerMapper().readValue(
					"{\"degraded\": false, \"filters\": {\"already_watched\": \"not_implemented\","
							+ " \"mainstream_media\": \"ok\", \"similar\": \"skipped\"}}",
					LambdaSelectJobResult.class);

			assertThat(applied.requestedFilters()).containsExactly("mainstream_media");
			assertThat(applied.appliedFilters()).containsExactly("mainstream_media");

			LambdaSelectJobResult lost = handlerMapper().readValue(
					"{\"degraded\": true, \"treatment_lost\": true,"
							+ " \"filters\": {\"already_watched\": \"not_implemented\","
							+ " \"mainstream_media\": \"failed: FileNotFoundError: csv\","
							+ " \"similar\": \"skipped\"}}",
					LambdaSelectJobResult.class);

			assertThat(lost.requestedFilters()).containsExactly("mainstream_media");
			assertThat(lost.appliedFilters()).isEmpty();
			assertThat(lost.isTreatmentLost()).isTrue();
		}

		@Test
		@DisplayName("an unrecognised or blank state says nothing, so it is not a request")
		void anUnknownStateIsNotARequest() throws Exception {
			LambdaSelectJobResult result = handlerMapper().readValue(
					"{\"degraded\": false, \"filters\": {\"similar\": \"deferred\", \"political\": \"  \","
							+ " \"fringe\": \"ok\"}}",
					LambdaSelectJobResult.class);

			assertThat(result.requestedFilters()).containsExactly("fringe");
			assertThat(result.appliedFilters()).containsExactly("fringe");
		}

		@Test
		@DisplayName("a filter name added to the contract later is still counted")
		void anUnknownFilterNameIsStillATreatment() throws Exception {
			// The name list is not hard-coded: only the hygiene exclusion is. A future
			// treatment must be visible the day it ships, not the day this file is
			// edited.
			LambdaSelectJobResult result = handlerMapper().readValue(
					"{\"degraded\": true, \"filters\": {\"a_treatment_invented_next_year\": \"failed: X: y\"}}",
					LambdaSelectJobResult.class);

			assertThat(result.requestedFilters()).containsExactly("a_treatment_invented_next_year");
			assertThat(result.appliedFilters()).isEmpty();
		}
	}

	@Test
	@DisplayName("an unknown field from a newer decision function is still ignored")
	void unknownFieldsAreIgnored() throws Exception {
		LambdaSelectJobResult result = handlerMapper().readValue(
				"{\"xpath\": \"//a\", \"a_field_invented_next_year\": {\"nested\": [1, 2]}}",
				LambdaSelectJobResult.class);

		assertThat(result.getXpath()).isEqualTo("//a");
	}

	@Test
	@DisplayName("selection_mode=llm is not a degradation")
	void llmSelectionIsNotRandom() throws Exception {
		LambdaSelectJobResult result = handlerMapper().readValue(
				"{\"selection_mode\": \"llm\", \"degraded\": false,"
						+ " \"filters\": {\"similar\": \"ok\"}, \"llm\": {\"available\": true,"
						+ " \"error_type\": null, \"error\": null}}",
				LambdaSelectJobResult.class);

		assertThat(result.isRandomSelection()).isFalse();
		assertThat(result.isDegraded()).isFalse();
		assertThat(result.failedFilters()).isEmpty();
		assertThat(result.describeLlmProblem()).isNull();
	}

	@Nested
	@DisplayName("treatment_lost, the field consumers gate on")
	class TreatmentLost {

		@Test
		@DisplayName("the explicit flag is taken as given, whatever the other fields say")
		void explicitFlagWins() throws Exception {
			// degraded AND random, yet not void: one requested filter still applied, so
			// the session carries its treatment. The two-term rule a consumer would
			// recompute gets this WRONG; the source knows and says so.
			LambdaSelectJobResult stillTreated = handlerMapper().readValue(
					"{\"degraded\": true, \"treatment_lost\": false, \"selection_mode\": \"random\","
							+ " \"filters\": {\"similar\": \"ok\", \"political\": \"failed: KeyError: x\"}}",
					LambdaSelectJobResult.class);

			assertThat(stillTreated.isDegraded()).isTrue();
			assertThat(stillTreated.isRandomSelection()).isTrue();
			assertThat(stillTreated.isTreatmentLost()).isFalse();

			// and the converse: lost even though the mode says an LLM chose
			LambdaSelectJobResult lost = handlerMapper().readValue(
					"{\"degraded\": true, \"treatment_lost\": true, \"selection_mode\": \"llm\"}",
					LambdaSelectJobResult.class);

			assertThat(lost.isRandomSelection()).isFalse();
			assertThat(lost.isTreatmentLost()).isTrue();
		}

		@Test
		@DisplayName("maps the snake_case name, so the flag is not silently always false")
		void parsesSnakeCaseName() throws Exception {
			LambdaSelectJobResult result = handlerMapper()
					.readValue("{\"treatment_lost\": true}", LambdaSelectJobResult.class);

			assertThat(result.hasTreatmentLostFlag()).isTrue();
			assertThat(result.isTreatmentLost()).isTrue();
			assertThat(result.hasDegradationReport()).isTrue();
		}

		@Test
		@DisplayName("falls back to degraded AND random only when the flag is absent")
		void fallsBackForAPayloadWithoutTheFlag() throws Exception {
			LambdaSelectJobResult lost = handlerMapper().readValue(
					"{\"degraded\": true, \"selection_mode\": \"random\"}", LambdaSelectJobResult.class);
			LambdaSelectJobResult control = handlerMapper().readValue(
					"{\"degraded\": false, \"selection_mode\": \"random\"}", LambdaSelectJobResult.class);
			LambdaSelectJobResult degradedButRated = handlerMapper().readValue(
					"{\"degraded\": true, \"selection_mode\": \"llm\"}", LambdaSelectJobResult.class);

			assertThat(lost.isTreatmentLost()).isTrue();
			assertThat(lost.hasTreatmentLostFlag()).isFalse();
			// a control crawler is never treatment_lost, however it selected
			assertThat(control.isTreatmentLost()).isFalse();
			assertThat(degradedButRated.isTreatmentLost()).isFalse();
		}
	}

	@Nested
	@DisplayName("an empty xpath with a selection is recoverable, not fatal")
	class EmptyXpathWithSelection {

		@Test
		@DisplayName("the decision function can select a video it cannot point at")
		void selectionSurvivesAnEmptyXpath() throws Exception {
			// When the recommendations sidebar has not rendered, the decision
			// function reads its candidates from the page's ytInitialData and can
			// still choose one -- but there is no element to build an xpath
			// against, so it returns the selection with an empty xpath. The runner
			// navigates by id instead of failing a job whose answer it already has.
			String body = "{\"xpath\": \"\", \"selected\": {\"id\": \"50bTsatwNYs\","
					+ " \"title\": \"Semiconductor tariffs\"},"
					+ " \"degraded\": true, \"treatment_lost\": false}";

			LambdaSelectJobResult parsed = new ObjectMapper().readValue(body, LambdaSelectJobResult.class);

			assertThat(parsed.getXpath()).isNullOrEmpty();
			assertThat(parsed.getSelected()).isNotNull();
			assertThat(parsed.getSelected().getId()).isEqualTo("50bTsatwNYs");
			assertThat(parsed.isDegraded()).isTrue();
		}

		@Test
		@DisplayName("an empty xpath with no selection stays fatal")
		void nothingToRecoverFrom() throws Exception {
			LambdaSelectJobResult parsed = new ObjectMapper()
					.readValue("{\"xpath\": \"\", \"degraded\": true}", LambdaSelectJobResult.class);

			assertThat(parsed.getXpath()).isNullOrEmpty();
			assertThat(parsed.getSelected()).as("nothing to navigate to").isNull();
		}
	}
}
