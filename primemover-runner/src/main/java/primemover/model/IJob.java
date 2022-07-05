package primemover.model;

import primemover.anticaptcha.CaptchaSolvingType;

public interface IJob {	
	Long getId();
	String getName();
	String getTask();
	String getDescription();
	CaptchaSolvingType getCaptchaSolving();
	Boolean isFailsave();
}
