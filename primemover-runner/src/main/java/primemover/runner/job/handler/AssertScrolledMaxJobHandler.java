package primemover.runner.job.handler;

import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import primemover.model.jobs.AssertScrolledMaxJob;
import primemover.runner.context.IJobAwareRunnerContext;
import primemover.runner.job.IJobHandler;
import primemover.util.Scrolling;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class AssertScrolledMaxJobHandler implements IJobHandler {

	private static Logger logger = LoggerFactory.getLogger(AssertScrolledMaxJobHandler.class);

	@Override
	public void handle(IJobAwareRunnerContext<?> context) {
		logger.debug("running job");

		WebDriver driver = context.getDriver();
		AssertScrolledMaxJob typedJob = (AssertScrolledMaxJob) context.getJob();
		Boolean res;

		switch (typedJob.getDirection()) {
		case DOWN:
			res = Scrolling.isScrolledToBootom(driver);
			assertTrue(res, "page is not scrolled to bottom");
			break;
		case UP:
			res = Scrolling.isScrolledToTop(driver);
			assertTrue(res, "page is not scrolled to Top");
			break;
		default:
			throw new UnsupportedOperationException("Not implemented yet");
		}
	}
}
