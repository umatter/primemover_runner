package primemover.model;

import primemover.anticaptcha.CaptchaSolvingType;

public interface IJob {
	static final boolean FAILSAVE_DEFAULT = false;
	static final boolean RESET_PREVOUS_FAILURE_DEFAULT = false;

	Long getId();

	String getName();

	String getTask();

	String getDescription();

	CaptchaSolvingType getCaptchaSolving();

	default boolean isFailsave() {
		return FAILSAVE_DEFAULT;
	}

	default boolean resetPreviousFailure() {
		return RESET_PREVOUS_FAILURE_DEFAULT;
	};
}
