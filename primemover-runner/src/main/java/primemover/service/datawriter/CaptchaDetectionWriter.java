package primemover.service.datawriter;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import primemover.runner.RecordedJobData;

public class CaptchaDetectionWriter extends RecordedJobDataBaseWriter<RecordedJobData> {

	private final static Logger logger = LoggerFactory.getLogger(CaptchaDetectionWriter.class);

	public CaptchaDetectionWriter(RecordedJobData data) {
		super(data, "static-captcha", "json", "application/json", "captcha data stored as json");
	}

	@Override
	public boolean writeData(OutputStream os) {
		RecordedJobData data = getData();
		logger.info("writting captcha data as JSON for job {}", data.getJob().getId());

		if (!data.hasAnyCaptchaResult()) {
			return false;
		}

		try {
			// json mapper
			ObjectMapper mapper = new ObjectMapper();
			String json = mapper.writeValueAsString(data);
			byte[] tmpByte = data.getPageSource().getBytes(StandardCharsets.UTF_8);
			os.write(tmpByte);
			return true;

		} catch (Exception ex) {
			logger.warn("Error writing captcha data", ex);
			return false;
		}
	}

	@Override
	public boolean hasOutput() {
		if (!getData().hasAnyCaptchaResult()) {
			return false;
		}

		return getData().getCaptchaResults().isAnyCaptchaDetected();
	}
}
