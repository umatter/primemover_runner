package primemover.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Retryable;
import org.springframework.retry.support.RetrySynchronizationManager;
import primemover.model.IJob;
import primemover.service.datawriter.IDataWriter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

public class BackendStorageService implements IStorageService {
	private static Logger logger = LoggerFactory.getLogger(BackendStorageService.class);

	@Autowired
	private ICrawlerService crawlerService;

	@Value("${export.Compression:ZIP}")
	private String compression;

	@Retryable
	@Override
	public Boolean writeData(IJob job, IDataWriter writer) {
		try (ByteArrayOutputStream os = new ByteArrayOutputStream(4048)) {

			logger.trace("Start writing data for job {}. Retry {}", job.getId(),
					RetrySynchronizationManager.getContext().getRetryCount());

			if (writer.writeData(os)) {
				byte[] bytesEncoded = Base64.getEncoder().encode(os.toByteArray());
				String content = new String(bytesEncoded);

				Boolean ret = crawlerService.writeJobData(job.getId(), writer.getFileName(), writer.getDescription(),
						writer.getContentType(), content);
				logger.trace("Done writing data for job {}", job.getId());
				return ret;
			}

		} catch (IOException e) {
			logger.warn("Error writing data for job {}", job.getId(), e);
			return false;
		}

		logger.warn("Error writing data");
		return false;
	}
}
