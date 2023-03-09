package primemover.anticaptcha;

import org.openqa.selenium.MutableCapabilities;

import primemover.capture.RecordingProxy;
import primemover.model.Crawler;
import primemover.model.IJob;
import primemover.runner.context.ContextBase;

public interface ICaptchaSolver {

	/**
	 * This method is called to set any additional options for the driver before
	 * start
	 * 
	 * @param crawler
	 * @param options
	 * @param recorder
	 * @return
	 */
	boolean setOptions(Crawler crawler, MutableCapabilities options, RecordingProxy recorder);

	/**
	 * This method is called to initialize anti-captcha after the driver was started
	 * 
	 * @param context
	 * @return
	 */
	boolean setupDriver(ContextBase<?> context);

	/**
	 * This method is called to solve captchas on the currently available page
	 * 
	 * @param context
	 * @param job
	 * @param submit
	 * @return
	 */
	CaptchaSolverResult solve(ContextBase<?> context, IJob job, Boolean submit);

}