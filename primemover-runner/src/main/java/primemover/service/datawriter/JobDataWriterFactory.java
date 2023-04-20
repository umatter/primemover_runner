package primemover.service.datawriter;

import primemover.runner.RecordedJobData;

public class JobDataWriterFactory implements IDataWriterFactory {

	@Override
	public IDataWriter getWriter(RecordedJobData data) {
		return new JobDataWriter(data);
	}
}
