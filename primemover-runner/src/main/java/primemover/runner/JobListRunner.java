package primemover.runner;

import primemover.model.Crawler;
import primemover.model.Joblist;

public class JobListRunner implements Runnable  {
	ITaskOperator taskOperator;
	Crawler crawler;
	Joblist jobs;

	@Override
	public void run() {
		// get Taskservice and setup
		taskOperator.setup(crawler);
	
		// run jobs
		taskOperator.runJobList(jobs);
		
		// release resources
		taskOperator.tearDown();		
	}

	public JobListRunner(ITaskOperator taskService, Crawler crawler, Joblist jobs) {
		super();
		this.taskOperator = taskService;
		this.crawler = crawler;
		this.jobs = jobs;
	}
}
