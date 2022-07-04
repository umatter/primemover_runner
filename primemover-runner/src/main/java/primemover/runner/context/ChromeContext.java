package primemover.runner.context;

import org.openqa.selenium.chrome.ChromeOptions;

public class ChromeContext extends ContextBase {

	private ChromeOptions options;
	
	public ChromeContext() {
		super();
	}

	public ChromeOptions getChromeOptions() {
		return options;
	}

	public void setChromeOptions(ChromeOptions options) {
		this.options = options;
	}

}
