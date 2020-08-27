package primemover.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.Cookie;

import primemover.model.IJob;

public class StorageHelper {
	private static Logger logger = LogManager.getLogger(StorageHelper.class);

	public static void writeCookies(OutputStream os, JobData data) throws IOException {
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
	
	private static SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss_");

	public String getFilenameAsZip(IJob job) {
		return dateFormat.format(new Date()) + "_jobdata_" + job.getId().toString() + ".zip";
	}
	
	public Boolean writeJobDataAsZip(OutputStream os, IJob job, JobData data) {
		logger.info("writting jobdata as Zip");
		
		// create filename
		String datePrefix = dateFormat.format(new Date());
		String jobId = job.getId().toString();

		// prepare streams
		try (ZipOutputStream zipOut = new ZipOutputStream(os)) {

			// entries
			String traficData = datePrefix + "_trafic_har" + jobId + ".json";
			String pageSourceData = datePrefix + "_page_finalsource_" + jobId + ".html";
			String cookies = datePrefix + "_page_cookies_" + jobId + ".json";
			String localStorage = datePrefix + "_page_localstorage_" + jobId + ".json";
			String sessionStorage = datePrefix + "_page_sessionstorage_" + jobId + ".json";
			String screenshot = datePrefix + "_page_screenshot_" + jobId + ".png";

			// trafic data
			ZipEntry zipEntry = new ZipEntry(traficData);
			zipOut.putNextEntry(zipEntry);
			// create temp stream as the json-library underlying the har-writing might close the stream
			ByteArrayOutputStream tmpOs = new ByteArrayOutputStream(4048);
			data.getHar().writeTo(tmpOs);
			zipOut.write(tmpOs.toByteArray());
			tmpOs.close();
			tmpOs = null;
			
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
			
			// screenshot
			zipEntry = new ZipEntry(screenshot);
			zipOut.putNextEntry(zipEntry);
			zipOut.write(data.getScreenshot());
			
			// make sure everything is written
			zipOut.finish();
			zipOut.flush();
			
			return true;
		} catch (IOException e) {
			logger.warn("Error creating jobdata", e);
			return false;
		}
	}
	public StorageHelper() {

	}

}
