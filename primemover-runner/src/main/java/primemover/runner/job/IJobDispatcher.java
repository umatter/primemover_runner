package primemover.runner.job;

import primemover.runner.context.IJobAwareRunnerContext;

public interface IJobDispatcher {

	public void handle(IJobAwareRunnerContext<?> context) throws UnsupportedOperationException;
	public void setup();
}