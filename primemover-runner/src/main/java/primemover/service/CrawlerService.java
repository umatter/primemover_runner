package primemover.service;

import java.util.List;

import org.apache.commons.lang3.tuple.Pair;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import primemover.converter.PrimemoverApiConverter;
import primemover.model.Crawler;
import primemover.model.Joblist;
import primemover.model.api.CrawlerResult;

@Service
public class CrawlerService implements ICrawlerService {

	private static Logger logger = LogManager.getLogger(CrawlerService.class);
	
	public CrawlerService() {
		// nothing to do
	}

	@Autowired
	private RestTemplate restTemplate;
	
	/* (non-Javadoc)
	 * @see primemover.service.ICrawlerService#getCurrentTasks()
	 */
	@Override
	public List<Pair<Crawler, Joblist>> getCurrentTasks() {
		logger.debug("loading jobs");
		CrawlerResult apiRes = restTemplate.getForObject("https://siaw.primemover.example/api/v1/crawlers", CrawlerResult.class);
		return PrimemoverApiConverter.convertFromApi(apiRes);
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
