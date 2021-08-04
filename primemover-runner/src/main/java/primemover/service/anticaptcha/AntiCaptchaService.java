package primemover.service.anticaptcha;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

public class AntiCaptchaService {

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
	

	private static Logger logger = LoggerFactory.getLogger(AntiCaptchaService.class);
	
	private ResponseEntity<SolvingResponse> pollForResult(Integer taskId) {
		long start = System.currentTimeMillis();
		
		// initial wait
		try {
			Thread.sleep(7000); // take into account the initial sleep in the loop
		} catch (InterruptedException e) {
			// noting to do
		}
		
		// check and retry
		
		long current = System.currentTimeMillis();
		
		while ((current - start) < (maxSolvingTimeoutSeconds * 1000)) {
			try {
				Thread.sleep(3000);
			} catch (InterruptedException e) {
				// noting to do
			}
			
			ResponseEntity<SolvingResponse> response = getTaskResult(taskId);
			
			if (response.getBody().getStatus() == TaskStatus.ready) {
				logger.debug("received captcha result {}", response);
				return response;
			}
			
			if (response.getBody().hasError()) {
				logger.warn("Error solving captcha (taskId {}", taskId);
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

}
