package primemover.service;

import primemover.model.IJob;
import primemover.model.JobState;
import primemover.model.Joblist;
import primemover.model.JoblistState;
import primemover.runner.DecisionVerdict;

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

	/**
	 * Report job state with a diagnostic <em>and</em> the decision function's
	 * verdict attached.
	 *
	 * <p>
	 * The verdict adds four fields to the PUT body --
	 * {@code decision_degraded}, {@code decision_treatment_lost},
	 * {@code decision_selection_mode} and {@code decision_report} -- so that "did
	 * the treatment this bot was assigned actually apply?" becomes a column the
	 * API can be queried on. Before this it survived only as prose inside
	 * {@code status_message} and as an object in MinIO.
	 *
	 * <p>
	 * <b>The status is not affected.</b> This overload adds fields; it never
	 * changes {@code status_code} or {@code status_message}. In particular it does
	 * not reinterpret a payload that carried no degradation report, so deploying a
	 * new jar cannot silently reclassify history.
	 *
	 * @param decision the parsed verdict, or {@code null} when there is none --
	 *                 a non-lambda job, or a decision function whose payload
	 *                 predates the degradation report. {@code null} omits all four
	 *                 keys, which the API reads as <b>unknown</b>. Never pass a
	 *                 default-constructed "clean" verdict to mean "we did not
	 *                 look".
	 */
	Boolean updateJobState(IJob job, JobState state, @Nullable String detail, Boolean captchaFound,
			Boolean captchaSolved, ZonedDateTime startTime, ZonedDateTime endTime,
			@Nullable DecisionVerdict decision);

}
