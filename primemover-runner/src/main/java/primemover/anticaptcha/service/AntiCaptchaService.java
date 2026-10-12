package primemover.anticaptcha.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import primemover.anticaptcha.TurnstileTokenSource;

import java.util.Optional;

public class AntiCaptchaService implements TurnstileTokenSource {

	public AntiCaptchaService() {
	}
	
	@Value("${anticaptcha.api.key}")
	private String apiKey;
	
	@Value("${anticaptcha.api.endpoint:https://api.anti-captcha.com}")
	private String endpoint;
	
	@Value("${anticaptcha.api.solvingTimeoutSeconds:120}")
	private Integer maxSolvingTimeoutSeconds = 120;
	
	@Qualifier("AntiCaptchaRestTemplate")
	@Autowired
	private RestTemplate restTemplate;

	/** Anti-Captcha asks clients not to poll before ~5 s; tests set these to 0. */
	private long initialPollDelayMillis = 7000;
	private long pollIntervalMillis = 3000;

	private static Logger logger = LoggerFactory.getLogger(AntiCaptchaService.class);

	private ResponseEntity<SolvingResponse> pollForResult(Integer taskId) {
		long start = System.currentTimeMillis();

		// initial wait
		try {
			Thread.sleep(initialPollDelayMillis); // take into account the initial sleep in the loop
		} catch (InterruptedException e) {
			// the session is being stopped: stop paying attention to the task
			Thread.currentThread().interrupt();
			return null;
		}

		// check and retry

		long current = System.currentTimeMillis();

		while ((current - start) < (maxSolvingTimeoutSeconds * 1000)) {
			try {
				Thread.sleep(pollIntervalMillis);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return null;
			}

			ResponseEntity<SolvingResponse> response = getTaskResult(taskId);

			if (response.getBody().getStatus() == TaskStatus.ready) {
				logger.debug("received captcha result {}", response);
				return response;
			}

			if (response.getBody().hasError()) {
				logger.warn("Error solving captcha (taskId {}): {} {}", taskId, response.getBody().getErrorCode(),
						response.getBody().getErrorDescription());
				return null;
			}
			
			logger.debug("retry");
			current = System.currentTimeMillis();
		}
		
		logger.warn("Solving timed out (taskId {}", taskId);
		return null;
	}
	
	private ResponseEntity<SolvingResponse> getTaskResult(Integer taskId) {
		String curEndpoint = endpoint + "/getTaskResult";
		
		SolvingRequest request = new SolvingRequest();
		request.setClientKey(this.apiKey);
		request.setTaskId(taskId);
		
		return restTemplate.postForEntity(curEndpoint, request, SolvingResponse.class);
	}
	
	public ResponseEntity<SolvingResponse> solveRecaptchaV2TaskProxyless(String websiteUrl, String websiteKey, String dataSValue, Boolean isVisible) {
		
		String curEndpoint = endpoint + "/createTask";
		
		AntiCaptchaTask task = new AntiCaptchaTask();
		task.setType("RecaptchaV2TaskProxyless");
		task.setIsInvisible(isVisible);
		task.setWebsiteSToken(dataSValue);
		task.setWebsiteKey(websiteKey);
		task.setWebsiteUrl(websiteUrl);
		
		CreateTaskRequest request = new CreateTaskRequest();
		request.setClientKey(apiKey);
		request.setTask(task);
		ResponseEntity<CreateTaskResponse> response;
		
		logger.debug("RecaptchaV2TaskProxyless {}", request);
		response = restTemplate.postForEntity(curEndpoint, request, CreateTaskResponse.class);
		logger.debug("received response {}", response);
		
		if (response.getBody().hasError()) {
			throw new IllegalStateException("task was not created");
		}
		
		return pollForResult(response.getBody().getTaskId());
	}

	/**
	 * A Cloudflare Turnstile token for this widget, solved by Anti-Captcha's
	 * workers from their own address (no proxy).
	 *
	 * <p>
	 * {@code action} and {@code cData} must be the values the page rendered the
	 * widget with: Cloudflare binds them into the token, and the site's verify step
	 * can check them. Every failure -- a createTask error such as a zero balance, an
	 * unsolvable task, a timeout, an unreachable service -- is logged and returned
	 * as empty, never thrown: a challenge we could not pay our way through is a
	 * degraded job, not a crashed one. The API key is never logged.
	 */
	@Override
	public Optional<String> solveTurnstileProxyless(String websiteUrl, String websiteKey, String action,
			String cData) {
		AntiCaptchaTask task = new AntiCaptchaTask();
		task.setType("TurnstileTaskProxyless");
		task.setWebsiteUrl(websiteUrl);
		task.setWebsiteKey(websiteKey);
		task.setAction(action);
		task.setCData(cData);

		CreateTaskRequest request = new CreateTaskRequest();
		request.setClientKey(apiKey);
		request.setTask(task);

		try {
			CreateTaskResponse created = restTemplate
					.postForEntity(endpoint + "/createTask", request, CreateTaskResponse.class).getBody();
			if (created == null || created.hasError() || created.getTaskId() == null) {
				logger.warn("Anti-Captcha refused the Turnstile task: {} {}",
						created == null ? "no body" : created.getErrorCode(),
						created == null ? "" : created.getErrorDescription());
				return Optional.empty();
			}

			logger.debug("Turnstile task {} created for site key {}", created.getTaskId(), websiteKey);
			ResponseEntity<SolvingResponse> result = pollForResult(created.getTaskId());
			SolvingResponse body = result == null ? null : result.getBody();
			String token = body == null || body.getSolution() == null ? null : body.getSolution().getToken();
			if (token == null || token.isBlank()) {
				return Optional.empty();
			}

			logger.info("Turnstile task {} solved (cost {})", created.getTaskId(), body.getCost());
			return Optional.of(token);
		} catch (RestClientException e) {
			logger.warn("Anti-Captcha unreachable or failing: {}", e.getMessage());
			return Optional.empty();
		}
	}

}
