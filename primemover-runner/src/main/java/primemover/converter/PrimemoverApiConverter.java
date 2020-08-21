package primemover.converter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;
import org.openqa.selenium.InvalidArgumentException;
import org.springframework.util.StringUtils;

import primemover.model.AgentType;
import primemover.model.BaseJob;
import primemover.model.BasicAgent;
import primemover.model.Crawler;
import primemover.model.DecisionType;
import primemover.model.EnterTextFieldJob;
import primemover.model.IAgent;
import primemover.model.IProxy;
import primemover.model.Joblist;
import primemover.model.MultiLoginAgent;
import primemover.model.SelectorType;
import primemover.model.SimpleProxy;
import primemover.model.SingleSelectUrlJob;
import primemover.model.VisitJob;
import primemover.model.WaitJob;
import primemover.model.api.CrawlerResult;
import primemover.model.api.Job;
import primemover.model.IJob;

public class PrimemoverApiConverter {

	public PrimemoverApiConverter() {
	}
	
	private static IProxy convertApiProxy(primemover.model.api.Proxy item) {
		SimpleProxy res = new SimpleProxy();
		
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
		String multiloginId = item.getMultilogin_id();
		
		if (StringUtils.isEmpty(multiloginId)) {
			if (StringUtils.isEmpty(ident) ) {
				throw new InvalidArgumentException("Emtpy string for agent found");
			} else {
				
				if (ident.contains("Firefox")) {
					agent = new BasicAgent(new Long(item.getId()), AgentType.FIREFOX, item.getName(), item.getDescription());
				} else if(ident.contains("Chrom")) {
					agent = new BasicAgent(new Long(item.getId()), AgentType.CHROME, item.getName(), item.getDescription());
				} else {
					throw new InvalidArgumentException("No valid agent found");
				}
			}
		} else {
			agent = new MultiLoginAgent(new Long(item.getId()), item.getName(), item.getDescription(), item.getMultilogin_id());
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
		job.setSeconds(new Integer(behaviours.get("seconds")));
	
		return job;
	}
	
	private static EnterTextFieldJob convertApiEnterTextFieldJob(Integer id, String name, String description, Map<String, String> behaviours) {
		EnterTextFieldJob job = new EnterTextFieldJob();
		setBaseFields(job, id, name, description);
		job.setType(SelectorType.valueOf(behaviours.get("selectionType")));
		job.setSelector(behaviours.get("selector"));
		job.setValue(behaviours.get("text"));
	
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
	
	private static IJob convertApiJob(primemover.model.api.Job job) {
		IJob res;
		
		// grab behaviour list
		Map<String, String> behaviours = convertApiBehaviourList(job.getBehaviors());
		
		switch (job.getType().toLowerCase()) {
		case "visitjob":
			res = convertApiVisitJob(job.getId(), job.getName(), job.getDescription(), behaviours);
			break;
		case "waitjob":
			res = convertApiWaitJob(job.getId(), job.getName(), job.getDescription(), behaviours);
			break;						
		case "entertextfieldjob":
			res = convertApiEnterTextFieldJob(job.getId(), job.getName(), job.getDescription(), behaviours);
			break;
		case "singleselecturljob":
			res = convertApiSingleSelectUrlJob(job.getId(), job.getName(), job.getDescription(), behaviours);
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
		crawler.setProxy(convertApiProxy(item.getProxy()));
		
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
