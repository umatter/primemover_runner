package primemover.runner.job.handler;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import primemover.model.jobs.AssertElementExistsJob;
import primemover.runner.context.IJobAwareRunnerContext;
import primemover.runner.job.IJobHandler;
import primemover.util.SelectingHelper;

public class AssertElementExistsJobHandler implements IJobHandler {

	private static Logger logger = LoggerFactory.getLogger(AssertElementExistsJobHandler.class);

	@Override
	public void handle(IJobAwareRunnerContext<?> context) {
		logger.debug("running job");

		WebDriver driver = context.getDriver();
		AssertElementExistsJob typedJob = (AssertElementExistsJob) context.getJob();

		List<WebElement> elements = SelectingHelper.getElementsByWebdriver(driver, typedJob.getType(),
				typedJob.getSelector());

		assertTrue(elements.size() > 0, "element(s) not found");
	}
}
