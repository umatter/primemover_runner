package primemover.service;

import java.util.Set;

import org.openqa.selenium.Cookie;

import net.lightbody.bmp.core.har.Har;

public class JobData {
	
	private byte[] screenshot;
	private Har har;
	private String pageSource;
	private Set<Cookie> cookies;
	private String sessionStorage;
	private String localStorage;

	public Har getHar() {
		return har;
	}

	public void setHar(Har har) {
		this.har = har;
	}

	public String getPageSource() {
		return pageSource;
	}

	public void setPageSource(String pageSource) {
		this.pageSource = pageSource;
	}

	public Set<Cookie> getCookies() {
		return cookies;
	}

	public void setCookies(Set<Cookie> cookies) {
		this.cookies = cookies;
	}

	public String getSessionStorage() {
		return sessionStorage;
	}

	public void setSessionStorage(String sessionStorage) {
		this.sessionStorage = sessionStorage;
	}

	public String getLocalStorage() {
		return localStorage;
	}

	public void setLocalStorage(String localStorage) {
		this.localStorage = localStorage;
	}

	public void setScreenshot(byte[] screenshot) {
		this.screenshot = screenshot;
	}
	
	
	public byte[] getScreenshot() {
		return this.screenshot;
	}
}
