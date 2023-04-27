package primemover.runner.job;

import primemover.runner.context.IJobAwareRunnerContext;

public interface IJobHandler {
	void handle(IJobAwareRunnerContext<?> context);
}
