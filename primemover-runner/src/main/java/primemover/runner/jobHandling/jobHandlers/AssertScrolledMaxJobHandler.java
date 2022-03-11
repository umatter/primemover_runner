package primemover.runner.jobHandling.jobHandlers;

import primemover.model.IJob;
import primemover.model.jobs.AssertScrolledMaxJob;
import primemover.runner.OperatorContext;
import primemover.runner.jobHandling.IJobHandler;
import primemover.util.Scrolling;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AssertScrolledMaxJobHandler implements IJobHandler {

	private static Logger logger = LoggerFactory.getLogger(AssertScrolledMaxJobHandler.class);

    public void handle(OperatorContext context, IJob job) {
        logger.debug("running job");

        WebDriver driver = context.getDriver();
        AssertScrolledMaxJob typedJob = (AssertScrolledMaxJob) job;
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
