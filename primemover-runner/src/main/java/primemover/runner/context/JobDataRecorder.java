package primemover.runner.context;

import org.openqa.selenium.Cookie;
import primemover.anticaptcha.CaptchaSolverResult;
import primemover.capture.SessionStorageRef;
import primemover.model.IJob;
import primemover.model.LocationInfo;
import primemover.runner.RecordedJobData;
import primemover.runner.RecordedJobDataBuilder;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Optional;
import java.util.Set;

public class JobDataRecorder implements IJobDataRecorder {

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
	@Nullable
	private CaptchaSolverResult capchaInfoBefore = null;
	@Nullable
	private CaptchaSolverResult capchaInfoAfter = null;

	public JobDataRecorder(IJob job) {
		super();
		this.job = job;
	}

	@Override
	public void setHar(String har) {
		this.har = har;
	}

	@Override
	public void setPageSource(String pageSource) {
		this.pageSource = pageSource;
	}

	@Override
	public void setCookies(Set<Cookie> cookies) {
		this.cookies = cookies;
	}

	@Override
	public void setSessionStorage(String sessionStorage) {
		this.sessionStorage = sessionStorage;
	}

	@Override
	public void setLocalStorage(String localStorage) {
		this.localStorage = localStorage;
	}

	@Override
	public void setScreenshot(byte[] screenshot) {
		setScreenshot(screenshot, "png");
	}

	@Override
	public void setScreenshot(byte[] screenshot, String extension) {
		this.screenshot = screenshot;
		this.screenshotExtension = extension;
	}

	@Override
	public void setSessionStorageRef(SessionStorageRef sessionStorageRef) {
		this.sessionStorageRef = sessionStorageRef;
	}

	@Override
	public void setLocation(LocationInfo location) {
		this.location = location;
	}

	@Override
	public void setCapchaResultBefore(Optional<CaptchaSolverResult> captchaInfo) {
		this.capchaInfoBefore = captchaInfo.orElse(null);
	}

	@Override
	public void setCapchaResultAfter(Optional<CaptchaSolverResult> captchaInfo) {
		this.capchaInfoAfter = captchaInfo.orElse(null);
	}

	@Override
	public RecordedJobData getRecordings() {
		return new RecordedJobDataBuilder(job) //
				.setCapchaResultAfter(capchaInfoAfter)//
				.setCapchaResultBefore(capchaInfoBefore)//
				.setCookies(cookies)//
				.setHar(har)//
				.setLocalStorage(localStorage)//
				.setLocation(location)//
				.setPageSource(pageSource)//
				.setScreenshot(screenshot, screenshotExtension)//
				.setSessionStorage(sessionStorage)//
				.setSessionStorageRef(sessionStorageRef)//
				.build();
	}

}
