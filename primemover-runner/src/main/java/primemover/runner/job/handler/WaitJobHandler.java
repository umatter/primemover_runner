package primemover.runner.job.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import primemover.anticaptcha.CaptchaJobHelper;
import primemover.anticaptcha.CaptchaJobHelper.JobPosition;
import primemover.model.IJob;
import primemover.model.jobs.WaitJob;
import primemover.runner.context.ContextBase;
import primemover.runner.job.IJobHandler;

public class WaitJobHandler implements IJobHandler {
	private final static Logger LOGGER = LoggerFactory.getLogger(WaitJobHandler.class);

	public void handle(ContextBase<?> context, IJob job) {
		LOGGER.debug("running job");

		WaitJob typedJob = (WaitJob) job;

		// captcha before
		CaptchaJobHelper.solveCaptcha(context, typedJob, JobPosition.BEFORE);

		try {
			Thread.sleep(typedJob.getSeconds() * 1000);
		} catch (InterruptedException e) {
			// nothing to do
		}

		// captcha after
		CaptchaJobHelper.solveCaptcha(context, typedJob, JobPosition.AFTER);
	}
}
