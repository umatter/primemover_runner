package primemover.runner.context;

import java.util.Set;

import org.openqa.selenium.Cookie;

import primemover.anticaptcha.CaptchaSolverResult;
import primemover.model.LocationInfo;
import primemover.runner.RecordedJobData;

public interface IJobDataRecorder {

	void setHar(String har);

	void setPageSource(String pageSource);

	void setCookies(Set<Cookie> cookies);

	void setSessionStorage(String sessionStorage);

	void setLocalStorage(String localStorage);

	void setScreenshot(byte[] screenshot);

	void setLocation(LocationInfo location);

	void setCapchaResultBefore(CaptchaSolverResult capchaInfo);

	void setCapchaResultAfter(CaptchaSolverResult capchaInfo);

	RecordedJobData getRecordings();
}