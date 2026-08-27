package primemover.runner;

import org.junit.jupiter.api.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The correlation id the runner writes into the MDC has to stay readable by the
 * two things that parse it: promtail's scrape config in the orchestrator repo,
 * and the orchestrator's own parser.
 *
 * <p>
 * The regexes below are the live ones, copied verbatim:
 * <ul>
 * <li>promtail ({@code infrastructure/promtail/promtail-config.yaml}):
 * {@code correlationId=(?P<correlation_id>[^\s,]+)} -- so the id may contain
 * neither whitespace nor a comma, because logback renders the whole MDC map
 * comma-separated. A space in the value would silently truncate it.</li>
 * <li>orchestrator {@code tracing/correlation.py}: {@code cr-(\d+)},
 * {@code (?:q|jl)-(\d+)}, {@code j-(\d+)}.</li>
 * </ul>
 */
class CorrelationIdFormatTest {

	/** exactly promtail's expression for the correlationId value */
	private static final Pattern PROMTAIL_VALUE = Pattern.compile("[^\\s,]+");

	private static final Pattern EXPERIMENT = Pattern.compile("exp-(\\d+)");
	private static final Pattern CRAWLER = Pattern.compile("cr-(\\d+)");
	private static final Pattern QUEUE = Pattern.compile("(?:q|jl)-(\\d+)");
	private static final Pattern JOB = Pattern.compile("j-(\\d+)");

	@Test
	void theQueueLevelIdIsWhatJobListRunnerPutsIntoTheMdc() {
		// JobListRunner.run(): no experiment id, no job id yet
		String id = JobDiagnostics.correlationId(null, 250L, 844L, null);

		assertEquals("cr-250|q-844", id);
		assertTrue(PROMTAIL_VALUE.matcher(id).matches(), "promtail would truncate: " + id);
	}

	@Test
	void theJobLevelIdCarriesEveryIdTheRunnerKnows() {
		// TaskOperator.runJob() extends the queue-level id for one job
		String id = JobDiagnostics.withJob(JobDiagnostics.correlationId(null, 250L, 844L, null), 3828L);

		assertEquals("cr-250|q-844|j-3828", id);
		assertTrue(PROMTAIL_VALUE.matcher(id).matches(), "promtail would truncate: " + id);

		assertEquals("250", firstGroup(CRAWLER, id));
		assertEquals("844", firstGroup(QUEUE, id));
		assertEquals("3828", firstGroup(JOB, id));
	}

	@Test
	void anUnknownSegmentIsOmittedNotFilledWithAPlaceholder() {
		String id = JobDiagnostics.correlationId(null, 250L, 844L, null);

		assertEquals("cr-250|q-844", id);
		assertFalse(EXPERIMENT.matcher(id).find(),
				"the runner does not know the experiment id and must not invent one: " + id);
	}

	@Test
	void theFullCanonicalFormIsSupportedForWhoeverDoesKnowTheExperiment() {
		String id = JobDiagnostics.correlationId(63L, 250L, 844L, 3828L);

		assertEquals("exp-63|cr-250|q-844|j-3828", id);
		assertEquals("63", firstGroup(EXPERIMENT, id));
	}

	@Test
	void middleSegmentsCanBeMissingWithoutLeavingAnEmptySeparator() {
		assertEquals("exp-63|j-3828", JobDiagnostics.correlationId(63L, null, null, 3828L));
		assertEquals("cr-250", JobDiagnostics.correlationId(null, 250L, null, null));
		assertNull(JobDiagnostics.correlationId(null, null, null, null));
	}

	@Test
	void withJobIsANoOpWhenThereIsNoJob() {
		assertEquals("cr-250|q-844", JobDiagnostics.withJob("cr-250|q-844", null));
		assertNull(JobDiagnostics.withJob(null, null));
	}

	@Test
	void withJobStillProducesSomethingUsableWithoutAnOuterId() {
		assertEquals("j-3828", JobDiagnostics.withJob(null, 3828L));
		assertEquals("j-3828", JobDiagnostics.withJob("", 3828L));
	}

	private static String firstGroup(Pattern pattern, String input) {
		Matcher matcher = pattern.matcher(input);
		assertTrue(matcher.find(), pattern + " did not match " + input);
		return matcher.group(1);
	}
}
