package primemover.runner.jobs.handler;

import primemover.captcha.CaptchaJobHelper;
import primemover.captcha.CaptchaSolver;
import primemover.captcha.CaptchaJobHelper.JobPosition;
import primemover.model.IJob;
import primemover.model.jobs.WaitJob;
import primemover.runner.context.ContextBase;
import primemover.runner.jobs.IJobHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class WaitJobHandler implements IJobHandler {

    @Autowired
	private CaptchaSolver captchaSolver;
    
	private static Logger logger = LoggerFactory.getLogger(WaitJobHandler.class);

    public void handle(ContextBase context, IJob job) {
        logger.debug("running job");

        WaitJob typedJob = (WaitJob) job;
        
        // captcha before
        CaptchaJobHelper.solveCaptcha(context, typedJob, JobPosition.BEFORE, captchaSolver);
        
        try {
            Thread.sleep(typedJob.getSeconds() * 1000);
        } catch (InterruptedException e) {
            // nothing to do
        }
        
        // captcha after
        CaptchaJobHelper.solveCaptcha(context, typedJob, JobPosition.AFTER, captchaSolver);
    }
}
