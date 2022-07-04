package primemover.runner.jobs;

import primemover.model.IJob;
import primemover.runner.context.ContextBase;

public interface IJobDispatcher {

	public void handle(ContextBase context, IJob job) throws UnsupportedOperationException;
	public void setup();
}