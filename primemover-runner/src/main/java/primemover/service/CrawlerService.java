package primemover.service;

import java.time.ZonedDateTime;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import primemover.config.StartupMode;
import primemover.converter.PrimemoverApiConverter;
import primemover.model.Crawler;
import primemover.model.Joblist;
import primemover.model.api.CrawlerResult;

@Service
public class CrawlerService implements ICrawlerService {
	
	@Value("${global.mode:PRODUCTION}")
	private StartupMode mode;

	private static Logger logger = LoggerFactory.getLogger(CrawlerService.class);
	
	public CrawlerService() {
		// nothing to do
	}

	@Autowired
	private RestTemplate restTemplate;
	
	
	private String calculateCrawlerUrl(Long id) {
		String url;
		if (mode == StartupMode.PRODUCTION || id != null) {
			url = "https://siaw.primemover.example/api/v1/crawlers";
		} else {
			url = "https://siaw.primemover.example/api/v1/crawlers-testing";
		}
		
		if (id != null) {
			url = url + '/' + id.toString();
		}
		
		return url;
	}
	
	private ZonedDateTime adjustTimestamp(ZonedDateTime in) {
		ZonedDateTime now = ZonedDateTime.now();
		
		return in.withYear(now.getYear())
				.withMonth(now.getMonth().getValue())
				.withDayOfMonth(now.getDayOfMonth());	
	}
	
	private void adjustJobsToToday(List<Pair<Crawler, Joblist>> inItems) {
		// iterate items
		for (Pair<Crawler, Joblist> item : inItems) {
			
			// adjust joblist
			Joblist jobs = item.getRight();
			ZonedDateTime curStart = jobs.getStartTime();	
			jobs.setStartTime(adjustTimestamp(curStart));			
		}
	}
	
	/* (non-Javadoc)
	 * @see primemover.service.ICrawlerService#getCurrentTasks()
	 */
	@Override
	public List<Pair<Crawler, Joblist>> getCurrentTasks() {		
		logger.debug("loading jobs");
		CrawlerResult apiRes = restTemplate.getForObject(calculateCrawlerUrl(null), CrawlerResult.class);		
		List<Pair<Crawler, Joblist>> res = PrimemoverApiConverter.convertFromApi(apiRes);
		
		if (mode != StartupMode.PRODUCTION) {
			adjustJobsToToday(res);
		}
		
		return res;
	}
	
	@Override
	public List<Pair<Crawler, Joblist>> getTasksForCrawler(Long id) {
		logger.debug("loading jobs");
		CrawlerResult apiRes = restTemplate.getForObject(calculateCrawlerUrl(id), CrawlerResult.class);
		List<Pair<Crawler, Joblist>> res = PrimemoverApiConverter.convertFromApi(apiRes);
		
		if (mode != StartupMode.PRODUCTION) {
			adjustJobsToToday(res);
		}
		
		return res;
	}
	
	
	public Boolean writeJobData(Long jobId, String filename, String description, String mimeType, String fileContent) {
		HttpHeaders headers = new HttpHeaders();
	    headers.setContentType(MediaType.APPLICATION_JSON);
	    JSONObject data = new JSONObject();
	    data.put("job_id", jobId);
	    data.put("description", description);
	    data.put("filecontent", fileContent);
	    data.put("mimetype", mimeType);
	    data.put("filename", filename);
	    
	    HttpEntity<String> request = new HttpEntity<String>(data.toString(), headers);
	  	   
	    try {
	    	
	    	ResponseEntity<String> response = restTemplate.postForEntity("https://siaw.primemover.example/api/v1/reports", request, String.class);
		    
	    	 if (response.getStatusCode().is2xxSuccessful()) {
	 	    	logger.info("stored data to server for job " + jobId);
	 	    	return true;
	 	    } else {
	 	    	logger.warn("failure storing data to server for job " + jobId);
	 	    	return false;
	 	    }	    
	    } catch (ResourceAccessException ex) {
	    	logger.warn("failure storing data to server for job " + jobId, ex);
	    	return false;
	    }
	}	
}
