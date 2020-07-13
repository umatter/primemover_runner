package primemover.service;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.openqa.selenium.Cookie;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;

import primemover.model.IJob;

public class FileStorageService implements IStorageService {
	
	@Value("${export.FileTargetFolder}")
	private String targetFolder;
	private Path localDir = Paths.get(".");
	
	
	/* (non-Javadoc)
	 * @see primemover.service.IJobData#writeJobData(primemover.model.IJob, primemover.model.JobData)
	 */
	@Override
	public void writeJobData(IJob job, JobData data) {
		// create a folder for the job
		Path jobFolder = Paths.get(targetFolder + "/" + job.getId().toString() + "/");
		jobFolder = localDir.resolve(jobFolder);
		jobFolder.toFile().mkdirs();
		
		SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd_HHmmss_");
		String datePrefix = format.format(new Date()); 
		
		// write traffic data
		Path traficData = jobFolder.resolve(datePrefix + "page.trafic.har.json");
		Path pageSourceData = jobFolder.resolve(datePrefix + "page.source.html");
		Path cookies = jobFolder.resolve(datePrefix + "page.data.cookies.json");
		Path localStorage = jobFolder.resolve(datePrefix + "page.data.localstorage.json");
		Path sessionStorage = jobFolder.resolve(datePrefix + "page.data.sessionstorage.json");
		
		try (OutputStream os = new FileOutputStream(traficData.toFile()))
		{
			data.getHar().writeTo(os);
					
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		try (OutputStream os = new FileOutputStream(pageSourceData.toFile()))
		{
			byte[] tmp = data.getPageSource().getBytes(StandardCharsets.UTF_8);
			os.write(tmp);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		try (OutputStream os = new FileOutputStream(cookies.toFile()))
		{
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
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		try (OutputStream os = new FileOutputStream(localStorage.toFile()))
		{
			byte[] tmp = data.getLocalStorage().getBytes(StandardCharsets.UTF_8);
			os.write(tmp);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		try (OutputStream os = new FileOutputStream(sessionStorage.toFile()))
		{
			byte[] tmp = data.getSessionStorage().getBytes(StandardCharsets.UTF_8);
			os.write(tmp);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
