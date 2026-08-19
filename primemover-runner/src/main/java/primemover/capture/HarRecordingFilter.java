package primemover.capture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import primemover.model.IJob;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Filter that determines whether HAR (HTTP Archive) recording should be enabled
 * for a specific job type. This allows selective HAR recording to reduce storage
 * overhead and improve performance for jobs that don't generate network traffic.
 *
 * By default, excludes job types that don't generate network activity:
 * - WaitJob: Just waits, no network activity
 * - ScrollJobs: Client-side scrolling only
 * - HandleAlertJob: Browser dialog handling
 * - Assert*Jobs: Test assertions, no network activity
 *
 * @author PrimeMover Team
 */
@Component
public class HarRecordingFilter {
	private static final Logger LOGGER = LoggerFactory.getLogger(HarRecordingFilter.class);

	@Value("${har.recording.enabled:true}")
	private Boolean harRecordingEnabled;

	@Value("${har.recording.excludeJobTypes:WaitJob,ScrollByDurationJob,ScrollByLengthJob,HandleAlertJob,AssertTextInFieldJob,AssertElementExistsJob,AssertScrolledMaxJob,AssertPageLoadedJob,AssertProxyJob,AssertProxyByOxylabsJob}")
	private String excludedJobTypes;

	private Set<String> excludedSet;

	@PostConstruct
	public void init() {
		excludedSet = parseJobTypes(excludedJobTypes);
		LOGGER.info("HAR recording filter initialized. Enabled: {}, Excluded job types: {}",
				harRecordingEnabled, excludedSet);
	}

	/**
	 * Parses a comma-separated list of job type names into a Set.
	 *
	 * @param jobTypes Comma-separated job type class names
	 * @return Set of trimmed job type names
	 */
	private Set<String> parseJobTypes(String jobTypes) {
		if (jobTypes == null || jobTypes.trim().isEmpty()) {
			return Collections.emptySet();
		}
		return Arrays.stream(jobTypes.split(","))
				.map(String::trim)
				.filter(s -> !s.isEmpty())
				.collect(Collectors.toSet());
	}

	/**
	 * Determines whether HAR recording should be enabled for the given job.
	 *
	 * @param job The job to check
	 * @return true if HAR should be recorded, false otherwise
	 */
	public boolean shouldRecordHar(IJob job) {
		if (!harRecordingEnabled) {
			LOGGER.debug("HAR recording globally disabled");
			return false;
		}

		if (job == null) {
			LOGGER.warn("Cannot determine HAR recording for null job");
			return false;
		}

		String jobClassName = job.getClass().getSimpleName();
		boolean shouldRecord = !excludedSet.contains(jobClassName);

		if (!shouldRecord) {
			LOGGER.debug("HAR recording skipped for job type: {} (job id: {})",
					jobClassName, job.getId());
		} else {
			LOGGER.debug("HAR recording enabled for job type: {} (job id: {})",
					jobClassName, job.getId());
		}

		return shouldRecord;
	}

	/**
	 * Gets the set of excluded job types (for testing/debugging).
	 *
	 * @return Unmodifiable set of excluded job type names
	 */
	public Set<String> getExcludedJobTypes() {
		return Collections.unmodifiableSet(excludedSet);
	}

	/**
	 * Checks if HAR recording is globally enabled.
	 *
	 * @return true if enabled globally
	 */
	public boolean isHarRecordingEnabled() {
		return harRecordingEnabled;
	}
}
