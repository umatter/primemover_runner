package primemover.service;

import primemover.model.IJob;
import primemover.service.jobdatawriter.IJobDataWriter;

public interface IStorageService {

	Boolean writeFixedJobData(IJob job, JobData data);
	
	Boolean writeData(IJob job, IJobDataWriter writer);
}