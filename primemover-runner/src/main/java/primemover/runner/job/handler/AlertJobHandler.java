package primemover.runner.job.handler;

import primemover.anticaptcha.CaptchaJobHelper;
import primemover.anticaptcha.CaptchaSolver;
import primemover.anticaptcha.CaptchaJobHelper.JobPosition;
import primemover.model.IJob;
import primemover.model.jobs.HandleAlertJob;
import primemover.runner.context.ContextBase;
import primemover.runner.job.IJobHandler;

import org.openqa.selenium.Alert;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class AlertJobHandler implements IJobHandler {

    @Autowired
	private CaptchaSolver captchaSolver;
    
	private static Logger logger = LoggerFactory.getLogger(AlertJobHandler.class);

    public void handle(ContextBase context, IJob job) {
        logger.debug("running job");

        WebDriver driver = context.getDriver();
        HandleAlertJob typedJob = (HandleAlertJob) job;
        
        // captcha before
        CaptchaJobHelper.solveCaptcha(context, typedJob, JobPosition.BEFORE, captchaSolver);
        
        // Wait for Element
        WebDriverWait wait = new WebDriverWait(driver, 5);
        Alert dialog = wait.until(ExpectedConditions.alertIsPresent());

        switch (typedJob.getAction()) {
        case ACCEPT:
            dialog.accept();
            break;
        case REJECT:
            dialog.dismiss();
            break;
        default:
            throw new UnsupportedOperationException("Not implemented yet");
        }
        
        // captcha after
        CaptchaJobHelper.solveCaptcha(context, typedJob, JobPosition.AFTER, captchaSolver);
    }
}
