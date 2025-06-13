package primemover.runner.job.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import primemover.anticaptcha.CaptchaJobHelper;
import primemover.anticaptcha.CaptchaJobHelper.JobPosition;
import primemover.model.jobs.WaitJob;
import primemover.runner.context.IJobAwareRunnerContext;
import primemover.runner.job.IJobHandler;

public class WaitJobHandler implements IJobHandler {
	private final static Logger LOGGER = LoggerFactory.getLogger(WaitJobHandler.class);

	@Override
	public void handle(IJobAwareRunnerContext<?> context) {
		LOGGER.debug("running job");

		WaitJob typedJob = (WaitJob) context.getJob();

		// captcha before
		CaptchaJobHelper.solveCaptcha(context, JobPosition.BEFORE);

		try {
			Thread.sleep(typedJob.getSeconds() * 1000);
		} catch (InterruptedException e) {
			// nothing to do
		}

		// captcha after
		CaptchaJobHelper.solveCaptcha(context, JobPosition.AFTER);
	}
}
