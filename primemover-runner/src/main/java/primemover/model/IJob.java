package primemover.model;

import primemover.captcha.CaptchaSolvingType;

public interface IJob {	
	Long getId();
	String getName();
	String getTask();
	String getDescription();
	CaptchaSolvingType getCaptchaSolving();
	Boolean isFailsave();
}
