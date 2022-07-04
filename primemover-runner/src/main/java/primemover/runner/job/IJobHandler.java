package primemover.runner.job;

import primemover.model.IJob;
import primemover.runner.context.ContextBase;

public interface IJobHandler {
    void handle(ContextBase context, IJob job);
}
