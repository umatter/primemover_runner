package primemover.runner.context;

import org.openqa.selenium.WebDriver;

import primemover.capture.RecordingProxy;
import primemover.model.Crawler;

public abstract class ContextBase {
	
	private RecordingProxy recorder;
	private WebDriver driver;
	private Crawler crawler;

	public ContextBase() {
	}

	public RecordingProxy getRecorder() {
		return recorder;
	}

	public void setRecorder(RecordingProxy recorder) {
		this.recorder = recorder;
	}

	public WebDriver getDriver() {
		return driver;
	}

	public void setDriver(WebDriver driver) {
		this.driver = driver;
	}

	public Crawler getCrawler() {
		return crawler;
	}

	public void setCrawler(Crawler crawler) {
		this.crawler = crawler;
	}
}
