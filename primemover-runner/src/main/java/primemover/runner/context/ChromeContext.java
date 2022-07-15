package primemover.runner.context;

import org.openqa.selenium.chrome.ChromeOptions;

public class ChromeContext extends ContextBase<ChromeOptions> {

	public ChromeContext() {
		super();
	}

	public ChromeOptions getChromeOptions() {
		return this.getOptions();
	}

	public void setChromeOptions(ChromeOptions options) {
		this.setOptions(options);
	}

}
