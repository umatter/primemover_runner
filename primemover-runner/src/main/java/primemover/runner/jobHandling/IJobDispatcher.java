package primemover.runner.jobHandling;

import primemover.model.IJob;
import primemover.runner.OperatorContext;

public interface IJobDispatcher {

	public void handle(OperatorContext context, IJob job) throws UnsupportedOperationException;
	public void setup();
}