package primemover.multilogin.mlx;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Batching is only worth anything if {@code times} actually reaches the API.
 *
 * <p>
 * The first version of this rewrote the field with a regex, which would have
 * been a silent disaster: {@code config/DefaultMultiLoginProfile.json} has no
 * {@code "times"} field to rewrite, so the substitution would no-op, the API
 * would default to one profile, and a batch of twelve would get a single id and
 * fail the other eleven -- worse than not batching at all.
 *
 * <p>
 * TemplateMapper picks the agent's own creation JSON when it has one and the
 * config default otherwise, so both shapes have to work.
 */
class ProfileTimesTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	/** The agent-supplied shape: already carries "times". */
	private static final String WITH_TIMES = "{\"name\": \"bot\", \"times\": 1, \"browser_type\": \"mimic\"}";

	/** The config default's shape: no "times" anywhere. */
	private static final String WITHOUT_TIMES = "{\"name\": \"bot\", \"browser_type\": \"mimic\"}";

	private static int timesOf(String json) throws Exception {
		JsonNode node = MAPPER.readTree(json).get("times");
		return node == null ? -1 : node.asInt();
	}

	@Test
	@DisplayName("an existing times value is replaced")
	void replacesExisting() throws Exception {
		assertEquals(12, timesOf(ProfileService.withTimes(WITH_TIMES, 12)));
	}

	@Test
	@DisplayName("a missing times field is added -- the case a regex would have missed")
	void addsWhenAbsent() throws Exception {
		assertEquals(12, timesOf(ProfileService.withTimes(WITHOUT_TIMES, 12)));
	}

	@Test
	@DisplayName("the real config template comes out with times set")
	void worksOnTheShippedTemplate() throws Exception {
		Path template = Path.of("config", "DefaultMultiLoginProfile.json");
		// Asserted, not skipped: a silent return here would let this test pass
		// vacuously and hide exactly the defect it exists to catch.
		assertTrue(Files.exists(template), "shipped template not found at " + template.toAbsolutePath());
		String json = Files.readString(template);
		assertEquals(-1, timesOf(json), "precondition: the shipped template has no times field");
		assertEquals(9, timesOf(ProfileService.withTimes(json, 9)));
	}

	@Test
	@DisplayName("everything else in the template survives")
	void preservesOtherFields() throws Exception {
		JsonNode out = MAPPER.readTree(ProfileService.withTimes(WITH_TIMES, 5));
		assertEquals("bot", out.get("name").asText());
		assertEquals("mimic", out.get("browser_type").asText());
	}

	@Test
	@DisplayName("a batch of one is left exactly as it was")
	void singleIsUntouched() {
		assertEquals(WITH_TIMES, ProfileService.withTimes(WITH_TIMES, 1));
	}

	@Test
	@DisplayName("an unparseable template is returned unchanged rather than throwing")
	void unparseableIsPassedThrough() {
		// Failing here would replace a clear API error with a stack trace from
		// the wrong layer.
		assertEquals("not json", ProfileService.withTimes("not json", 4));
	}
}
