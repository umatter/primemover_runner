package primemover.runner;

import primemover.model.Crawler;
import primemover.model.Joblist;

public class JobListRunner implements Runnable  {
	ITaskOperator taskService;
	Crawler crawler;
	Joblist jobs;

	@Override
	public void run() {
		// get Taskservice and setup
		taskService.setup(crawler);
	
		// run jobs
		taskService.runJobList(jobs);
		
		// release resources
		taskService.tearDown();		
	}

	public JobListRunner(ITaskOperator taskService, Crawler crawler, Joblist jobs) {
		super();
		this.taskService = taskService;
		this.crawler = crawler;
		this.jobs = jobs;
	}
}
