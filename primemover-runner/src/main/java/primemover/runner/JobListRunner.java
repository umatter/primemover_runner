package primemover.runner;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import primemover.model.Crawler;
import primemover.model.Joblist;
import primemover.model.JoblistState;
import primemover.service.JobService;

/** 
 * Runs joblists on the specified crawler
 * 
 * @author dburkolter
 *
 */
public class JobListRunner implements Callable<Boolean> {
	private static Logger logger = LoggerFactory.getLogger(JobListRunner.class);
	
	private ITaskOperator taskOperator;
	private Crawler crawler;
	private Joblist jobs;
	
	@Value("${runner.MaxRedoMinutes:45}")
	private Long redoMinutes = 45l;

	@Autowired
	private JobService jobService;
	
	@Override
	public Boolean call() {
		// Check for max delay
		long diff = ChronoUnit.MINUTES.between(jobs.getStartTime(), ZonedDateTime.now());
		
		if (diff > redoMinutes) {
			logger.info("skipping run as the jobs timed out after {} minutes. Max delay is {} minutes", diff, redoMinutes);
			jobService.updateJoblistState(jobs, JoblistState.SKIPPED_TIMEDOUT, ZonedDateTime.now(), null, Boolean.TRUE, null); // set execution time
			return false;
		}
		
		// running
		boolean setupRes = false;
		boolean jobsRes = false;
		
		try {			
			
			logger.info("setup crawler for {} ({}) with delay of {} minutes", crawler.getId(), crawler.getName(), diff);
			jobService.updateJoblistState(jobs, JoblistState.RUNNING_INPROGRESS, ZonedDateTime.now(), null, null, Boolean.TRUE); // set start time and processing, commit later
			
			// get Taskservice and setup
			setupRes = taskOperator.setup(crawler);
			if (setupRes) {
				jobService.updateJoblistState(jobs, JoblistState.RUNNING_INITDONE, null, null, null, null);
				logger.trace("running jobs for {} ({})", crawler.getId(), crawler.getName());
				// run jobs
				jobsRes = taskOperator.runJobList(jobs);
				if (jobsRes) {
					logger.trace("success running jobs for {} ({})", crawler.getId(), crawler.getName());
					jobService.updateJoblistState(jobs, JoblistState.RUNNING_JOBSDONE, null, null, null, null);
				} else {
					logger.trace("failure running jobs for {} ({})", crawler.getId(), crawler.getName());

					jobService.updateJoblistState(jobs, JoblistState.RUNNING_JOBSFAILURE, null, null, null, null);
				};
				
				
			} else {
				logger.warn("failure running jobs for {} ({})", crawler.getId(), crawler.getName());
				jobService.updateJoblistState(jobs, JoblistState.RUNNING_INITFAILED, null, null, null, null);
			};	
			
		} catch (Exception e) {
			logger.warn("uncaught error during execution of joblist {} for crawler {}", crawler.getName(), crawler.getId(), e);
			jobService.updateJoblistState(jobs, JoblistState.DONE_FAILURE, null, ZonedDateTime.now(), Boolean.TRUE, Boolean.FALSE);
			
			// make sure to tear down!
			logger.trace("tearing down crawler for {} ({})", crawler.getId(), crawler.getName());
			taskOperator.tearDown(false);
			
			return false; // exit due to error
		}
			
			
			
		// release resources
		logger.trace("tearing down crawler for {} ({})", crawler.getId(), crawler.getName());
		taskOperator.tearDown(false);
		
		// set end-state and time
		if (setupRes) {
			if (jobsRes) {
				jobService.updateJoblistState(jobs, JoblistState.DONE_SUCCESS, null, ZonedDateTime.now(), Boolean.TRUE, Boolean.FALSE);
			} else {
				jobService.updateJoblistState(jobs, JoblistState.DONE_JOBSFAILURE, null, ZonedDateTime.now(), Boolean.TRUE, Boolean.FALSE);
			}
		} else {
			jobService.updateJoblistState(jobs, JoblistState.DONE_INITFAILED, null, ZonedDateTime.now(), Boolean.TRUE, Boolean.FALSE);
		}
		
		return true; // sucessfull exit	
	}

	public JobListRunner(ITaskOperator taskOperator, Crawler crawler, Joblist jobs) {
		super();
		this.taskOperator = taskOperator;
		this.crawler = crawler;
		this.jobs = jobs;
	}
}
