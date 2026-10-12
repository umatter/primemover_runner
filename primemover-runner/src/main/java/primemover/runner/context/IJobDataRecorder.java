package primemover.runner.context;

import org.openqa.selenium.Cookie;
import primemover.anticaptcha.CaptchaSolverResult;
import primemover.capture.SessionStorageRef;
import primemover.model.LocationInfo;
import primemover.runner.RecordedJobData;

import java.util.Optional;
import java.util.Set;

public interface IJobDataRecorder {

	void setHar(String har);

	void setPageSource(String pageSource);

	void setCookies(Set<Cookie> cookies);

	void setSessionStorage(String sessionStorage);

	void setLocalStorage(String localStorage);

	void setScreenshot(byte[] screenshot);

	/** The screenshot in another format than PNG; {@code extension} names it ({@code jpg}). */
	void setScreenshot(byte[] screenshot, String extension);

	/** Store this pointer instead of the session storage, which is unchanged (compact capture). */
	void setSessionStorageRef(SessionStorageRef sessionStorageRef);

	void setLocation(LocationInfo location);

	void setCapchaResultBefore(Optional<CaptchaSolverResult> capchaInfo);

	void setCapchaResultAfter(Optional<CaptchaSolverResult> capchaInfo);

	RecordedJobData getRecordings();
}