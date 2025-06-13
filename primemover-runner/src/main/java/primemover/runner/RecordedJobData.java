package primemover.runner;

import org.openqa.selenium.Cookie;
import org.springframework.util.StringUtils;
import primemover.model.IJob;
import primemover.model.LocationInfo;
import primemover.service.datawriter.IRecordedData;

import javax.annotation.Nonnull;
import java.util.Set;

public class RecordedJobData implements IRecordedData {

	private byte[] screenshot;
	private String har;
	private String pageSource;
	private Set<Cookie> cookies;
	private String sessionStorage;
	private String localStorage;
	private LocationInfo location;
	private CaptchaSolverResultHolder captchaResults = new CaptchaSolverResultHolder();
	@Nonnull
	private IJob job;

	public RecordedJobData(RecordedJobDataBuilder builder) {
		super();

		this.job = builder.getJob();
		this.pageSource = builder.getPageSource();
		this.sessionStorage = builder.getSessionStorage();
		this.cookies = builder.getCookies();
		this.localStorage = builder.getLocalStorage();
		this.location = builder.getLocation();
		this.har = builder.getHar();
		this.screenshot = builder.getScreenshot();
		this.captchaResults = builder.getCaptchaResults();
	}

	@Override
	@Nonnull
	public IJob getJob() {
		return job;
	}

	public String getHar() {
		return har;
	}

	public void setHar(String har) {
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

	public CaptchaSolverResultHolder getCaptchaResults() {
		return captchaResults;
	}

	public void setCapchaResults(CaptchaSolverResultHolder capchaInfo) {
		this.captchaResults = capchaInfo;
	}

	public boolean hasAnyCaptchaResult() {
		return captchaResults != null && captchaResults.hasAnyCaptchaResult();
	}
}
