package primemover.service.anticaptcha;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class AntiCaptchaTask {
	
	private String type;
	@JsonProperty("websiteURL")
	private String websiteUrl;
	private String websiteKey;
	private String websiteSToken;
	@JsonIgnore
	private Boolean isInvisible;
		
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
}
