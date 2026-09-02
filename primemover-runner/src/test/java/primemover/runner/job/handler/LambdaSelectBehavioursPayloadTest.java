package primemover.runner.job.handler;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import primemover.model.jobs.LambdaLinkType;
import primemover.model.jobs.LambdaSelectJob;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The lambda_select job's behaviours must reach the decision function in the
 * payload as {@code job.behaviors}. The runner previously dropped every behaviour
 * not mapped to a typed field, so a {@code strategy} marker and any treatment
 * config never left the runner. This pins the wire contract the decision
 * function's read_treatment() depends on.
 */
class LambdaSelectBehavioursPayloadTest {

	/** The mapper configured exactly as {@link LambdaSelectJobHandler} configures its own. */
	private static ObjectMapper handlerMapper() {
		ObjectMapper mapper = JsonMapper.builder()
				.enable(JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS)
				.build();
		mapper.setVisibility(PropertyAccessor.FIELD, Visibility.ANY);
		return mapper;
	}

	@Test
	@DisplayName("behaviours are serialised into the payload as job.behaviors, marker and all")
	void behavioursReachThePayload() throws Exception {
		LambdaSelectJob job = new LambdaSelectJob();
		job.setLinkType(LambdaLinkType.FUNCTION_NAME);
		job.setLinkValue("bot-decision-yt");
		Map<String, String> behaviours = new HashMap<>();
		behaviours.put("task", "youtube_video_select");
		behaviours.put("linkType", "FUNCTION_NAME");
		behaviours.put("strategy", "youtube_v1");
		behaviours.put("pi", "1");
		job.setBehaviors(behaviours);

		LambdaSelectJobParam param = new LambdaSelectJobParam(job, null, "<html></html>", null);
		String json = handlerMapper().writeValueAsString(param);

		JsonNode behaviors = handlerMapper().readTree(json).get("job").get("behaviors");
		assertThat(behaviors).isNotNull();
		assertThat(behaviors.get("strategy").asText()).isEqualTo("youtube_v1");
		assertThat(behaviors.get("pi").asText()).isEqualTo("1");
		assertThat(behaviors.get("task").asText()).isEqualTo("youtube_video_select");
	}

	@Test
	@DisplayName("a job without behaviours serialises them as null, which the decision function tolerates")
	void nullBehavioursAreTolerated() throws Exception {
		LambdaSelectJob job = new LambdaSelectJob();
		job.setLinkType(LambdaLinkType.FUNCTION_NAME);
		job.setLinkValue("bot-decision-yt");

		LambdaSelectJobParam param = new LambdaSelectJobParam(job, null, "src", null);
		String json = handlerMapper().writeValueAsString(param);

		JsonNode behaviors = handlerMapper().readTree(json).get("job").get("behaviors");
		assertThat(behaviors == null || behaviors.isNull()).isTrue();
	}
}
