package primemover.service;

import primemover.model.IJob;

public interface IStorageService {

	Boolean writeJobData(IJob job, JobData data);

}