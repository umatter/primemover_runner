package primemover.runner;

import org.junit.jupiter.api.Test;
import primemover.model.IJob;
import primemover.model.jobs.WaitJob;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecordedJobDataWarningsTest {

	private static RecordedJobData recordings() {
		IJob job = new WaitJob();
		return new RecordedJobDataBuilder(job).build();
	}

	@Test
	void thereAreNoWarningsByDefault() {
		RecordedJobData data = recordings();

		assertFalse(data.hasWarnings());
		assertEquals(List.of(), data.getWarnings());
	}

	@Test
	void warningsAreCarriedAlongWithTheRecordedData() {
		RecordedJobData data = recordings();

		data.addWarnings(List.of("lambda selection_mode=random", "similar filter failed"));

		assertTrue(data.hasWarnings());
		assertEquals(List.of("lambda selection_mode=random", "similar filter failed"), data.getWarnings());
	}

	@Test
	void nullAndEmptyAreIgnored() {
		RecordedJobData data = recordings();

		data.addWarnings(null);
		data.addWarnings(List.of());

		assertFalse(data.hasWarnings());
	}

	@Test
	void theReturnedListIsASnapshot() {
		RecordedJobData data = recordings();
		data.addWarnings(List.of("one"));

		List<String> snapshot = data.getWarnings();
		data.addWarnings(List.of("two"));

		assertEquals(List.of("one"), snapshot);
		assertEquals(List.of("one", "two"), data.getWarnings());
		assertThrows(UnsupportedOperationException.class, () -> snapshot.add("nope"));
	}
}
