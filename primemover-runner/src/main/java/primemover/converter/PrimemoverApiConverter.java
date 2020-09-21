package primemover.converter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;
import org.openqa.selenium.InvalidArgumentException;
import org.springframework.util.StringUtils;

import primemover.model.AgentType;
import primemover.model.BasicAgent;
import primemover.model.Crawler;
import primemover.model.DecisionType;
import primemover.model.GeoSurfProxy;
import primemover.model.IAgent;
import primemover.model.IProxy;
import primemover.model.Joblist;
import primemover.model.MultiLoginAgent;
import primemover.model.ScrollDirection;
import primemover.model.SelectorType;
import primemover.model.TypingModeType;
import primemover.model.HttpProxy;
import primemover.model.api.CrawlerResult;
import primemover.model.jobs.BaseJob;
import primemover.model.jobs.EnterTextFieldJob;
import primemover.model.jobs.ScrollByDurationJob;
import primemover.model.jobs.ScrollByLengthJob;
import primemover.model.jobs.SingleSelectUrlJob;
import primemover.model.jobs.TryClickJob;
import primemover.model.jobs.VisitJob;
import primemover.model.jobs.WaitJob;
import primemover.model.IJob;

public class PrimemoverApiConverter {

	public PrimemoverApiConverter() {
	}
	
	private static IProxy convertApiProxy(primemover.model.api.Proxy item, Crawler crawler) {
		
		HttpProxy res;	
		
		String type = item.getType();
		if (StringUtils.isEmpty(type)) {
			res = new HttpProxy();
		} else {
			type = type.toLowerCase();
			if (type.contains("geosurf")) {
				GeoSurfProxy geoProxy = new GeoSurfProxy();
				geoProxy.setLocation(crawler.getAgent().getLocation());
				res = geoProxy;
				
			} else if (type.contains("http")) {
				res = new HttpProxy();
			} else {
				throw new InvalidArgumentException("Unknown proxy type");
			}
		}		
		
		if (StringUtils.isEmpty(item.getHostname())) {
			throw new InvalidArgumentException("Hostname must not be empty");
		}
		
		if (item.getPort() <= 0) {
			throw new InvalidArgumentException("No valid port: " + item.getPort());			
		}
		
		res.setUrl(item.getHostname());
		res.setPort(item.getPort());
		res.setUsername(item.getUsername());
		res.setPassword(item.getPassword());
	
		return res;
	}
	
	private static IAgent convertApiAgent(primemover.model.api.Agent item) {
		IAgent agent;
		
		String ident = item.getIdentification();
		
		if (StringUtils.isEmpty(ident)) {
			ident = "multilogin";
			
			// TODO reimplement
			// throw new InvalidArgumentException("Identification is required");
		}
		
		ident = ident.toLowerCase();
		
		if (ident.contains("firefox")) {
			agent = new BasicAgent(new Long(item.getId()), AgentType.FIREFOX, item.getName(), item.getDescription(), item.getLocation());
		} else if(ident.contains("chrome")) {
			agent = new BasicAgent(new Long(item.getId()), AgentType.CHROME, item.getName(), item.getDescription(), item.getLocation());
		} else if(ident.contains("multilogin") || ident.contains("multlogin")) {
			agent = new MultiLoginAgent(new Long(item.getId()), item.getName(), item.getDescription(), item.getMultilogin_id(), item.getLocation());
		} else {
			throw new InvalidArgumentException("No valid agent found");
		}
		
		return agent;
	}
	
	private static Map<String, String> convertApiBehaviourList(List<primemover.model.api.Behaviour> behaviours) {
		HashMap<String, String> map = new HashMap<String, String>();
		
		for (primemover.model.api.Behaviour item : behaviours) {
			String key = item.getName();
			String value = item.getValue();			
			map.put(key, value);
		}
		
		return map;
	}
		
	private static <JobType extends BaseJob> void setBaseFields(JobType job, Integer id, String name, String description) {
		job.setId(new Long(id));
		job.setName(name);
		job.setDescription(description);
	}
	
	private static VisitJob convertApiVisitJob(Integer id, String name, String description, Map<String, String> behaviours) {
		VisitJob job = new VisitJob();
		setBaseFields(job, id, name, description);
		job.setUrl(behaviours.get("url"));
	
		return job;
	}
	
	private static WaitJob convertApiWaitJob(Integer id, String name, String description, Map<String, String> behaviours) {
		WaitJob job = new WaitJob();
		setBaseFields(job, id, name, description);
		if (behaviours.containsKey("seconds")) {
			job.setSeconds(new Integer(behaviours.get("seconds")));
		} else {
			job.setSeconds(30);
		}		
	
		return job;
	}
	
	private static EnterTextFieldJob convertApiEnterTextFieldJob(Integer id, String name, String description, Map<String, String> behaviours) {
		EnterTextFieldJob job = new EnterTextFieldJob();
		setBaseFields(job, id, name, description);
		job.setType(SelectorType.valueOf(behaviours.get("selectionType")));
		job.setSelector(behaviours.get("selector"));
		job.setValue(behaviours.get("text"));
		
		// optional fields
		Boolean appendReturn = true;
		if (behaviours.containsKey("appendReturn")) {
			String val = behaviours.get("appendReturn");
			
			if ("1".equals(val)) {
				appendReturn = true;
			} else if ("0".equals(val)) {
				appendReturn = false;
			} else {
				appendReturn = Boolean.valueOf(val);	
			}
		}
		
		TypingModeType typingMode = TypingModeType.SIMULATED_NOTYPOS;
		if (behaviours.containsKey("typingMode")) {
			typingMode = TypingModeType.valueOf(behaviours.get("typingMode"));
		}
		
		job.setAppendReturn(appendReturn);
		job.setTypingMode(typingMode);		
	
		return job;
	}
	
