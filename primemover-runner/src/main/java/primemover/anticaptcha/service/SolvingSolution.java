package primemover.anticaptcha.service;

import java.util.ArrayList;
import java.util.List;

public class SolvingSolution {

	private String gRecaptchaResponse;
	private List<String> cookies = new ArrayList<String>();
	
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

}
