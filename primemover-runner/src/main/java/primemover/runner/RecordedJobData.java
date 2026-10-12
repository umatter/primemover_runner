package primemover.runner;

import org.openqa.selenium.Cookie;
import org.springframework.util.StringUtils;
import primemover.capture.SessionStorageRef;
import primemover.model.IJob;
import primemover.model.LocationInfo;
import primemover.service.datawriter.IRecordedData;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

public class RecordedJobData implements IRecordedData {

	private byte[] screenshot;
	private String screenshotExtension = "png";
	private SessionStorageRef sessionStorageRef;
	private String har;
	private String pageSource;
	private Set<Cookie> cookies;
	private String sessionStorage;
	private String localStorage;
	private LocationInfo location;
	private CaptchaSolverResultHolder captchaResults = new CaptchaSolverResultHolder();
	/**
	 * Warnings attached to the job while it ran (see
	 * {@link primemover.runner.JobDiagnostics}) -- the reasons it is reported as
	 * {@code warning} rather than {@code success}.
	 *
	 * <p>
	 * They are <b>serialised into the job-data archive</b> by
	 * {@link primemover.service.datawriter.JobDataWriter}, as a
	 * {@code <prefix>_job_warnings_<jobId>.json} entry containing the list. Until
	 * 2026-08-27 they were write-only -- populated here and read by nothing -- so
	 * the claim that they travel with the recorded data was not true of any file
	 * on disk. It matters most for exactly the case the status contract is about:
	 * whoever later analyses a HAR/job-data folder must be able to see that this
	 * session lost its treatment, long after the database's status_message has
	 * been truncated or rotated away.
	 *
	 * <p>
	 * Copy-on-write because the recordings are handed to several writers on the
	 * executor thread and may be read while more warnings arrive.
	 */
	private final List<String> warnings = new CopyOnWriteArrayList<>();
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
		this.screenshotExtension = builder.getScreenshotExtension();
		this.sessionStorageRef = builder.getSessionStorageRef();
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

	/** {@code png}, or {@code jpg} when a compact capture re-encoded it. */
	public String getScreenshotExtension() {
		return this.screenshotExtension;
	}

	/** The pointer stored instead of an unchanged session storage, or {@code null}. */
	public SessionStorageRef getSessionStorageRef() {
		return this.sessionStorageRef;
	}

	public boolean hasSessionStorageRef() {
		return this.sessionStorageRef != null;
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

	/**
	 * Warnings recorded for this job -- the reasons it is reported as
	 * {@code warning} rather than {@code success}. Never {@code null}.
	 */
	public List<String> getWarnings() {
		return List.copyOf(warnings);
	}

	public void addWarnings(@Nullable Collection<String> warnings) {
		if (warnings == null || warnings.isEmpty()) {
			return;
		}
		this.warnings.addAll(warnings);
	}

	public boolean hasWarnings() {
		return !warnings.isEmpty();
	}
}
