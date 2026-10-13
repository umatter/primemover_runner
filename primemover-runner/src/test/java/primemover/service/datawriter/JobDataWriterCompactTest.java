package primemover.service.datawriter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import primemover.capture.SessionStorageRef;
import primemover.model.jobs.WaitJob;
import primemover.runner.RecordedJobData;
import primemover.runner.RecordedJobDataBuilder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The job-data archive in the default form, which must not change, and in the
 * two shapes the compact capture adds: a {@code .jpg} screenshot and a session
 * storage pointer.
 */
class JobDataWriterCompactTest {

	private static final byte[] IMAGE = { (byte) 0xFF, (byte) 0xD8, 1, 2, 3 };
	private static final String STORAGE = "{\"SerpCache\":\"blob\"}";

	private static RecordedJobDataBuilder recordings() {
		WaitJob job = new WaitJob();
		job.setId(717527L);
		job.setTask("wait");
		return new RecordedJobDataBuilder(job).setPageSource("<html>serp</html>").setSessionStorage(STORAGE);
	}

	/** Entry name without its date prefix -> content. */
	private static Map<String, String> entries(RecordedJobData data) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		assertThat(new JobDataWriter(data).writeData(out)).isTrue();

		Map<String, String> entries = new LinkedHashMap<>();
		try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(out.toByteArray()))) {
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null) {
				entries.put(entry.getName().substring(entry.getName().indexOf("_page_") + 1),
						new String(zip.readAllBytes(), StandardCharsets.ISO_8859_1));
			}
		}
		return entries;
	}

	@Test
	@DisplayName("default: a .png screenshot and the session storage in full, as always")
	void defaultIsUnchanged() throws Exception {
		Map<String, String> entries = entries(recordings().setScreenshot(IMAGE).build());

		assertThat(entries.keySet()).containsExactly("page_finalsource_717527.html",
				"page_sessionstorage_717527.json", "page_screenshot_717527.png");
		assertThat(entries.get("page_sessionstorage_717527.json")).isEqualTo(STORAGE);
	}

	@Test
	@DisplayName("a JPEG screenshot is stored as .jpg")
	void jpegScreenshot() throws Exception {
		Map<String, String> entries = entries(recordings().setScreenshot(IMAGE, "jpg").build());

		assertThat(entries).containsKey("page_screenshot_717527.jpg").doesNotContainKey("page_screenshot_717527.png");
	}

	@Test
	@DisplayName("an unchanged session storage is a pointer entry instead of the content")
	void sessionStoragePointer() throws Exception {
		SessionStorageRef ref = new SessionStorageRef(717526L, "static-jobdata-visitjob-NULL-717526",
				"ab".repeat(32));

		Map<String, String> entries = entries(recordings().setSessionStorageRef(ref).build());

		assertThat(entries).doesNotContainKey("page_sessionstorage_717527.json");
		JsonNode pointer = new ObjectMapper().readTree(entries.get("page_sessionstorage_ref_717527.json"));
		assertThat(pointer.get("same_as_job_id").asLong()).isEqualTo(717526L);
		assertThat(pointer.get("same_as_object").asText()).isEqualTo("static-jobdata-visitjob-NULL-717526");
	}
}
