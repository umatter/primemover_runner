package primemover.runner;

import java.util.Collections;
import java.util.Set;

import javax.annotation.Nonnull;

import org.openqa.selenium.Cookie;

import com.browserup.harreader.model.Har;
import com.google.common.base.Objects;

import primemover.anticaptcha.CaptchaSolverResult;
import primemover.model.IJob;
import primemover.model.LocationInfo;
import primemover.runner.CaptchaSolverResultHolder;
import primemover.runner.RecordedJobData;


public class RecordedJobDataBuilder {

	@Nonnull private IJob job;
	private Har har = null;
	private String pageSource = null;
	private Set<Cookie> cookies = null;
	private String sessionStorage = null;
	private String localStorage = null;
	private byte[] screenshot = null;
	private LocationInfo location = null;
	private CaptchaSolverResult capchaInfoBefore = null;
	private CaptchaSolverResult capchaInfoAfter = null;
	
	public RecordedJobDataBuilder(IJob job) {
		super();
		this.job = job;
	}
		
	public RecordedJobData build() {
		return new RecordedJobData(this);
	}
	
	public IJob getJob() {
		return this.job;
	}
	
	public RecordedJobDataBuilder setHar(Har har) {
		this.har = har;
		return this;
	}
	
	public Har getHar() {
		return this.har;
	}

	public RecordedJobDataBuilder setPageSource(String pageSource) {
		this.pageSource = pageSource;
		return this;
	}
	
	public String getPageSource() {
		return this.pageSource;
	}
	
	public RecordedJobDataBuilder setCookies(Set<Cookie> cookies) {
		this.cookies = Collections.unmodifiableSet(cookies);
		return this;
	}
	
	public Set<Cookie> getCookies() {
		return this.cookies;
	}

	public RecordedJobDataBuilder setSessionStorage(String sessionStorage) {
		this.sessionStorage = sessionStorage;
		return this;
	}
	
	public String getSessionStorage() {
		return sessionStorage;
	}
	
	public RecordedJobDataBuilder setLocalStorage(String localStorage) {
		this.localStorage = localStorage;
		return this;
	}
	
	public String getLocalStorage() {
		return this.localStorage;
	}
	
	public RecordedJobDataBuilder setScreenshot(byte[] screenshot) {
		this.screenshot = screenshot;
		return this;
	}
	
	public byte[] getScreenshot() {
		return screenshot;
	}

	public RecordedJobDataBuilder setLocation(LocationInfo location) {
		this.location = location;
		return this;
	}
	
	public LocationInfo getLocation() {
		return this.location;
	}

	public RecordedJobDataBuilder setCapchaResultBefore(CaptchaSolverResult captchaInfo) {
		this.capchaInfoBefore = captchaInfo;
		return this;
	}
	
	public RecordedJobDataBuilder setCapchaResultAfter(CaptchaSolverResult captchaInfo) {
		this.capchaInfoAfter = captchaInfo;
		return this;
	}
	
	public CaptchaSolverResultHolder getCaptchaResults() {
		return new CaptchaSolverResultHolder(this.capchaInfoBefore, this.capchaInfoAfter);
	}
}
