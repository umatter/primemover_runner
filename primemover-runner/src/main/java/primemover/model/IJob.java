package primemover.model;

import primemover.captcha.CaptchaSolvingType;

public interface IJob {
	
	Long getId();
	String getName();
	String getDescription();
	CaptchaSolvingType getCaptchaSolving();
}
