package primemover.service.datawriter;

import primemover.runner.RecordedJobData;

public class PageSourceWriterFactory implements IDataWriterFactory {

	@Override
	public IDataWriter getWriter(RecordedJobData data) {
		return new PageSourceWriter(data);
	}

}
