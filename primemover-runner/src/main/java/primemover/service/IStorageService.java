package primemover.service;

import primemover.model.IJob;
import primemover.service.datawriter.IDataWriter;

public interface IStorageService {
	Boolean writeData(IJob job, IDataWriter writer);
}