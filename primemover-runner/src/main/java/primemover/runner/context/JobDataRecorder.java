package primemover.runner.context;

import java.util.Optional;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.openqa.selenium.Cookie;

import primemover.anticaptcha.CaptchaSolverResult;
import primemover.model.IJob;
import primemover.model.LocationInfo;
import primemover.runner.RecordedJobData;
import primemover.runner.RecordedJobDataBuilder;

public class JobDataRecorder implements IJobDataRecorder {

	@Nonnull
	private IJob job;
	private String har = null;
	private String pageSource = null;
	private Set<Cookie> cookies = null;
	private String sessionStorage = null;
	private String localStorage = null;
	private byte[] screenshot = null;
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
		this.screenshot = screenshot;
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
				.setScreenshot(screenshot)//
				.setSessionStorage(sessionStorage)//
				.build();
	}

}
