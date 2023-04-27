package primemover.runner.context;

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
import primemover.runner.RecordedJobDataBuilder;


public class JobDataRecorder implements IJobDataRecorder  {

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
	
	public JobDataRecorder(IJob job) {
		super();
		this.job = job;
	}
	
	@Override
	public void setHar(Har har) {
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
	public void setCapchaResultBefore(CaptchaSolverResult captchaInfo) {
		this.capchaInfoBefore = captchaInfo;
	}
	
	@Override
	public void setCapchaResultAfter(CaptchaSolverResult captchaInfo) {
		this.capchaInfoAfter = captchaInfo;
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
