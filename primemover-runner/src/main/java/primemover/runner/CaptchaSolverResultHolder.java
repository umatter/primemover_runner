package primemover.runner;

import primemover.anticaptcha.CaptchaSolverResult;

import javax.annotation.Nullable;

public class CaptchaSolverResultHolder {
	
	@Nullable private CaptchaSolverResult beforeJob = null;
	@Nullable private CaptchaSolverResult afterJob = null;

	
	public CaptchaSolverResultHolder(@Nullable CaptchaSolverResult before, @Nullable CaptchaSolverResult after) {
		super();

		this.beforeJob = before;
		this.afterJob = after;
	}
	
	public CaptchaSolverResultHolder() {
		super();
	}
	
	
	public boolean hasAnyCaptchaResult() {
		return beforeJob != null || afterJob != null;
	}
	
	public boolean hasBeforeCaptchaResult() {
		return beforeJob != null;
	}
	
	public boolean hasAfterCaptchaResult() {
		return afterJob != null;
	}
	
	public boolean isAnyCaptchaDetected() {
		if ( beforeJob != null && beforeJob.isCaptchaDetected()) {
			return true;
		}
		
		if(afterJob != null && afterJob.isCaptchaDetected()) {
			return true;
		}
		
		return false;
	}
	
	public @Nullable  CaptchaSolverResult getBeforeJob() {
		return beforeJob;
	}
	
	public void setBeforeJob(@Nullable  CaptchaSolverResult beforeJob) {
		this.beforeJob = beforeJob;
	}
	
	public @Nullable  CaptchaSolverResult getAfterJob() {
		return afterJob;
	}
	
	public void setAfterJob(@Nullable CaptchaSolverResult afterJob) {
		this.afterJob = afterJob;
	}	
}
