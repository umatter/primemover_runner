package primemover.runner;

import org.openqa.selenium.remote.DesiredCapabilities;
import primemover.anticaptcha.ICaptchaSolver;
import primemover.capture.RecordingProxy;
import primemover.model.Crawler;
import primemover.runner.context.MultiloginContext;

public interface IContextBuilder {
	
	public MultiloginContext buildContext(Crawler crawler, DesiredCapabilities capabilities, RecordingProxy recorder,
			ICaptchaSolver captchaSolver);
	
	public void destroyContext(MultiloginContext context);

}
