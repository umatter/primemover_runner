package primemover.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import primemover.runner.job.handler.LambdaSelectJobResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The decision verdict as it is prepared for the job row.
 *
 * <p>
 * Two things are under test and they pull in opposite directions. The verdict
 * must carry enough of the decision function's report to answer "which part of
 * the treatment did not apply" without going back to MinIO -- and it must be
 * bounded, because the report is built from free text the decision function
 * produces in a loop and travels in an HTTP body no human ever sees before it is
 * sent.
 *
 * <p>
 * The third thing, and the one that matters most, is what is <em>absent</em>:
 * a payload with no degradation report yields no verdict at all, so the API is
 * told nothing rather than being told a {@code false} nobody measured.
 */
class DecisionVerdictTest {

	private static LambdaSelectJobResult parse(String json) throws Exception {
		return new ObjectMapper().readValue(json, LambdaSelectJobResult.class);
	}

	@Test
	@DisplayName("a degraded payload becomes a verdict naming what failed and what was requested")
	void degradedPayloadIsMappedInFull() throws Exception {
		DecisionVerdict verdict = DecisionVerdict.of(parse("{\"xpath\": \"//a\", \"degraded\": true,"
				+ " \"treatment_lost\": false, \"selection_mode\": \"llm\","
				+ " \"filters\": {\"already_watched\": \"ok\","
				+ " \"similar\": \"failed: RateLimitError: Rate limit reached\","
				+ " \"political\": \"skipped\"},"
				+ " \"llm\": {\"available\": false, \"error_type\": \"rate_limit\", \"error\": \"Rate limit\"},"
				+ " \"warnings\": [\"similar filter skipped\"]}"));

		assertThat(verdict).isNotNull();
		assertThat(verdict.isDegraded()).isTrue();
		assertThat(verdict.isTreatmentLost()).isFalse();
		assertThat(verdict.getSelectionMode()).isEqualTo("llm");
		assertThat(verdict.applied()).containsExactly("already_watched");
		// "skipped" means NOT REQUESTED: political was never asked for, so it is
		// neither applied nor requested, and above all not a casualty
		assertThat(verdict.requested()).containsExactlyInAnyOrder("already_watched", "similar");
		assertThat(verdict.requested()).doesNotContain("political");
		assertThat(verdict.filters()).containsEntry("political", "skipped");
		assertThat(verdict.llm()).containsEntry("available", false).containsEntry("error_type", "rate_limit");
		assertThat(verdict.warnings()).containsExactly("similar filter skipped");
	}

	@Test
	@DisplayName("treatment_lost survives into the verdict as the alarm it is")
	void treatmentLostIsCarried() throws Exception {
		DecisionVerdict verdict = DecisionVerdict.of(parse("{\"degraded\": true, \"treatment_lost\": true,"
				+ " \"selection_mode\": \"random\", \"filters\": {\"similar\": \"failed: X: y\"}}"));

		assertThat(verdict).isNotNull();
		assertThat(verdict.isTreatmentLost()).isTrue();
		assertThat(verdict.isDegraded()).isTrue();
		assertThat(verdict.severity()).isEqualTo(2);
	}

	@Test
	@DisplayName("a payload with no degradation report yields NO verdict, not a clean one")
	void legacyPayloadYieldsNoVerdict() throws Exception {
		// The decision function deployed today emits exactly this shape, and there
		// are thousands of them in the archive. "We cannot tell" and "nothing went
		// wrong" are different answers; a verdict object can only express the second.
		assertThat(DecisionVerdict.of(parse("{\"xpath\": \"//a\", \"selected\": {\"id\": \"x\"},"
				+ " \"warnings\": [\"UserWarning: Aversion to mainstream media channels ignored\"]}"))).isNull();
		assertThat(DecisionVerdict.of(null)).isNull();
	}

	@Test
	@DisplayName("a healthy control-arm decision is a verdict, and it says nothing is wrong")
	void controlArmDecisionIsRecordedAsClean() throws Exception {
		// 236 of the 243 bot configurations on this box are control-arm, and they
		// draw at random on purpose. The verdict must be recorded (that is a
		// measurement) and must not read as a problem.
		DecisionVerdict verdict = DecisionVerdict.of(parse("{\"xpath\": \"//a\", \"degraded\": false,"
				+ " \"treatment_lost\": false, \"selection_mode\": \"random\","
				+ " \"filters\": {\"already_watched\": \"skipped\", \"mainstream_media\": \"skipped\","
				+ " \"children_entertainment\": \"skipped\", \"similar\": \"skipped\","
				+ " \"political\": \"skipped\", \"fringe\": \"skipped\"}}"));

		assertThat(verdict).isNotNull();
		assertThat(verdict.isDegraded()).isFalse();
		assertThat(verdict.isTreatmentLost()).isFalse();
		assertThat(verdict.severity()).isZero();
		assertThat(verdict.getSelectionMode()).isEqualTo("random");
		assertThat(verdict.requested()).isEmpty();
		assertThat(verdict.applied()).isEmpty();
	}

	@Test
	@DisplayName("a missing llm block is not recorded as 'the llm was available'")
	void absentLlmBlockIsNotAsserted() throws Exception {
		DecisionVerdict verdict = DecisionVerdict
				.of(parse("{\"degraded\": false, \"selection_mode\": \"random\"}"));

		assertThat(verdict).isNotNull();
		assertThat(verdict.llm()).isEmpty();
		assertThat(verdict.reportJson().getJSONObject("llm").length()).isZero();
	}

