package primemover.runner;

import org.openqa.selenium.firefox.FirefoxOptions;

public class FirefoxContext extends OperatorContext {

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
