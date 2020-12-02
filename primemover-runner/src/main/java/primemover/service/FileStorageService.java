package primemover.service;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import primemover.model.IJob;

public class FileStorageService implements IStorageService {
	private static Logger logger = LoggerFactory.getLogger(FileStorageService.class);
	
	
	@Value("${export.FileTargetFolder}")
	private String targetFolder;
	private Path localDir = Paths.get(".");
	
	
	/* (non-Javadoc)
	 * @see primemover.service.IJobData#writeJobData(primemover.model.IJob, primemover.model.JobData)
	 */
	@Override
	public Boolean writeFixedJobData(IJob job, JobData data) {
		
		
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

		//create a folder for the job
		Path jobFolder = Paths.get(targetFolder + "/" + job.getId().toString() + "/");
		jobFolder = localDir.resolve(jobFolder);
		jobFolder.toFile().mkdirs();
		
		
		// write traffic data
		Path filepath = jobFolder.resolve(helper.getFilenameAsZip(job));
		
		try (OutputStream os = new FileOutputStream(filepath.toFile())) {
			logger.trace("Start writing data for job " + job.getId().toString());
			if (helper.writeJobDataAsZip(os, job, data)) {	
				
				// done writing
				os.flush();
				
				logger.trace("Done writing data for job " + job.getId().toString());
				return true;
			}
			
			} catch (IOException e) {
				logger.warn("Error writing data", e);
				return false;
			}
		
		logger.warn("Error writing data");
		return false;
	}
}
