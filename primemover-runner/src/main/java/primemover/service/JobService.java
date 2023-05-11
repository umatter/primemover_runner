package primemover.service;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import primemover.config.StartupMode;
import primemover.model.IJob;
import primemover.model.JobState;
import primemover.model.Joblist;
import primemover.model.JoblistState;

@Service
public class JobService {

	@Value("${global.mode:PRODUCTION}")
	private StartupMode mode;

	@Value("#{'${backend.endpoint}' +'queues'}")
	private String endpointPathQueues;

	@Value("#{'${backend.endpoint}' +'jobs'}")
	private String endpointPathJobs;

	private static Logger logger = LoggerFactory.getLogger(JobService.class);

	public JobService() {
		// nothing to do
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

	public Boolean updateJoblistState(Joblist jobs, JoblistState state, ZonedDateTime startTime, ZonedDateTime endTime,
			Boolean processed, Boolean processing) {
		return updateJoblistState(jobs.getId(), state, startTime, endTime, processed, processing);
	}

	public Boolean updateJoblistState(Long queueId, JoblistState state, ZonedDateTime startTime, ZonedDateTime endTime,
			Boolean processed, Boolean processing) {
		String url = calculateJobListUrl(queueId);

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		JSONObject data = new JSONObject();
		data.put("status_code", state.getCode());
		data.put("status_message", state.getText());
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

	public Boolean updateCaptchaStateOnJob(final IJob job, Boolean captchaFound, Boolean captchaSolved) {
		return updateJobState(job.getId(), null, captchaFound, captchaSolved, null, null);
	}

	public Boolean updateJobState(final IJob job, final JobState state, Boolean captchaFound,
			final Boolean captchaSolved, final ZonedDateTime startTime, final ZonedDateTime endTime) {
		return updateJobState(job.getId(), state, captchaFound, captchaSolved, startTime, endTime);
	}

	public Boolean updateJobState(final Long jobID, final JobState state, final Boolean captchaFound,
			final Boolean captchaSolved, final ZonedDateTime startTime, final ZonedDateTime endTime) {
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
			data.put("status_message", state.getText());
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
}
