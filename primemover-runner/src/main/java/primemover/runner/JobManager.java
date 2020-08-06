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
import primemover.model.DecisionType;
import primemover.model.EnterTextField;
import primemover.model.Joblist;
import primemover.model.ScrollDirection;
import primemover.model.ScrollByLengthJob;
import primemover.model.SelectorBaseJob;
import primemover.model.SelectorType;
import primemover.model.SingleSelectUrl;
import primemover.model.VisitJob;
import primemover.model.WaitJob;
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

	@Scheduled(fixedRate = 90000)
	public void run() {
		// Basic settings
		Agent agent = new Agent(1l, AgentType.CHROME, "Chrome", "Chrome");
		Crawler crawler = new Crawler();
		crawler.setAgent(agent);

		Joblist jobs = new Joblist();
		
		// job
		
		VisitJob visitJob = new VisitJob(1l, "visit foxnews", "visit foxnews", "https://www.foxnews.com/");
		//jobs.appendJob(visitJob);
		
		WaitJob waitJob = new WaitJob(1l, "wait", "wait", 15);
		//jobs.appendJob(waitJob);
			
		visitJob = new VisitJob(1l, "visit google", "visit google", "https://www.google.ch");
		jobs.appendJob(visitJob);
		
		SelectorBaseJob enterJob = new EnterTextField(1l, "enter value to text field", "enter value to text field", SelectorType.XPATH, "//input[@name='q']", "Klimaerwärmung");
		jobs.appendJob(enterJob);
		
		waitJob = new WaitJob(1l, "wait", "wait", 10);
		jobs.appendJob(waitJob);
				
		SingleSelectUrl singleSelectUrlJob = new SingleSelectUrl(1l, "select first entry", "select first entry", SelectorType.XPATH, ".//div[@id='rso']/div/div/div/a", DecisionType.FIRST);
		jobs.appendJob(singleSelectUrlJob);
		
		waitJob = new WaitJob(1l, "wait", "wait", 10);
		jobs.appendJob(waitJob);
		
		visitJob = new VisitJob(1l, "visit nzz.ch", "visit google", "https://www.nzz.ch");
		jobs.appendJob(visitJob);
		
		waitJob = new WaitJob(1l, "wait", "wait", 5);
		jobs.appendJob(waitJob);
		
		ScrollByLengthJob scrollJob = new ScrollByLengthJob(1l, "scroll", "scroll 50% down", ScrollDirection.DOWN, 60);
		jobs.appendJob(scrollJob);
		
		scrollJob = new ScrollByLengthJob(1l, "scroll", "scroll 15% up", ScrollDirection.UP, 15);
		jobs.appendJob(scrollJob);
		
		waitJob = new WaitJob(1l, "wait", "wait", 10);
		jobs.appendJob(waitJob);
		
		// running		
		JobListRunner runner = new JobListRunner(taskServiceFactory.getItem(), crawler, jobs);
		executorService.execute(runner);
	}

}
