package primemover.runner.context;

import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.WebDriver;

import primemover.anticaptcha.ICaptchaSolver;
import primemover.capture.RecordingProxy;
import primemover.model.Crawler;

public abstract class ContextBase<OptionsType extends MutableCapabilities> {

	private OptionsType options;
	private RecordingProxy recorder;
	private WebDriver driver;
	private Crawler crawler;
	private ICaptchaSolver captchaSolver;

	public ContextBase() {
	}

	public OptionsType getOptions() {
		return options;
	}

	public void setOptions(OptionsType options) {
		this.options = options;
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

	public ICaptchaSolver getCaptchaSolver() {
		return captchaSolver;
	}

	public void setCaptchaSolver(ICaptchaSolver captchaSolver) {
		this.captchaSolver = captchaSolver;
	}

	public boolean isValid() {
		return options != null && recorder != null && driver != null && crawler != null && captchaSolver != null;
	}
}
