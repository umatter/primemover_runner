package primemover.runner.job.handler;

import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import primemover.anticaptcha.CaptchaJobHelper;
import primemover.anticaptcha.CaptchaJobHelper.JobPosition;
import primemover.model.jobs.VisitJob;
import primemover.runner.context.IJobAwareRunnerContext;
import primemover.runner.job.IJobHandler;
import primemover.util.NavigationHelper;

public class VisitJobHandler implements IJobHandler {
	private static Logger logger = LoggerFactory.getLogger(VisitJobHandler.class);

	@Override
	public void handle(IJobAwareRunnerContext<?> context) {
		boolean success = false;
		WebDriver driver = context.getDriver();
		VisitJob typedJob = (VisitJob) context.getJob();

		logger.debug("running job");

		// captcha before
		CaptchaJobHelper.solveCaptcha(context, JobPosition.BEFORE);

		logger.debug("navigate to {} for job {}", typedJob.getUrl(), typedJob.getId());
		NavigationHelper navHelper = new NavigationHelper(driver, 2 * 60 * 1000l); // 2 Minuten
		driver.navigate().to(typedJob.getUrl());

		// Wait for DOM-reload
		try {
			navHelper.waitForPageLoad(typedJob.getUrl());
		} catch (Exception e) {
			logger.warn("simple page load wait (DOM) failed for job {}.", typedJob.getId(), e);
			// second chance below
		}

		// wait for pending javascript triggered loads
		try {
			primemover.util.Waiter.waitUntilJSReady(context.getDriver(), 5, 12); // check every 5 seconds for 1 Minutes
			success = true;
		} catch (Exception e) {
			logger.warn("page ready load wait failed for job {}.", typedJob.getId(), e);
		}

		// captcha after
		CaptchaJobHelper.solveCaptcha(context, JobPosition.AFTER);

		if (!success) {
			logger.warn("Timeout visiting page {} for job {}", typedJob.getUrl(), typedJob.getId());
			throw new TimeoutException("Timeout visiting page " + typedJob.getUrl() + " for job " + typedJob.getId());
		}

	}
}
