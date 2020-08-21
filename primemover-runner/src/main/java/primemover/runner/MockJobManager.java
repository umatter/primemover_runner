package primemover.runner;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.beans.factory.annotation.Autowired;

import primemover.model.Crawler;
import primemover.model.Joblist;

public class MockJobManager implements IJobManager {

	private Integer maxThreads = 2; // maximum parallel executions of joblists
	
	private ExecutorService executorService;
	
	private List<JobListRunner> runners = new ArrayList<JobListRunner>();

	@Autowired
	TaskOperatorFactory taskServiceFactory;
	
	public MockJobManager() {
	}

	@Override
	public void setup() {
		executorService = Executors.newFixedThreadPool(maxThreads);
	}
	

	public void addTask (Crawler crawler, Joblist jobs) {
		JobListRunner runner = new JobListRunner(taskServiceFactory.getItem(), crawler, jobs);
		runners.add(runner);
	}
	
	@Override
	public void run() {
		for (JobListRunner job : runners) {
			executorService.execute(job);
		}
	}

}
