package primemover.runner;

import org.openqa.selenium.chrome.ChromeOptions;

public class ChromeContext extends OperatorContext {

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
