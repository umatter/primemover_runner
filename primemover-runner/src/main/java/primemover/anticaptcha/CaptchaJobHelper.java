package primemover.anticaptcha;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import primemover.model.IJob;
import primemover.runner.context.IJobAwareRunnerContext;

public class CaptchaJobHelper {
	private static Logger logger = LoggerFactory.getLogger(CaptchaJobHelper.class);

	private CaptchaJobHelper() {
	}

	public enum JobPosition {
		BEFORE, AFTER
	}

	public static Optional<CaptchaSolverResult> solveCaptcha(IJobAwareRunnerContext<?> context, JobPosition position) {

		IJob job = context.getJob();
		CaptchaSolvingType solvingType = job.getCaptchaSolving();

		if (solvingType == CaptchaSolvingType.NEVER) {
			logger.debug("not running captcha (NEVER) for job {}", job.getId());
			return Optional.empty();
		}

		switch (position) {
		case AFTER:
			if (solvingType == CaptchaSolvingType.ALWAYS || solvingType == CaptchaSolvingType.AFTER) {
				logger.debug("running captcha (AFTER) for job {}", job.getId());
				Optional<CaptchaSolverResult> afterResult = Optional
						.of(context.getCaptchaSolver().solve(context, job, true));
				context.getJobDataRecorder().setCapchaResultAfter(afterResult.orElse(null));
				return afterResult;
			}

			break;
		case BEFORE:
			if (solvingType == CaptchaSolvingType.ALWAYS || solvingType == CaptchaSolvingType.BEFORE) {
				logger.debug("running captcha (BEFORE) for job {}", job.getId());
				Optional<CaptchaSolverResult> beforeResult = Optional
						.of(context.getCaptchaSolver().solve(context, job, true));
				context.getJobDataRecorder().setCapchaResultBefore(beforeResult.orElse(null));
				return beforeResult;
			}
			break;
		}

		return Optional.empty();
	}
}
