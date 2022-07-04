package primemover.runner.job.handler;

import primemover.model.IJob;
import primemover.model.jobs.AssertTextInFieldJob;
import primemover.runner.context.ContextBase;
import primemover.runner.job.IJobHandler;
import primemover.util.SelectingHelper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AssertTextInFieldJobHandler implements IJobHandler {
   
	private static Logger logger = LoggerFactory.getLogger(AssertTextInFieldJobHandler.class);

    public void handle(ContextBase context, IJob job) {
        logger.debug("running job");

        WebDriver driver = context.getDriver();
        AssertTextInFieldJob typedJob = (AssertTextInFieldJob) job;

        WebElement element = SelectingHelper.getElementByWebdriver(driver, typedJob.getType(),
                typedJob.getSelector());
        
        String textFound = element.getAttribute("innerText");
        assertEquals(textFound, typedJob.getValue());
    }
}
