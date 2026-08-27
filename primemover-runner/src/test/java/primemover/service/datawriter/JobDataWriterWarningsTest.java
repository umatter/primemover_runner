package primemover.service.datawriter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import primemover.model.jobs.WaitJob;
import primemover.runner.RecordedJobData;
import primemover.runner.RecordedJobDataBuilder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The job's warnings in the job-data archive.
 *
 * <p>
 * They were write-only until 2026-08-27: {@code TaskOperator} put them on the
 * {@link RecordedJobData} and nothing ever called {@code getWarnings()}, so the
 * claim that they were "carried along with the recorded data" was true of no
 * file on disk. That archive is the experiment's evidence trail, and the
 * warning that matters most -- this session lost its treatment -- is precisely
 * the fact a later analysis needs in order not to read control-arm data as
 * treatment data. The database's status_message is truncated and eventually
 * rotated away; the archive is not.
 */
class JobDataWriterWarningsTest {

	private static RecordedJobData recordings() {
		WaitJob job = new WaitJob();
		job.setId(3828L);
		job.setTask("wait");
		return new RecordedJobDataBuilder(job).build();
	}

	private static Map<String, String> zipEntries(RecordedJobData data) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		assertThat(new JobDataWriter(data).writeData(out)).isTrue();

		Map<String, String> entries = new LinkedHashMap<>();
		try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(out.toByteArray()))) {
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null) {
				entries.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
			}
		}
		return entries;
	}

	@Test
	@DisplayName("the warnings are written into the archive as their own JSON entry")
	void warningsAreSerialisedIntoTheJobData() throws Exception {
		RecordedJobData data = recordings();
		data.addWarnings(List.of(
				"lambda treatment_lost: a requested treatment did not apply, so this job produced control-arm data",
				"lambda filter similar failed: RateLimitError: Rate limit reached"));

		Map<String, String> entries = zipEntries(data);

		String name = entries.keySet().stream().filter(n -> n.contains("_job_warnings_")).findFirst()
				.orElseThrow(() -> new AssertionError("no job_warnings entry in " + entries.keySet()));
		assertThat(name).endsWith("_job_warnings_3828.json");

		List<?> parsed = new ObjectMapper().readValue(entries.get(name), List.class);
		assertThat(parsed).hasSize(2);
		assertThat(String.valueOf(parsed.get(0))).contains("treatment_lost");
		assertThat(String.valueOf(parsed.get(1))).contains("RateLimitError");
	}

	@Test
	@DisplayName("a job with nothing wrong gets no such entry, so its archive is what it always was")
	void aCleanJobArchiveIsUnchanged() throws Exception {
		Map<String, String> entries = zipEntries(recordings());

		assertThat(entries.keySet()).noneSatisfy(n -> assertThat(n).contains("_job_warnings_"));
	}

	@Test
	@DisplayName("a warning containing quotes and newlines survives the round trip")
	void warningsAreProperlyEscaped() throws Exception {
		RecordedJobData data = recordings();
		List<String> awkward = new ArrayList<>();
		awkward.add("lambda: KeyError: \"channel\" \\ not found\nsecond line");
		data.addWarnings(awkward);

		Map<String, String> entries = zipEntries(data);
		String name = entries.keySet().stream().filter(n -> n.contains("_job_warnings_")).findFirst().orElseThrow();

		List<?> parsed = new ObjectMapper().readValue(entries.get(name), List.class);
		assertThat(parsed).hasSize(1);
		assertThat(parsed.get(0)).isEqualTo("lambda: KeyError: \"channel\" \\ not found\nsecond line");
	}
}
