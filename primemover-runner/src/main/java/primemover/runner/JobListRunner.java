package primemover.runner;

import primemover.model.Crawler;
import primemover.model.Joblist;
import primemover.service.ITaskService;
import primemover.service.MockTaskService;
import primemover.service.TaskServiceFactory;

public class JobListRunner implements Runnable  {
	ITaskService taskService;
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

	public JobListRunner(ITaskService taskService, Crawler crawler, Joblist jobs) {
		super();
		this.taskService = taskService;
		this.crawler = crawler;
		this.jobs = jobs;
	}
}
