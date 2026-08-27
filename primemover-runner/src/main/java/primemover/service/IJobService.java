package primemover.service;

import primemover.model.IJob;
import primemover.model.JobState;
import primemover.model.Joblist;
import primemover.model.JoblistState;

import javax.annotation.Nullable;
import java.time.ZonedDateTime;

public interface IJobService {

	Boolean updateJoblistState(Joblist jobs, JoblistState state, ZonedDateTime startTime, ZonedDateTime endTime,
			Boolean processed, Boolean processing);

	/**
	 * Report queue state with a diagnostic attached.
	 *
	 * @param detail single-line cause, appended to the state text as
	 *               {@code "<state text> - <detail>"}. {@code null} or blank
	 *               sends the bare state text, exactly as the overload above.
	 */
	Boolean updateJoblistState(Joblist jobs, JoblistState state, @Nullable String detail, ZonedDateTime startTime,
			ZonedDateTime endTime, Boolean processed, Boolean processing);

	Boolean updateCaptchaStateOnJob(IJob job, Boolean captchaFound, Boolean captchaSolved);

	Boolean updateJobState(IJob job, JobState state, Boolean captchaFound, Boolean captchaSolved,
			ZonedDateTime startTime, ZonedDateTime endTime);

	/**
	 * Report job state with a diagnostic attached.
	 *
	 * <p>
	 * Without this, every failed job in the database reads exactly
	 * {@code "error during execution"} -- the state text and nothing else -- while
	 * the real cause was logged locally and thrown away.
	 *
	 * @param detail single-line cause, appended to the state text as
	 *               {@code "<state text> - <detail>"} and truncated to
	 *               {@link JobService#MAX_STATUS_MESSAGE_LENGTH} characters.
	 *               {@code null} or blank sends the bare state text, exactly as
	 *               the overload above. Never pass a stack trace.
	 */
	Boolean updateJobState(IJob job, JobState state, @Nullable String detail, Boolean captchaFound,
			Boolean captchaSolved, ZonedDateTime startTime, ZonedDateTime endTime);

}
