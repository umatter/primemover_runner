package primemover.service;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.retry.RetryContext;
import org.springframework.retry.annotation.Retryable;
import org.springframework.retry.support.RetrySynchronizationManager;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import primemover.config.StartupMode;
import primemover.model.IJob;
import primemover.model.JobState;
import primemover.model.Joblist;
import primemover.model.JoblistState;
import primemover.runner.DecisionVerdict;

import javax.annotation.Nullable;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class JobService implements IJobService {

	@Value("${global.mode:PRODUCTION}")
	private StartupMode mode;

	@Value("#{'${backend.endpoint.runner}' +'queues'}")
	private String endpointPathQueues;

	@Value("#{'${backend.endpoint.runner}' +'jobs'}")
	private String endpointPathJobs;

	private static Logger logger = LoggerFactory.getLogger(JobService.class);

	/**
	 * Upper bound on the {@code status_message} sent to the API. The Laravel
	 * column is TEXT and its validation rule is {@code nullable} with no length
	 * limit, so this cap is ours: it keeps one runaway exception message from
	 * bloating a row, not a schema constraint.
	 */
	static final int MAX_STATUS_MESSAGE_LENGTH = 1800;

	private static final String TRUNCATION_MARKER = "...";

	public JobService() {
		// nothing to do
	}

	/**
	 * Build the {@code status_message} for a state report.
	 *
	 * <p>
	 * Either the bare state text, or {@code "<state text> - <detail>"} with the
	 * detail collapsed onto a single line and truncated. Callers must pass a
	 * cause, never a stack trace.
	 */
	static String buildStatusMessage(String stateText, @Nullable String detail) {
		if (detail == null) {
			return stateText;
		}

		// single-line: newlines and tabs would break every log/CSV consumer of
		// this field, and a stack trace pasted in here would be unreadable anyway
		String singleLine = detail.replaceAll("\\s+", " ").trim();
		if (singleLine.isEmpty()) {
			return stateText;
		}

		String message = stateText + " - " + singleLine;
		if (message.length() <= MAX_STATUS_MESSAGE_LENGTH) {
			return message;
		}

		return message.substring(0, truncationEnd(message, MAX_STATUS_MESSAGE_LENGTH - TRUNCATION_MARKER.length()))
				+ TRUNCATION_MARKER;
	}

	/**
	 * The largest cut point at or below {@code limit} that does not split a
	 * character.
	 *
	 * <p>
	 * Java indexes {@link String} by UTF-16 code unit, so a non-BMP character --
	 * an emoji, a CJK extension ideograph, a mathematical symbol -- is two chars.
	 * Cutting between them leaves a lone high surrogate, which is not a character
	 * at all: it survives {@code toString()}, but the JSON encoder that puts this
	 * field on the wire has no legal encoding for it, and what the API stores is
	 * {@code ?}. A video title is attacker-supplied text arriving from YouTube via
	 * a failed job's message, so a truncation boundary landing inside an emoji is
	 * a question of when, not whether.
	 *
	 * <p>
	 * The result may therefore be one char shorter than {@code limit}. Everything
	 * upstream deals in "at most", so that is free.
	 */
	private static int truncationEnd(String message, int limit) {
		if (limit <= 0) {
			return 0;
		}
		if (limit >= message.length()) {
			return message.length();
		}

		// only a high surrogate immediately before the cut can have its partner
		// after it; a lone one already in the input is left exactly as it was
		if (Character.isHighSurrogate(message.charAt(limit - 1)) && Character.isLowSurrogate(message.charAt(limit))) {
			return limit - 1;
		}

		return limit;
	}

	/**
	 * Current retry attempt, or 0 when called outside a retry context.
	 * {@link RetrySynchronizationManager#getContext()} returns {@code null} when
	 * the call did not go through the retry proxy (self-invocation, tests), and
	 * dereferencing it there used to be an NPE waiting to happen.
	 */
	private static int currentRetryCount() {
		RetryContext context = RetrySynchronizationManager.getContext();
		return context == null ? 0 : context.getRetryCount();
	}

	private static DateTimeFormatter dateFormater = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

	@Qualifier("ServiceRestTemplate")
	@Autowired
	private RestTemplate restTemplate;

	private String calculateJobListUrl(Long id) {
		String url = endpointPathQueues;

		if (id != null) {
			url = url + '/' + id.toString();
		}

		return url;
	}

	private String calculateJobUrl(Long id) {
		String url = endpointPathJobs;

		if (id != null) {
			url = url + '/' + id.toString();
		}

		return url;
	}

	@Retryable
	@Override
	public Boolean updateJoblistState(Joblist jobs, JoblistState state, ZonedDateTime startTime, ZonedDateTime endTime,
			Boolean processed, Boolean processing) {
		logger.info("updateing joblist-state. Retry {}", currentRetryCount());
		return updateJoblistState(jobs.getId(), state, null, startTime, endTime, processed, processing);
	}

	@Retryable
	@Override
	public Boolean updateJoblistState(Joblist jobs, JoblistState state, @Nullable String detail,
			ZonedDateTime startTime, ZonedDateTime endTime, Boolean processed, Boolean processing) {
		logger.info("updateing joblist-state. Retry {}", currentRetryCount());
		return updateJoblistState(jobs.getId(), state, detail, startTime, endTime, processed, processing);
	}

	private Boolean updateJoblistState(Long queueId, JoblistState state, @Nullable String detail,
			ZonedDateTime startTime, ZonedDateTime endTime, Boolean processed, Boolean processing) {
		String url = calculateJobListUrl(queueId);

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		JSONObject data = new JSONObject();
		data.put("status_code", state.getCode());
		data.put("status_message", buildStatusMessage(state.getText(), detail));
		if (!(startTime == null)) {
			data.put("started_at", dateFormater.format(startTime));
		}
		if (!(endTime == null)) {
			data.put("finished_at", dateFormater.format(endTime));
		}
		if (!(processed == null)) {
			data.put("processed", processed);
		}
		if (!(processing == null)) {
			data.put("processing", processing);
		}

		HttpEntity<String> request = new HttpEntity<String>(data.toString(), headers);

		try {
			ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.PUT, request, String.class);

			if (response.getStatusCode().is2xxSuccessful()) {
				logger.info("updated data for joblist {}", queueId);
				return true;
			} else {
				logger.warn("failure updating joblist {}\nrequest:{}\nresponse:\n{}", queueId, request, response);
				return false;
			}
		} catch (Exception ex) {
			logger.warn("failure updating joblist {} ", queueId, ex);
			return false;
		}
	}

	@Retryable
	@Override
	public Boolean updateCaptchaStateOnJob(final IJob job, Boolean captchaFound, Boolean captchaSolved) {
		logger.info("updateing captcha-state. Retry {}", currentRetryCount());
		return updateJobState(job.getId(), null, null, captchaFound, captchaSolved, null, null, null);
	}

	@Retryable
	@Override
	public Boolean updateJobState(final IJob job, final JobState state, Boolean captchaFound,
			final Boolean captchaSolved, final ZonedDateTime startTime, final ZonedDateTime endTime) {
		logger.info("updateing job-state. Retry {}", currentRetryCount());
		return updateJobState(job.getId(), state, null, captchaFound, captchaSolved, startTime, endTime, null);
	}

	@Retryable
	@Override
	public Boolean updateJobState(final IJob job, final JobState state, @Nullable final String detail,
			final Boolean captchaFound, final Boolean captchaSolved, final ZonedDateTime startTime,
			final ZonedDateTime endTime) {
		logger.info("updateing job-state. Retry {}", currentRetryCount());
		return updateJobState(job.getId(), state, detail, captchaFound, captchaSolved, startTime, endTime, null);
	}

	@Retryable
	@Override
	public Boolean updateJobState(final IJob job, final JobState state, @Nullable final String detail,
			final Boolean captchaFound, final Boolean captchaSolved, final ZonedDateTime startTime,
			final ZonedDateTime endTime, @Nullable final DecisionVerdict decision) {
		logger.info("updateing job-state. Retry {}", currentRetryCount());
		return updateJobState(job.getId(), state, detail, captchaFound, captchaSolved, startTime, endTime, decision);
	}

	private Boolean updateJobState(final Long jobID, final JobState state, @Nullable final String detail,
			final Boolean captchaFound, final Boolean captchaSolved, final ZonedDateTime startTime,
			final ZonedDateTime endTime, @Nullable final DecisionVerdict decision) {
		// do we have something to update?
		if (state == null && captchaFound == null && captchaSolved == null && startTime != null && endTime != null) {
			logger.debug("skipping call nothing to update");
			return true;
		}

		// run update
		String url = calculateJobUrl(jobID);

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		JSONObject data = new JSONObject();

		// state data
		if (state != null) {
			data.put("status_code", state.getCode());
			data.put("status_message", buildStatusMessage(state.getText(), detail));
		}

		// captcha recording and solving
		if (captchaFound != null) {
			data.put("captcha_found", captchaFound);
		}
		if (captchaSolved != null) {
			data.put("captcha_solved", captchaSolved);
		}

		// start and end timing
		if (!(startTime == null)) {
			data.put("started_at", dateFormater.format(startTime));
		}
		if (!(endTime == null)) {
			data.put("finished_at", dateFormater.format(endTime));
		}

		// the decision function's verdict on this job, when there was one
		addDecisionFields(data, decision);

		if (logger.isDebugEnabled()) {
			logger.debug("sending JSON:\n{}", data);
		}

		HttpEntity<String> request = new HttpEntity<String>(data.toString(), headers);

		try {
			ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.PUT, request, String.class);

			if (response.getStatusCode().is2xxSuccessful()) {
				logger.info("updated data for job {}", jobID);
				return true;
			} else {
				logger.warn("failure updating job {}, HTTP-state: {}, message:\n{}", jobID, response.getStatusCode(),
						response.getBody());
				return false;
			}
		} catch (Exception ex) {
			logger.warn("failure updating job {} ", jobID, ex);
			return false;
		}
	}

	/**
	 * Put the decision function's verdict into the job PUT body, or nothing at all.
	 *
	 * <p>
	 * Four keys, named exactly as the API's columns:
	 * {@code decision_degraded}, {@code decision_treatment_lost},
	 * {@code decision_selection_mode} and {@code decision_report}
	 * ({@code {filters, llm, warnings, applied, requested}}).
	 *
	 * <p>
	 * <b>All four are omitted when {@code decision} is {@code null}</b>, and that
	 * is the load-bearing half of this method. {@code null} arrives for a
	 * non-lambda job, for a legacy payload, and for any decision function that
	 * predates degradation reporting -- three different ways of saying "nobody
	 * measured this". A missing key reads as <em>unknown</em> on the API side; a
	 * {@code false} would read as "we checked, and the treatment applied", which
	 * is exactly the false-clean reading that made silent treatment-arm loss
	 * invisible in the first place (docs/integration-contract.md &sect;7.3).
	 *
	 * <p>
	 * {@code decision_selection_mode} is sent as an explicit JSON {@code null}
	 * when the report carried no mode: the report exists, the mode simply was not
	 * in it, and that is a different statement from having no report.
	 * {@code selection_mode} is a descriptor, never a gate -- {@code "random"} is
	 * the correct answer for a control crawler.
	 *
	 * <p>
	 * The report is bounded by {@link DecisionVerdict} before it gets here, so a
	 * pathological payload -- hundreds of warnings, a filter status carrying a
	 * traceback -- cannot inflate this request.
	 */
	private static void addDecisionFields(JSONObject data, @Nullable DecisionVerdict decision) {
		if (decision == null) {
			return;
		}

		data.put("decision_degraded", decision.isDegraded());
		data.put("decision_treatment_lost", decision.isTreatmentLost());

		String mode = decision.getSelectionMode();
		data.put("decision_selection_mode", mode == null ? JSONObject.NULL : mode);

		data.put("decision_report", decision.reportJson());
	}
}
