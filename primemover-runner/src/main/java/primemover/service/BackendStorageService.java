package primemover.service;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.Cookie;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.MimeType;

import primemover.model.IJob;

public class BackendStorageService implements IStorageService {
	private static Logger logger = LogManager.getLogger(BackendStorageService.class);
	
	@Value("${export.FileTargetFolder}")
	private String targetFolder;
	private Path localDir = Paths.get(".");		
		

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
		
		/*
		// create a folder for the job
		Path jobFolder = Paths.get(targetFolder + "/" + job.getId().toString() + "/");
		jobFolder = localDir.resolve(jobFolder);
		jobFolder.toFile().mkdirs();
		
		try (FileOutputStream fos = new FileOutputStream(jobFolder.toString() + "/" + filename)) {
			fos.write(os.toByteArray());
		}
		*/
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
