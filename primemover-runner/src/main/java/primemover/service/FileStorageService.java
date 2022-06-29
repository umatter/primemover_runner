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
import primemover.service.datawriter.IDataWriter;
import primemover.service.datawriter.StorageUtility;

public class FileStorageService implements IStorageService {
	private static Logger logger = LoggerFactory.getLogger(FileStorageService.class);
	
	
	@Value("${export.FileTargetFolder}")
	private String targetFolder;
	private Path localDir = Paths.get(".");
	
	@Override
	public Boolean writeData(IJob job, IDataWriter writer) {
		//create a folder for the job
		Path jobFolder = Paths.get(targetFolder + "/" + job.getId().toString() + "/");
		jobFolder = localDir.resolve(jobFolder);
		jobFolder.toFile().mkdirs();
		
		// write traffic data
		Path filepath = jobFolder.resolve(writer.getFileName());
		
		try (OutputStream os = new FileOutputStream(filepath.toFile())) {
			logger.trace("Start writing data for job {}", job.getId());
			if (writer.writeData(os) ) {
				// done writing
				os.flush();
				
				logger.trace("Done writing data for job {}", job.getId());
				return true;
			}
			
			} catch (IOException e) {
				logger.warn("Error writing data for job {}", job.getId(), e);
				return false;
			}
		
		logger.warn("Error writing data for job {}", job.getId());
		return false;
	}
}
