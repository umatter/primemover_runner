package primemover.runner.context;

import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.WebDriver;

import primemover.anticaptcha.ICaptchaSolver;
import primemover.capture.RecordingProxy;
import primemover.model.Crawler;
import primemover.model.IJob;

public abstract class ContextBase<OptionsType extends MutableCapabilities> implements IRunnerContext<OptionsType> {

	private OptionsType options;
	private RecordingProxy recorderProxy;
	private WebDriver driver;
	private Crawler crawler;
	private ICaptchaSolver captchaSolver;

	public ContextBase() {
	}

	@Override
	public OptionsType getOptions() {
		return options;
	}

	public void setOptions(OptionsType options) {
		this.options = options;
	}

	@Override
	public RecordingProxy getRecordingProxy() {
		return recorderProxy;
	}

	public void setRecordingProxy(RecordingProxy recorder) {
		this.recorderProxy = recorder;
	}

	@Override
	public WebDriver getDriver() {
		return driver;
	}

	public void setDriver(WebDriver driver) {
		this.driver = driver;
	}

	@Override
	public Crawler getCrawler() {
		return crawler;
	}

	public void setCrawler(Crawler crawler) {
		this.crawler = crawler;
	}

	@Override
	public ICaptchaSolver getCaptchaSolver() {
		return captchaSolver;
	}

	public void setCaptchaSolver(ICaptchaSolver captchaSolver) {
		this.captchaSolver = captchaSolver;
	}

	public boolean isValid() {
		return recorderProxy != null && driver != null && crawler != null && captchaSolver != null;
	}
}
