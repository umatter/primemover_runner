package primemover.runner.context;

import org.openqa.selenium.firefox.FirefoxOptions;

public class FirefoxContext extends ContextBase<FirefoxOptions> {

	public FirefoxContext() {
		super();
	}

	public FirefoxOptions getFirefoxOptions() {
		return this.getOptions();
	}

	public void setFirefoxOptions(FirefoxOptions firefoxOptions) {
		this.setOptions(firefoxOptions);
	}

}
