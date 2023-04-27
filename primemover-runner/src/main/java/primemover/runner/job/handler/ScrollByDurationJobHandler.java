package primemover.runner.job.handler;

import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import primemover.model.jobs.ScrollByDurationJob;
import primemover.runner.context.IJobAwareRunnerContext;
import primemover.runner.job.IJobHandler;
import primemover.util.Scrolling;
import primemover.util.Waiter;

public class ScrollByDurationJobHandler implements IJobHandler {

	private static Logger logger = LoggerFactory.getLogger(ScrollByDurationJobHandler.class);

	@Override
	public void handle(IJobAwareRunnerContext<?> context) {
		logger.debug("running job");

		WebDriver driver = context.getDriver();
		ScrollByDurationJob typedJob = (ScrollByDurationJob) context.getJob();

		switch (typedJob.getDirection()) {
		case DOWN:
			Scrolling.scrollDownForDurationByJavascript(driver, typedJob.getDuration());
			Waiter.waitUntilJSReady(driver, 60);
			break;
		case UP:
			Scrolling.scrollUpForDurationByJavascript(driver, typedJob.getDuration());
			Waiter.waitUntilJSReady(driver, 60);
			break;
		default:
			throw new UnsupportedOperationException("Not implemented yet");
		}

	}
}
