package primemover.runner.jobHandling.jobHandlers;

import primemover.model.IJob;
import primemover.model.jobs.AssertElementExistsJob;
import primemover.runner.OperatorContext;
import primemover.runner.jobHandling.IJobHandler;
import primemover.util.SelectingHelper;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AssertElementExistsJobHandler implements IJobHandler {

	private static Logger logger = LoggerFactory.getLogger(AssertElementExistsJobHandler.class);

    public void handle(OperatorContext context, IJob job) {
        logger.debug("running job");

        WebDriver driver = context.getDriver();
        AssertElementExistsJob typedJob = (AssertElementExistsJob) job;
        
        List<WebElement> elements = SelectingHelper.getElementsByWebdriver(driver, typedJob.getType(),
                typedJob.getSelector());
        
        assertTrue(elements.size() > 0, "element(s) not found");
    }
}
