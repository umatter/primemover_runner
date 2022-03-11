package primemover.runner.jobHandling;

import primemover.model.IJob;
import primemover.runner.OperatorContext;

public interface IJobHandler {
    void handle(OperatorContext context, IJob job);
}
