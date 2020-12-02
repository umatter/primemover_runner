package primemover.service;

import primemover.model.IJob;

public interface IStorageService {

	Boolean writeFixedJobData(IJob job, JobData data);

}