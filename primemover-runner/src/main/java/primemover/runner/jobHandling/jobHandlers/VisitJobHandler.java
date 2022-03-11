package primemover.runner.jobHandling.jobHandlers;

import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;

import primemover.captcha.CaptchaJobHelper;
import primemover.captcha.CaptchaSolver;
import primemover.captcha.CaptchaJobHelper.JobPosition;
import primemover.model.IJob;
import primemover.model.jobs.VisitJob;
import primemover.runner.OperatorContext;
import primemover.runner.jobHandling.IJobHandler;
import primemover.util.NavigationHelper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class VisitJobHandler implements IJobHandler {

    @Autowired
	private CaptchaSolver captchaSolver;
    
	private static Logger logger = LoggerFactory.getLogger(VisitJobHandler.class);

    public void handle(OperatorContext context, IJob job) {
        boolean success = false;
        WebDriver driver = context.getDriver();
        VisitJob typedJob = (VisitJob) job;
        NavigationHelper navHelper = new NavigationHelper(driver, 2*60*1000l); // 2 Minuten
        
        logger.debug("running job");

        
        // captcha before
        CaptchaJobHelper.solveCaptcha(context, typedJob, JobPosition.BEFORE, captchaSolver);
        
        logger.debug("navigate to {} for job {}", typedJob.getUrl(), typedJob.getId());
        driver.navigate().to(typedJob.getUrl());
        
        // Wait for DOM-reload
        try {
            navHelper.waitForPageLoad();
        } catch (Exception e) {
            logger.debug("simple page load wait (DOM) failed for job {}.", typedJob.getId());
            // second chance below
        }
        
        // wait for pending javascript triggered loads
        try {
            primemover.util.Waiter.waitUntilJSReady(context.getDriver(), 5, 12); // check every 5 seconds for 1 Minutes
            success = true;
        } catch (Exception e) {
            logger.debug("page ready load wait failed for job {}.", typedJob.getId());
        }
        
        // captcha after
        CaptchaJobHelper.solveCaptcha(context, typedJob, JobPosition.AFTER, captchaSolver);
        
        if (!success) {
            logger.warn("Timeout visiting page {} for job {}",  typedJob.getUrl(), typedJob.getId());
            throw new TimeoutException("Timeout visiting page " + typedJob.getUrl() +  " for job " + typedJob.getId());
        }
        
    }
}
