package primemover.service;

import primemover.model.IJob;

public interface IStorageService {

	void writeJobData(IJob job, JobData data);

}