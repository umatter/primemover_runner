package primemover.runner;

import org.openqa.selenium.Cookie;
import primemover.anticaptcha.CaptchaSolverResult;
import primemover.capture.SessionStorageRef;
import primemover.model.IJob;
import primemover.model.LocationInfo;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class RecordedJobDataBuilder {

	@Nonnull
	private IJob job;
	private String har = null;
	private String pageSource = null;
	private Set<Cookie> cookies = null;
	private String sessionStorage = null;
	private String localStorage = null;
	private byte[] screenshot = null;
	private String screenshotExtension = "png";
	private SessionStorageRef sessionStorageRef = null;
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

	public RecordedJobDataBuilder setHar(String har) {
		this.har = har;
		return this;
	}

	public String getHar() {
		return this.har;
	}

	public RecordedJobDataBuilder setPageSource(String pageSource) {
		this.pageSource = pageSource;
		return this;
	}

	public String getPageSource() {
		return this.pageSource;
	}

	public RecordedJobDataBuilder setCookies(@Nullable Set<Cookie> cookies) {
		if (cookies == null) {
			cookies = new HashSet<>();
		}
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
		return setScreenshot(screenshot, "png");
	}

	/** The screenshot and the extension of its format ({@code png}, or {@code jpg} for a compact capture). */
	public RecordedJobDataBuilder setScreenshot(byte[] screenshot, String extension) {
		this.screenshot = screenshot;
		this.screenshotExtension = extension;
		return this;
	}

	public byte[] getScreenshot() {
		return screenshot;
	}

	public String getScreenshotExtension() {
		return screenshotExtension;
	}

	/** Stored instead of the session storage when it is unchanged (compact capture). */
	public RecordedJobDataBuilder setSessionStorageRef(@Nullable SessionStorageRef sessionStorageRef) {
		this.sessionStorageRef = sessionStorageRef;
		return this;
	}

	public @Nullable SessionStorageRef getSessionStorageRef() {
		return sessionStorageRef;
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
