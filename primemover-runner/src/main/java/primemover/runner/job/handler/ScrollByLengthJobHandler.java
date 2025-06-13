package primemover.runner.job.handler;

import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import primemover.model.jobs.ScrollByLengthJob;
import primemover.runner.context.IJobAwareRunnerContext;
import primemover.runner.job.IJobHandler;
import primemover.util.Scrolling;
import primemover.util.Waiter;

public class ScrollByLengthJobHandler implements IJobHandler {

	private static Logger logger = LoggerFactory.getLogger(ScrollByLengthJobHandler.class);

	@Override
	public void handle(IJobAwareRunnerContext<?> context) {
		logger.debug("running job");

		WebDriver driver = context.getDriver();

		ScrollByLengthJob typedJob = (ScrollByLengthJob) context.getJob();

		switch (typedJob.getDirection()) {
		case DOWN:
			Scrolling.scrollDownByJavascript(driver, typedJob.getLength());
			Waiter.waitUntilJSReady(driver, 60);
			break;
		case UP:
			Scrolling.scrollUpByJavascript(driver, typedJob.getLength());
			Waiter.waitUntilJSReady(driver, 60);
			break;
		default:
			throw new UnsupportedOperationException("Not implemented yet");
		}
	}
}
