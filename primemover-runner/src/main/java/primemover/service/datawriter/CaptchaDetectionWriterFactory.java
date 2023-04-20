package primemover.service.datawriter;

import primemover.runner.RecordedJobData;

public class CaptchaDetectionWriterFactory implements IDataWriterFactory {

	@Override
	public IDataWriter getWriter(RecordedJobData data) {
		return new CaptchaDetectionWriter(data);
	}

}
