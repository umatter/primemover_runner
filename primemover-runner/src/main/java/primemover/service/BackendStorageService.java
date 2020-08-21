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

	private static SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss_");

	private void writeCookies(OutputStream os, JobData data) throws IOException {
		logger.debug("writting cookeis");
		
		Integer cnt = 0;

		StringBuilder tmp = new StringBuilder(1024);
		tmp.append("[");
		for (Cookie item : data.getCookies()) {

			if (cnt > 0) {
				tmp.append(",");
			}

			tmp.append("{");

			tmp.append("domain: '");
			tmp.append(item.getDomain());
			tmp.append("',");

			tmp.append("name: '");
			tmp.append(item.getName());
			tmp.append("',");

			tmp.append("path: '");
			tmp.append(item.getPath());
			tmp.append("',");

			tmp.append("value: '");
			tmp.append(item.getValue());
			tmp.append("',");

			tmp.append("expiry: '");
			tmp.append(item.getExpiry());
			tmp.append("'");

			tmp.append("}");

			cnt++;
		}
		tmp.append("]");

		os.write(tmp.toString().getBytes(StandardCharsets.UTF_8));
	}

	private Boolean writeJobDataAsZip(IJob job, JobData data) {
		logger.info("writting jobdata as Zip");
		
		// create filename
		String datePrefix = dateFormat.format(new Date());
		String jobId = job.getId().toString();
		String filename = datePrefix + "_jobdata_" + jobId + ".zip";

		// prepare streams
		try (ByteArrayOutputStream os = new ByteArrayOutputStream(4048);
				ZipOutputStream zipOut = new ZipOutputStream(os)) {

			// entries
			String traficData = datePrefix + "_trafic_har" + jobId + ".json";
			String pageSourceData = datePrefix + "_page_finalsource_" + jobId + ".html";
			String cookies = datePrefix + "_page_cookies_" + jobId + ".json";
			String localStorage = datePrefix + "_page_localstorage_" + jobId + ".json";
			String sessionStorage = datePrefix + "_page_sessionstorage_" + jobId + ".json";

			// trafic data
			ZipEntry zipEntry = new ZipEntry(traficData);
			zipOut.putNextEntry(zipEntry);
			// create temp stream as the json-library underlying the har-writing might close the stream
			ByteArrayOutputStream tmpOs = new ByteArrayOutputStream(4048);
			data.getHar().writeTo(tmpOs);
			zipOut.write(tmpOs.toByteArray());
			tmpOs.close();
			
			// Page Source
			zipEntry = new ZipEntry(pageSourceData);
			zipOut.putNextEntry(zipEntry);
			byte[] tmpByte = data.getPageSource().getBytes(StandardCharsets.UTF_8);
			zipOut.write(tmpByte);

			// Cookies
			zipEntry = new ZipEntry(cookies);
			zipOut.putNextEntry(zipEntry);
			writeCookies(zipOut, data);

			// local Storage
			zipEntry = new ZipEntry(localStorage);
			zipOut.putNextEntry(zipEntry);
			tmpByte = data.getLocalStorage().getBytes(StandardCharsets.UTF_8);
			zipOut.write(tmpByte);

			// session storage
			zipEntry = new ZipEntry(sessionStorage);
			zipOut.putNextEntry(zipEntry);
			tmpByte = data.getSessionStorage().getBytes(StandardCharsets.UTF_8);
			zipOut.write(tmpByte);
			
			// make sure everything is written
			zipOut.finish();
			zipOut.flush();
			
			byte[] bytesEncoded = Base64.getEncoder().encode(os.toByteArray());
			String content = new String(bytesEncoded);
			
			/*
			// create a folder for the job
			Path jobFolder = Paths.get(targetFolder + "/" + job.getId().toString() + "/");
			jobFolder = localDir.resolve(jobFolder);
			jobFolder.toFile().mkdirs();
			
			try (FileOutputStream fos = new FileOutputStream(jobFolder.toString() + "/" + filename)) {
				fos.write(os.toByteArray());
			}
			*/
			
			return crawlerService.writeJobData(job.getId(), filename, "recording results for job " + job.getId(), "application/zip", content);
			
		} catch (IOException e) {
			logger.warn("Error creating jobdata", e);
			return false;
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see primemover.service.IJobData#writeJobData(primemover.model.IJob,
	 * primemover.model.JobData)
	 */
	@Override
	public Boolean writeJobData(IJob job, JobData data) {
		return writeJobDataAsZip(job, data);
	}
}
