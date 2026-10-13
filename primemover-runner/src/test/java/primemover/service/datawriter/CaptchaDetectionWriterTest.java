package primemover.service.datawriter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import primemover.anticaptcha.CaptchaSolverResult;
import primemover.model.jobs.WaitJob;
import primemover.runner.RecordedJobData;
import primemover.runner.RecordedJobDataBuilder;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The {@code static-captcha} object is the per-job record of what the captcha
 * solver met and did. It used to build that JSON and then write the page source
 * instead, which nobody noticed because on Multilogin X the solver never
 * detected anything and the object stayed empty.
 */
class CaptchaDetectionWriterTest {

	private static final String PAGE = "<html><body>the page the job ended on</body></html>";

	private static RecordedJobDataBuilder recordings() {
		WaitJob job = new WaitJob();
		job.setId(3828L);
		job.setTask("wait");
		return new RecordedJobDataBuilder(job).setPageSource(PAGE);
	}

	@Test
	@DisplayName("the solver's before/after results are written as JSON, not the page source")
	void writesTheSolverResults() throws Exception {
		RecordedJobData data = recordings().setCapchaResultBefore(CaptchaSolverResult._NOCAPTCHADETECTED)
				.setCapchaResultAfter(CaptchaSolverResult.of(true, true, true, true, 21, false, false,
						"bing challenge page (turnstile)"))
				.build();
		CaptchaDetectionWriter writer = new CaptchaDetectionWriter(data);
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		assertThat(writer.hasOutput()).isTrue();
		assertThat(writer.writeData(out)).isTrue();

		String json = out.toString(StandardCharsets.UTF_8);
		assertThat(json).doesNotContain("the page the job ended on");
		JsonNode root = new ObjectMapper().readTree(json);
		assertThat(root.get("job_id").asLong()).isEqualTo(3828L);
		assertThat(root.get("before").get("detected").asBoolean()).isFalse();
		JsonNode after = root.get("after");
		assertThat(after.get("detected").asBoolean()).isTrue();
		assertThat(after.get("solved").asBoolean()).isTrue();
		assertThat(after.get("type").asText()).isEqualTo("bing challenge page (turnstile)");
		assertThat(after.get("solving_seconds").asLong()).isEqualTo(21L);
		assertThat(after.get("error").asBoolean()).isFalse();
	}

	@Test
	@DisplayName("an attempt that did not run is null, not a made-up result")
	void missingAttemptIsNull() throws Exception {
		RecordedJobData data = recordings().setCapchaResultBefore(CaptchaSolverResult.of(true, true, true, false,
				40, false, true, "bing challenge page (turnstile)")).build();
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		assertThat(new CaptchaDetectionWriter(data).writeData(out)).isTrue();

		JsonNode root = new ObjectMapper().readTree(out.toString(StandardCharsets.UTF_8));
		assertThat(root.get("before").get("solved").asBoolean()).isFalse();
		assertThat(root.get("before").get("error").asBoolean()).isTrue();
		assertThat(root.get("after").isNull()).isTrue();
	}

	@Test
	@DisplayName("no solver results at all: nothing to write")
	void noResultsNoOutput() {
		CaptchaDetectionWriter writer = new CaptchaDetectionWriter(recordings().build());

		assertThat(writer.hasOutput()).isFalse();
		assertThat(writer.writeData(new ByteArrayOutputStream())).isFalse();
	}
}
