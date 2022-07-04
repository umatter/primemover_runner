package primemover.runner.context;

import org.openqa.selenium.firefox.FirefoxOptions;

public class FirefoxContext extends ContextBase {

	private FirefoxOptions firefoxOptions;
	
	public FirefoxContext() {
		super();
	}

	public FirefoxOptions getFirefoxOptions() {
		return firefoxOptions;
	}

	public void setFirefoxOptions(FirefoxOptions firefoxOptions) {
		this.firefoxOptions = firefoxOptions;
	}

}
