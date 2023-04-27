package primemover.runner.job.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import primemover.model.jobs.AssertTextInFieldJob;
import primemover.runner.context.IJobAwareRunnerContext;
import primemover.runner.job.IJobHandler;
import primemover.util.SelectingHelper;

public class AssertTextInFieldJobHandler implements IJobHandler {

	private static Logger logger = LoggerFactory.getLogger(AssertTextInFieldJobHandler.class);

	@Override
	public void handle(IJobAwareRunnerContext<?> context) {
		logger.debug("running job");

		WebDriver driver = context.getDriver();
		AssertTextInFieldJob typedJob = (AssertTextInFieldJob) context.getJob();

		WebElement element = SelectingHelper.getElementByWebdriver(driver, typedJob.getType(), typedJob.getSelector());

		String textFound = element.getAttribute("innerText");
		assertEquals(textFound, typedJob.getValue());
	}
}
