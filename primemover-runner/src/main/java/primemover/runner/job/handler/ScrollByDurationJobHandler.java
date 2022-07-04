package primemover.runner.job.handler;

import primemover.model.IJob;
import primemover.model.jobs.ScrollByDurationJob;
import primemover.runner.context.ContextBase;
import primemover.runner.job.IJobHandler;
import primemover.util.Scrolling;
import primemover.util.Waiter;

import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ScrollByDurationJobHandler implements IJobHandler {
    
	private static Logger logger = LoggerFactory.getLogger(ScrollByDurationJobHandler.class);

    public void handle(ContextBase context, IJob job) {
        logger.debug("running job");

        WebDriver driver = context.getDriver();
        ScrollByDurationJob typedJob = (ScrollByDurationJob) job;

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
