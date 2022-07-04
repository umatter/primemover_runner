package primemover.runner.job.handler;

import primemover.model.IJob;
import primemover.runner.context.ContextBase;
import primemover.runner.job.IJobHandler;
import primemover.util.Page;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AssertPageLoadedJobHandler implements IJobHandler {

	private static Logger logger = LoggerFactory.getLogger(AssertPageLoadedJobHandler.class);

    public void handle(ContextBase context, IJob job) {
        logger.debug("running job");

        WebDriver driver = context.getDriver();
        // AssertPageLoadedJob typedJob = (AssertPageLoadedJob) job;

        Boolean isReady = Page.isPageReady(driver);
        assertTrue(isReady, "page is not ready");
    }
}
