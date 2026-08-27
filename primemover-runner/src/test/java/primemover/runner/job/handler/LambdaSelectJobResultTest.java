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
			+ "\"filters\": {\"already_watched\": \"ok\", \"mainstream_media\": \"failed: KeyError: 'channel'\","
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
			assertThat(result.appliedFilters()).containsExactly("already_watched");
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
			assertThat(result.filtersSnapshot()).isEmpty();
			assertThat(result.llmErrorType()).isNull();
			assertThat(result.llmError()).isNull();
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
}
