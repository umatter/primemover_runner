package primemover.runner.job.handler;

import java.time.Duration;

import org.openqa.selenium.Alert;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import primemover.anticaptcha.CaptchaJobHelper;
import primemover.anticaptcha.CaptchaJobHelper.JobPosition;
import primemover.model.jobs.HandleAlertJob;
import primemover.runner.context.IJobAwareRunnerContext;
import primemover.runner.job.IJobHandler;

public class AlertJobHandler implements IJobHandler {
	private static Logger logger = LoggerFactory.getLogger(AlertJobHandler.class);

	@Override
	public void handle(IJobAwareRunnerContext<?> context) {
		logger.debug("running job");

		WebDriver driver = context.getDriver();
		HandleAlertJob typedJob = (HandleAlertJob) context.getJob();

		// captcha before
		CaptchaJobHelper.solveCaptcha(context, JobPosition.BEFORE);

		// Wait for Element
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5));
		Alert dialog = wait.until(ExpectedConditions.alertIsPresent());

		switch (typedJob.getAction()) {
		case ACCEPT:
			dialog.accept();
			break;
		case REJECT:
			dialog.dismiss();
			break;
		default:
			throw new UnsupportedOperationException("Not implemented yet");
		}

		// captcha after
		CaptchaJobHelper.solveCaptcha(context, JobPosition.AFTER);
	}
}
