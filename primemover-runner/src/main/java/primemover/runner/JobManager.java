package primemover.runner;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;

import primemover.model.Agent;
import primemover.model.Agent.AgentType;
import primemover.model.Crawler;
import primemover.model.Joblist;
import primemover.model.VisitJob;
import primemover.service.ITaskService;
import primemover.service.TaskServiceFactory;

public class JobManager {
	@Value("${runner.MaxParallelExecution}")
	private Integer maxThreads; // maximum parallel executions of joblists
	
	private ExecutorService executorService;

	@Autowired
	TaskServiceFactory taskServiceFactory;
	
	public void setup() {
		executorService = Executors.newFixedThreadPool(maxThreads);
	}

	@Scheduled(fixedRate = 60000)
	public void run() {
		// Basic settings
		Agent agent = new Agent(1l, AgentType.CHROME, "Firefox", "Firefox");
		Crawler crawler = new Crawler();
		crawler.setAgent(agent);

		// job
		VisitJob job = new VisitJob(1l, "visit google", "visit google", "https://www.google.ch");
		Joblist jobs = new Joblist();
		jobs.appendJob(job);

		// running		
		JobListRunner runner = new JobListRunner(taskServiceFactory.getItem(), crawler, jobs);
		executorService.execute(runner);
	}

}
