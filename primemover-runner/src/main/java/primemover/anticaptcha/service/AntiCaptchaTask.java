package primemover.anticaptcha.service;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One createTask {@code task}. Fields a task type does not use stay null and are
 * left out of the JSON, so a Turnstile task carries no reCAPTCHA fields.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AntiCaptchaTask {
	
	private String type;
	@JsonProperty("websiteURL")
	private String websiteUrl;
	private String websiteKey;
	private String websiteSToken;
	@JsonIgnore
	private Boolean isInvisible;
	/** Turnstile: the widget's {@code action}. */
	private String action;
	/** Turnstile: the widget's {@code cData}. */
	@JsonProperty("cData")
	private String cData;
	/** Proxy tasks ({@code TurnstileTask}): the proxy the worker solves through. */
	private String proxyType;
	private String proxyAddress;
	private Integer proxyPort;
	private String proxyLogin;
	private String proxyPassword;
		
	public AntiCaptchaTask() {
	}

	public String getWebsiteUrl() {
		return websiteUrl;
	}

	public void setWebsiteUrl(String websiteUrl) {
		this.websiteUrl = websiteUrl;
	}

	public String getWebsiteKey() {
		return websiteKey;
	}

	public void setWebsiteKey(String websiteKey) {
		this.websiteKey = websiteKey;
	}

	public String getWebsiteSToken() {
		return websiteSToken;
	}

	public void setWebsiteSToken(String value) {
		this.websiteSToken = value;
	}

	public Boolean getIsInvisible() {
		return isInvisible;
	}

	public void setIsInvisible(Boolean isInvisible) {
		this.isInvisible = isInvisible;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getAction() {
		return action;
	}

	public void setAction(String action) {
		this.action = action;
	}

	@JsonProperty("cData")
	public String getCData() {
		return cData;
	}

	public void setCData(String cData) {
		this.cData = cData;
	}

	/** Fill the five proxy fields from one resolved proxy. */
	public void setProxy(TaskProxy proxy) {
		this.proxyType = proxy.getType();
		this.proxyAddress = proxy.getAddress();
		this.proxyPort = proxy.getPort();
		this.proxyLogin = proxy.getLogin();
		this.proxyPassword = proxy.getPassword();
	}

	public String getProxyType() {
		return proxyType;
	}

	public String getProxyAddress() {
		return proxyAddress;
	}

	public Integer getProxyPort() {
		return proxyPort;
	}

	public String getProxyLogin() {
		return proxyLogin;
	}

	public String getProxyPassword() {
		return proxyPassword;
	}
}
