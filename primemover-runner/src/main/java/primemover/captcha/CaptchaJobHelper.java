package primemover.captcha;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import primemover.model.IJob;
import primemover.runner.OperatorContext;

public class CaptchaJobHelper {
	private static Logger logger = LoggerFactory.getLogger(CaptchaJobHelper.class);
	

	public CaptchaJobHelper() {
	}
	
	public enum JobPosition {
		BEFORE,
		AFTER
	}
	
	public static Optional<CaptchaSolverResult> solveCaptcha(OperatorContext context, IJob job, JobPosition position, CaptchaSolver solver) {

		if (job.getCaptchaSolving() == CaptchaSolvingType.NEVER) {
			logger.debug("not running captcha (NEVER) for job {}", job.getId());
			return Optional.empty();
		}
		
		switch (position) {
		case AFTER:
			if (job.getCaptchaSolving() == CaptchaSolvingType.ALWAYS) {
				logger.debug("running captcha (AFTER) for job {}", job.getId());
				return Optional.of(solver.solve(context, job, true));
			}
			
			if (job.getCaptchaSolving() == CaptchaSolvingType.AFTER) {
				logger.debug("running captcha (AFTER) for job {}", job.getId());
				return Optional.of(solver.solve(context, job, true));
			}
			
			break;
		case BEFORE:
			if (job.getCaptchaSolving() == CaptchaSolvingType.ALWAYS) {
				logger.debug("running captcha (BEFORE) for job {}", job.getId());
				return Optional.of(solver.solve(context, job, true));
			}
			
			if (job.getCaptchaSolving() == CaptchaSolvingType.BEFORE) {
				logger.debug("running captcha (BEFORE) for job {}", job.getId());
				return Optional.of(solver.solve(context, job, true));
			}
			break;
		}
				
		return Optional.empty();
	}
}
