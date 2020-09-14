package primemover.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import primemover.model.IJob;

public class BackendStorageService implements IStorageService {
	private static Logger logger = LoggerFactory.getLogger(BackendStorageService.class);
	
	
	@Autowired
	private ICrawlerService crawlerService;
	
	@Value("${export.Compression:ZIP}")
	private String compression;


	/*
	 * (non-Javadoc)
	 * 
	 * @see primemover.service.IJobData#writeJobData(primemover.model.IJob,
	 * primemover.model.JobData)
	 */
	@Override
	public Boolean writeJobData(IJob job, JobData data) {
		
		StorageHelper helper = new StorageHelper();
		
		try (ByteArrayOutputStream os = new ByteArrayOutputStream(4048)) {
			logger.trace("Start writing data for job " + job.getId().toString());
			if (helper.writeJobDataAsZip(os, job, data)) {
				byte[] bytesEncoded = Base64.getEncoder().encode(os.toByteArray());
				String content = new String(bytesEncoded);		
				
				Boolean ret = crawlerService.writeJobData(job.getId(), helper.getFilenameAsZip(job), "recording results for job " + job.getId(), "application/zip", content);
				logger.trace("Done writing data for job " + job.getId().toString());
				return ret;
			}
			
			} catch (IOException e) {
				logger.warn("Error writing data", e);
				return false;
			}
		
		logger.warn("Error writing data");
		return false;
	}
}
