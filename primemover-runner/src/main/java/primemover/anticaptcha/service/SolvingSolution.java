package primemover.anticaptcha.service;

import java.util.ArrayList;
import java.util.List;

public class SolvingSolution {

	private String gRecaptchaResponse;
	private List<String> cookies = new ArrayList<String>();
	/** Turnstile: the token the page's callback would have received. */
	private String token;
	/** Turnstile: the worker browser's user agent. */
	private String userAgent;
	
	public SolvingSolution() {
	}

	public String getgRecaptchaResponse() {
		return gRecaptchaResponse;
	}

	public void setgRecaptchaResponse(String gRecaptchaResponse) {
		this.gRecaptchaResponse = gRecaptchaResponse;
	}

	public List<String> getCookies() {
		return cookies;
	}

	public void setCookies(List<String> cookies) {
		this.cookies = cookies;
	}

	public String getToken() {
		return token;
	}

	public void setToken(String token) {
		this.token = token;
	}

	public String getUserAgent() {
		return userAgent;
	}

	public void setUserAgent(String userAgent) {
		this.userAgent = userAgent;
	}

}
