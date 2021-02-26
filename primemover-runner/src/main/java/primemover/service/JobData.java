package primemover.service;

import java.util.Set;

import org.openqa.selenium.Cookie;
import org.springframework.util.StringUtils;

import com.browserup.harreader.model.Har;

import primemover.model.LocationInfo;

public class JobData {
	
	private byte[] screenshot;
	private Har har;
	private String pageSource;
	private Set<Cookie> cookies;
	private String sessionStorage;
	private String localStorage;
	private LocationInfo location;
	
	public Har getHar() {
		return har;
	}

	public void setHar(Har har) {
		this.har = har;
	}
	
	public boolean hasHar() {
		return this.har != null;
	}

	public String getPageSource() {
		return pageSource;
	}

	public void setPageSource(String pageSource) {
		this.pageSource = pageSource;
	}
	
	public boolean hasPageSource() {
		return StringUtils.hasText(this.pageSource);
	}

	public Set<Cookie> getCookies() {
		return cookies;
	}

	public void setCookies(Set<Cookie> cookies) {
		this.cookies = cookies;
	}
	
	public boolean hasCookies() {
		return this.cookies != null;
	}

	public String getSessionStorage() {
		return sessionStorage;
	}

	public void setSessionStorage(String sessionStorage) {
		this.sessionStorage = sessionStorage;
	}
	
	public boolean hasSessionStorage() {
		return StringUtils.hasText(this.sessionStorage);
	}

	public String getLocalStorage() {
		return localStorage;
	}

	public void setLocalStorage(String localStorage) {
		this.localStorage = localStorage;
	}
	
	public boolean hasLocalStorage() {
		return StringUtils.hasText(this.localStorage);
	}

	public void setScreenshot(byte[] screenshot) {
		this.screenshot = screenshot;
	}
	
	
	public byte[] getScreenshot() {
		return this.screenshot;
	}
	
	public boolean hasScreenshot() {
		return this.screenshot != null;
	}

	public LocationInfo getLocation() {
		return location;
	}

	public void setLocation(LocationInfo location) {
		this.location = location;
	}
	
	public boolean hasLocationInfo() {
		return this.location != null;
	}
}