	@Test
	@DisplayName("the report carries exactly the five contracted keys")
	void reportShapeIsTheContractedOne() throws Exception {
		JSONObject report = DecisionVerdict.of(parse("{\"degraded\": true, \"treatment_lost\": false,"
				+ " \"selection_mode\": \"llm\", \"filters\": {\"similar\": \"ok\"}}")).reportJson();

		assertThat(keysOf(report)).containsExactlyInAnyOrder("filters", "llm", "warnings", "applied", "requested");
	}

	@Test
	@DisplayName("a pathological payload is bounded: hundreds of warnings, filters carrying tracebacks")
	void pathologicalPayloadIsBounded() {
		List<String> warnings = new ArrayList<>();
		for (int i = 0; i < 500; i++) {
			warnings.add("UserWarning (line " + i + "): something\n   went badly wrong " + "x".repeat(4000));
		}

		Map<String, String> filters = new LinkedHashMap<>();
		for (int i = 0; i < 60; i++) {
			filters.put("filter_" + i + "_" + "n".repeat(200), "failed: SomeError: " + "y".repeat(5000));
		}

		LambdaSelectJobResult parsed = new LambdaSelectJobResult();
		parsed.setDegraded(Boolean.TRUE);
		parsed.setTreatmentLost(Boolean.TRUE);
		parsed.setSelectionMode("random" + "z".repeat(500));
		parsed.setWarnings(warnings);
		parsed.setFilters(filters);

		DecisionVerdict verdict = DecisionVerdict.of(parsed);
		assertThat(verdict).isNotNull();

		// the verdict itself is still true; only the evidence is abridged
		assertThat(verdict.isTreatmentLost()).isTrue();
		assertThat(verdict.getSelectionMode()).hasSizeLessThanOrEqualTo(DecisionVerdict.MAX_SELECTION_MODE_CHARS);

		// warnings: four kept, plus one line saying where the rest are
		assertThat(verdict.warnings()).hasSize(DecisionVerdict.MAX_REPORT_WARNINGS + 1);
		assertThat(verdict.warnings().get(DecisionVerdict.MAX_REPORT_WARNINGS))
				.contains("more warnings, see the recorded lambda payload");
		assertThat(verdict.warnings()).allSatisfy(w -> {
			assertThat(w).doesNotContain("\n");
			assertThat(w).hasSizeLessThanOrEqualTo(DecisionVerdict.MAX_WARNING_CHARS + 60);
		});

		assertThat(verdict.filters()).hasSizeLessThanOrEqualTo(DecisionVerdict.MAX_FILTERS);
		assertThat(verdict.filters().values())
				.allSatisfy(v -> assertThat(v).hasSizeLessThanOrEqualTo(DecisionVerdict.MAX_FILTER_STATUS_CHARS));
		assertThat(verdict.requested()).hasSizeLessThanOrEqualTo(DecisionVerdict.MAX_FILTER_NAMES_LISTED);

		String serialized = verdict.reportJson().toString();
		assertThat(serialized.length()).isLessThanOrEqualTo(DecisionVerdict.MAX_REPORT_JSON_CHARS);
	}

	@Test
	@DisplayName("the whole-object guard drops the least important evidence first, keeping filters")
	void wholeObjectGuardKeepsTheFiltersLast() {
		// Drive the backstop directly: enough bounded-but-large content to cross the
		// cap even after the per-element caps have done their work.
		Map<String, String> filters = new LinkedHashMap<>();
		for (int i = 0; i < DecisionVerdict.MAX_FILTERS; i++) {
			filters.put(("filter_" + i + "_").repeat(20), "failed: SomeError: " + "y".repeat(5000));
		}
		List<String> warnings = new ArrayList<>();
		for (int i = 0; i < 50; i++) {
			warnings.add("w".repeat(4000));
		}

		LambdaSelectJobResult parsed = new LambdaSelectJobResult();
		parsed.setDegraded(Boolean.TRUE);
		parsed.setFilters(filters);
		parsed.setWarnings(warnings);

		DecisionVerdict verdict = DecisionVerdict.of(parsed);
		assertThat(verdict).isNotNull();

		JSONObject report = verdict.reportJson();
		assertThat(report.toString().length()).isLessThanOrEqualTo(DecisionVerdict.MAX_REPORT_JSON_CHARS);
		// whatever else had to go, "which filter failed" is the last thing dropped
		assertThat(report.has("filters")).isTrue();
	}

	@Test
	@DisplayName("the report is a fresh object each call, so a caller cannot mutate the verdict")
	void reportJsonIsDefensive() throws Exception {
		DecisionVerdict verdict = DecisionVerdict
				.of(parse("{\"degraded\": true, \"filters\": {\"similar\": \"ok\"}}"));

		JSONObject first = verdict.reportJson();
		first.put("filters", new JSONObject());
		first.put("warnings", new JSONArray(List.of("injected")));

		JSONObject second = verdict.reportJson();
		assertThat(second.getJSONObject("filters").getString("similar")).isEqualTo("ok");
		assertThat(second.getJSONArray("warnings").length()).isZero();
	}

	/**
	 * The object's keys. {@code JSONObject.keySet()} does not exist in the org.json
	 * version this build resolves, so iterate the key iterator that does.
	 */
	private static List<String> keysOf(JSONObject object) {
		List<String> keys = new ArrayList<>();
		for (java.util.Iterator<String> it = object.keys(); it.hasNext();) {
			keys.add(it.next());
		}
		return keys;
	}
}
