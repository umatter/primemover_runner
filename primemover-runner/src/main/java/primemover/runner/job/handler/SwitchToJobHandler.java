package primemover.runner.job.handler;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import primemover.model.jobs.SwitchToJob;
import primemover.runner.context.IJobAwareRunnerContext;
import primemover.runner.job.IJobHandler;
import primemover.util.SelectingHelper;

import java.time.Duration;
import java.util.List;

public class SwitchToJobHandler implements IJobHandler {

	private static Logger logger = LoggerFactory.getLogger(SwitchToJobHandler.class);

	@Override
	public void handle(IJobAwareRunnerContext<?> context) {
		logger.debug("running job");

		WebDriver driver = context.getDriver();
		SwitchToJob typedJob = (SwitchToJob) context.getJob();

		List<WebElement> elements = SelectingHelper.getElementsByWebdriver(driver, typedJob.getType(),
				typedJob.getSelector());

		if (elements.size() == 1) {
			logger.debug("Switching to element for job {}", typedJob.getId());
			WebElement selectedElement = elements.get(0);

			// Wait for Element
			WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5));
			wait.until(ExpectedConditions.frameToBeAvailableAndSwitchToIt(selectedElement));
		} else {
			logger.warn("Found more than one element for job {}", typedJob.getId());
			throw new IllegalStateException("More than one element found for job " + typedJob.getId());
		}

		try {
			Thread.sleep(500);
		} catch (InterruptedException e) {
			// nothing to do
		}
	}
}
