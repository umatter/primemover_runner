package primemover.runner.jobs.handler;

import primemover.captcha.CaptchaJobHelper;
import primemover.captcha.CaptchaSolver;
import primemover.captcha.CaptchaJobHelper.JobPosition;
import primemover.model.IJob;
import primemover.model.SelectorType;
import primemover.model.TypingModeType;
import primemover.model.jobs.EnterTextFieldJob;
import primemover.runner.context.ContextBase;
import primemover.runner.jobs.IJobHandler;
import primemover.util.SelectingHelper;
import primemover.util.Typing;

import java.util.function.Supplier;

import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class EnterTextFieldJobHandler implements IJobHandler {

    @Autowired
	private CaptchaSolver captchaSolver;
    
	private static Logger logger = LoggerFactory.getLogger(EnterTextFieldJobHandler.class);

    public void handle(ContextBase context, IJob job) {
        logger.debug("running job");
        
        WebDriver driver = context.getDriver();
        EnterTextFieldJob typedJob = (EnterTextFieldJob) job;

        boolean done = false;
        int retriesCount = 0;
        
        logger.debug("typing text ({}) to field ({} : {})", typedJob.getValue(), typedJob.getType(), typedJob.getSelector());
        
        while (!done && retriesCount < 5) {
            try {
                retriesCount++;
                logger.debug("typing text. Trial {} of {}", retriesCount, 5);
                SelectorType selectorType = typedJob.getType();
                String selector = typedJob.getSelector();
                
                // captcha before
                CaptchaJobHelper.solveCaptcha(context, typedJob, JobPosition.BEFORE, captchaSolver);
                
                // provide WebElement dynamic for below functions. Google search will stale the element with the search suggestions
                Supplier<WebElement> elementSup = () -> SelectingHelper.getElementByWebdriver(driver, selectorType, selector);
                
                TypingModeType typingMode = typedJob.getTypingMode();
                
                // typing text
                switch (typingMode) {
                case DIRECT:
                    Typing.typeTextDirect(elementSup, typedJob.getValue(), typedJob.getAppendReturn());
                    break;
                case SIMULATED_NOTYPOS:
                    Typing.typeTextSimulated(elementSup, typedJob.getValue(), typedJob.getAppendReturn(), false, false);
                    break;
                case SIMULATED_KEEPINGTYPOS:
                    Typing.typeTextSimulated(elementSup, typedJob.getValue(), typedJob.getAppendReturn(), true, true);
                    break;
                case SIMULATED_FIXINGTYPOS:
                    Typing.typeTextSimulated(elementSup, typedJob.getValue(), typedJob.getAppendReturn(), true, false);
                    break;
                default:
                    throw new UnsupportedOperationException("Not implemented yet (" + typingMode + ")");
                }
                logger.debug("done typing text");
                
                // captcha after
                CaptchaJobHelper.solveCaptcha(context, typedJob, JobPosition.AFTER, captchaSolver);
                
                done = true;
            } catch (StaleElementReferenceException e) { 
                done = false; // retry
                logger.debug("retrying typing text in {} seconds", 3);
                
                // just wait
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e1) {
                    // nothing to do
                }
            }
            
        }
        

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            // nothing to do
        }

        primemover.util.Waiter.waitUntilJSReady(driver, 120);
    }
}