	private static TryClickJob convertApiTryClickJob(Integer id, String name, String description, Map<String, String> behaviours) {
		TryClickJob job = new TryClickJob();
		setBaseFields(job, id, name, description);
		job.setType(SelectorType.valueOf(behaviours.get("selectionType")));
		job.setSelector(behaviours.get("selector"));
	
		return job;
	}
	
	
	private static SingleSelectUrlJob convertApiSingleSelectUrlJob(Integer id, String name, String description, Map<String, String> behaviours) {
		SingleSelectUrlJob job = new SingleSelectUrlJob();
		setBaseFields(job, id, name, description);
		job.setType(SelectorType.valueOf(behaviours.get("selectionType")));
		job.setSelector(behaviours.get("selector"));
		job.setDecisionType(DecisionType.valueOf(behaviours.get("decisionType")));
	
		return job;
	}
	
	private static ScrollByDurationJob convertApiScrollByDurationJob(Integer id, String name, String description, Map<String, String> behaviours) {
		ScrollByDurationJob job = new ScrollByDurationJob();
		setBaseFields(job, id, name, description);
		
		if (behaviours.containsKey("scrollDuration")) {
			job.setDuration(new Double(behaviours.get("scrollDuration")).intValue());
		} else if (behaviours.containsKey("duration")) {
			job.setDuration(new Double(behaviours.get("duration")).intValue());
		} else {
			throw new IllegalStateException("no duration or scrollDuration found");
		}
		job.setDirection(ScrollDirection.valueOf(behaviours.get("scrollDirection")));
	
		return job;
	}
	
	private static ScrollByLengthJob convertApiScrollByLengthJob(Integer id, String name, String description, Map<String, String> behaviours) {
		ScrollByLengthJob job = new ScrollByLengthJob();
		setBaseFields(job, id, name, description);
		job.setLength(new Integer(behaviours.get("length")));
		job.setDirection(ScrollDirection.valueOf(behaviours.get("scrollDirection")));
	
		return job;
	}
	
	private static IJob convertApiJob(primemover.model.api.Job job) {
		IJob res;
		
		// grab behavior list
		Map<String, String> behaviors = convertApiBehaviourList(job.getBehaviors());
		
		switch (job.getType().toLowerCase()) {
		case "visitjob":
			res = convertApiVisitJob(job.getId(), job.getName(), job.getDescription(), behaviors);
			break;
		case "waitjob":
			res = convertApiWaitJob(job.getId(), job.getName(), job.getDescription(), behaviors);
			break;						
		case "entertextfieldjob":
			res = convertApiEnterTextFieldJob(job.getId(), job.getName(), job.getDescription(), behaviors);
			break;
		case "singleselecturljob":
			res = convertApiSingleSelectUrlJob(job.getId(), job.getName(), job.getDescription(), behaviors);
			break;
		case "tryclickjob":
			res = convertApiTryClickJob(job.getId(), job.getName(), job.getDescription(), behaviors);
			break;
		case "scrollbydurationjob":
			res = convertApiScrollByDurationJob(job.getId(), job.getName(), job.getDescription(), behaviors);
			break;
		case "scrollbylengthjob":
			res = convertApiScrollByLengthJob(job.getId(), job.getName(), job.getDescription(), behaviors);
			break;
		default:
			throw new IllegalArgumentException("Invalid Job type found: " + job.getType());
		}		
				
		return res;
	}

	
	private static Joblist convertApiQueue(primemover.model.api.Queue item) {
		Joblist jobList = new Joblist();
		
		jobList.setId(new Long(item.getId()));
		jobList.setActive(item.getActive());
		jobList.setStartTime(item.getStart_at());
		
		List<primemover.model.api.Job> list = item.getJobs();
		
		if (list.isEmpty()) {
			throw new InvalidArgumentException("A joblist must contain at least one job.");
		}
		
		List<IJob> jobs = new ArrayList<IJob>();
		for (primemover.model.api.Job job : list) {
			jobs.add(convertApiJob(job));
		}
		jobList.setJobs(jobs);
		
		return jobList;
	}
	
	
	private static List<Pair<Crawler, Joblist>> convertApiCrawler(primemover.model.api.Crawler item) {
		
		List<Pair<Crawler, Joblist>> out = new ArrayList<Pair<Crawler, Joblist>>();
		
		// grab crawler
		Crawler crawler = new Crawler();
		
		crawler.setId(new Long(item.getId()));
		crawler.setName(item.getName());
		crawler.setDescription(item.getDescription());
		crawler.setAgent(convertApiAgent(item.getAgent()));
		crawler.setProxy(convertApiProxy(item.getProxy(), crawler)); // needs to go after agent as agent contains required location input
		
		// grab joblists
		List<primemover.model.api.Queue> queues = item.getQueues();
		
		for (primemover.model.api.Queue queue : queues) {
			Joblist jobList = convertApiQueue(queue);
			
			out.add(Pair.of(crawler, jobList));
		}
		
		return out;
	}
	
	public static List<Pair<Crawler, Joblist>> convertFromApi(CrawlerResult input) {
		List<Pair<Crawler, Joblist>> res = new ArrayList<Pair<Crawler, Joblist>>();
		List<primemover.model.api.Crawler> data = input.getData();
		
		if (!data.isEmpty()) {
			
			for (primemover.model.api.Crawler item : data) {
				res.addAll(convertApiCrawler(item));
			}
		}
		
		return res;
	}
	

}
