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
import primemover.model.DecisionType;
import primemover.model.Joblist;
import primemover.model.MultiLoginAgent;
import primemover.model.SelectorType;
import primemover.model.TypingModeType;
import primemover.model.api.CrawlerResult;
import primemover.model.jobs.EnterTextFieldJob;
import primemover.model.jobs.SingleSelectUrlJob;
import primemover.model.jobs.VisitJob;
import primemover.model.jobs.WaitJob;

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
				//.withZoneSameLocal(now.getZone());	
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
	
	private void appendFakeJob(List<Pair<Crawler, Joblist>> inItems) {
		Crawler crawler = new Crawler();
		MultiLoginAgent agent = new MultiLoginAgent();
		agent.setId(1l);
		agent.setName("End2End - 00");
		agent.setMultiLoginId("c00b9234-3908-44b6-b8a9-40cf1cf24ee9");
		crawler.setAgent(agent);

		Joblist jobs = new Joblist();
		jobs.setId(-1l);
		jobs.setStartTime(ZonedDateTime.now().plusSeconds(30));
		
		// open page
		VisitJob visitJob = new VisitJob(1l, "visit google", "visit google", "https://www.google.ch");
		jobs.appendJob(visitJob);
		
		// enter search term
		EnterTextFieldJob enterJob = new EnterTextFieldJob(1l, "enter value to text field", "enter value to text field", SelectorType.XPATH, "//input[@name='q']", "Klimaerwärmung", TypingModeType.SIMULATED_KEEPINGTYPOS, true);
		jobs.appendJob(enterJob);
		
		// quick wait for search to finish
		WaitJob waitJob = new WaitJob(1l, "wait", "wait", 10);
		jobs.appendJob(waitJob);
			
		// select url
		SingleSelectUrlJob singleSelectUrlJob = new SingleSelectUrlJob(1l, "select random entry", "select first entry", SelectorType.XPATH, ".//div[@id='rso']/div/div/div/a", DecisionType.RANDOM);
		jobs.appendJob(singleSelectUrlJob);
		
		// wait for search result
		waitJob = new WaitJob(1l, "wait", "wait", 10);
		jobs.appendJob(waitJob);
		
		
		inItems.add(Pair.of(crawler, jobs));
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
			appendFakeJob(res);
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
