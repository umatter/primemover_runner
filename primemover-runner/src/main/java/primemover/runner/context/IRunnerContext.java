package primemover.runner.context;

import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.WebDriver;
import primemover.anticaptcha.ICaptchaSolver;
import primemover.capture.RecordingProxy;
import primemover.model.Crawler;

public interface IRunnerContext<OptionsType extends MutableCapabilities> {

	OptionsType getOptions();

	RecordingProxy getRecordingProxy();

	WebDriver getDriver();

	Crawler getCrawler();

	ICaptchaSolver getCaptchaSolver();

}