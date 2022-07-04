package primemover.runner.job.handler;

import primemover.model.IJob;
import primemover.model.jobs.ScrollByLengthJob;
import primemover.runner.context.ContextBase;
import primemover.runner.job.IJobHandler;
import primemover.util.Scrolling;
import primemover.util.Waiter;

import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ScrollByLengthJobHandler implements IJobHandler {
    
	private static Logger logger = LoggerFactory.getLogger(ScrollByLengthJobHandler.class);

    public void handle(ContextBase context, IJob job) {
        logger.debug("running job");

        WebDriver driver = context.getDriver();
				
        ScrollByLengthJob typedJob = (ScrollByLengthJob) job;

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
