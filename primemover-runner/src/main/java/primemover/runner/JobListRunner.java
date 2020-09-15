package primemover.runner;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import primemover.model.Crawler;
import primemover.model.Joblist;

public class JobListRunner implements Callable<Boolean> {
	private static Logger logger = LoggerFactory.getLogger(JobListRunner.class);
	
	private ITaskOperator taskOperator;
	private Crawler crawler;
	private Joblist jobs;
	
	@Value("${runner.MaxRedoMinutes:15}")
	private Long redoMinutes = 15l;

	@Override
	public Boolean call() {
		// Check for max delay
		long diff = ChronoUnit.MINUTES.between(jobs.getStartTime(), ZonedDateTime.now());
		
		if (diff > redoMinutes) {
			logger.info("skipping run as the jobs timed out after {} minutes. Max delay is {} minutes", diff, redoMinutes);
			return false;
		}
		
		// running
		Boolean res = false;
		
		try {
			logger.info("setup crawler for {} ({}) with delay of {} minutes", crawler.getId(), crawler.getName(), diff);
			
			// get Taskservice and setup
			if (taskOperator.setup(crawler)) {
				
				logger.trace("running jobs for {} ({})", crawler.getId(), crawler.getName());
				// run jobs
				if (taskOperator.runJobList(jobs)) {
					logger.trace("success running jobs for {} ({})", crawler.getId(), crawler.getName());
				} else {
					logger.trace("failure running jobs for {} ({})", crawler.getId(), crawler.getName());
				};
			};
			
			logger.trace("done running jobs for {} ({})", crawler.getId(), crawler.getName());
			
			// release resources
			logger.trace("tearing down crawler for {} ({})", crawler.getId(), crawler.getName());
			taskOperator.tearDown();
			
			res = true;
		} catch (Exception e) {
			logger.warn("uncaught error during execution of joblist for {} ({})", crawler.getId(), crawler.getName(), e);
		}
		

		return res;
	}

	public JobListRunner(ITaskOperator taskService, Crawler crawler, Joblist jobs) {
		super();
		this.taskOperator = taskService;
		this.crawler = crawler;
		this.jobs = jobs;
	}
}
