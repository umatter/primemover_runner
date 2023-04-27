package primemover.runner.job.handler;

import java.util.List;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import primemover.model.jobs.ClickJob;
import primemover.runner.context.IJobAwareRunnerContext;
import primemover.runner.job.IJobHandler;
import primemover.util.SelectingHelper;
import primemover.util.Waiter;

public class ClickJobHandler implements IJobHandler {

	private static Logger logger = LoggerFactory.getLogger(ClickJobHandler.class);

	@Override
	public void handle(IJobAwareRunnerContext<?> context) {
		logger.debug("running job");

		WebDriver driver = context.getDriver();
		ClickJob typedJob = (ClickJob) context.getJob();

		List<WebElement> elements = SelectingHelper.getElementsByWebdriver(driver, typedJob.getType(),
				typedJob.getSelector());

		if (elements.size() == 1) {
			logger.debug("Clicking on element for job {}", typedJob.getId());
			WebElement selectedElement = elements.get(0);

			// Wait for Element
			WebDriverWait wait = new WebDriverWait(driver, 5);
			wait.until(ExpectedConditions.elementToBeClickable(selectedElement));

			selectedElement.click();
			if (typedJob.expectsPageReload()) {
				Waiter.waitUntilObjectGone(driver, 2000, selectedElement);
			}
		} else {
			logger.warn("Found more than one element for job {}", typedJob.getId());
			throw new IllegalStateException("More than one element found for job " + typedJob.getId());
		}

		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			// nothing to do
		}
	}
}
