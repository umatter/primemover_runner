package primemover.service.anticaptcha;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class CreateTaskRequest {
	private String clientKey;
	@JsonIgnore
	private Integer softId = null; // not used
	@JsonIgnore
	private String languagePool = "en";
	@JsonIgnore
	private String callbackUrl = ""; // not used
	
	private AntiCaptchaTask task;
	
	public CreateTaskRequest() {
	}

	public String getClientKey() {
		return clientKey;
	}

	public void setClientKey(String clientKey) {
		this.clientKey = clientKey;
	}

	public Integer getSoftId() {
		return softId;
	}

	public void setSoftId(Integer softId) {
		this.softId = softId;
	}

	public String getLanguagePool() {
		return languagePool;
	}

	public void setLanguagePool(String languagePool) {
		this.languagePool = languagePool;
	}

	public String getCallbackUrl() {
		return callbackUrl;
	}

	public void setCallbackUrl(String callbackUrl) {
		this.callbackUrl = callbackUrl;
	}

	public AntiCaptchaTask getTask() {
		return task;
	}

	public void setTask(AntiCaptchaTask task) {
		this.task = task;
	}

}
