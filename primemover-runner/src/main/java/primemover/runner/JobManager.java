package primemover.runner;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;

import primemover.config.StartupMode;
import primemover.model.Crawler;
import primemover.model.Joblist;
import primemover.model.JoblistState;
import primemover.service.ICrawlerService;
import primemover.service.JobService;

public class JobManager implements IJobManager {
	private static Logger logger = LoggerFactory.getLogger(JobManager.class);
	
	@Value("${global.mode:PRODUCTION}")
	private StartupMode mode;
	
	@Value("${runner.MaxParallelExecution:4}")
	private Integer maxThreads = 4; // maximum parallel executions of joblists
	
	@Value("${runner.UpdateInterval:5}")
	private Integer updateInterval; // Update Interval to grab planned jobs in minutes
	
	@Value("${runner.MaxRedoMinutes:45}")
	private Integer redoMinutes; // Update Interval to grab planned jobs in minutes
	
	private ZonedDateTime lastExecution = null; // last execution time of scheduler
	private ZonedDateTime lastTimeAdded = ZonedDateTime.now(); // the max datetime of the jobs added during the last run
	
	private ExecutorService executorService;
	
	private ScheduledExecutorService scheduler;


	@Autowired
	private ICrawlerService crawlerService;
	
	@Autowired
	JobListRunnerFactory jobListRunnerFactory;
	
	@Autowired
	private JobService jobService;
	
	/* (non-Javadoc)
	 * @see primemover.runner.IJobManager#setup()
	 */
	@Override
	public void setup() {
		executorService = Executors.newFixedThreadPool(maxThreads);
		scheduler = Executors.newSingleThreadScheduledExecutor(); // jobs will only pass the execution to the executor service
	}

	/* (non-Javadoc)
	 * @see primemover.runner.IJobManager#run()
	 */
	@Override
	@Scheduled(fixedRate = 60000, initialDelay = 10000)
	public void run() {
		ZonedDateTime curRun = ZonedDateTime.now();
		
		if (lastExecution != null) {
			long diff = ChronoUnit.MINUTES.between(lastExecution, curRun);
			
			// reduce invocations to update interval
			if (diff < updateInterval) {
				logger.trace("Intervall not reached - Skipping execution");
				return;
			}
		}
		
		logger.info("Scheduled execution");
		
		// get the jobList for each Crawler
		List<Pair<Crawler, Joblist>> taskList = crawlerService.getCurrentTasks();
		
		// last time to add scheduler tasks
		ZonedDateTime newLastTimeToAdd = curRun.plusMinutes(updateInterval);
		
		// consider redo-minutes after startup
		ZonedDateTime lastTimeAddedInclRedo;
		if (this.lastExecution == null) {
			lastTimeAddedInclRedo = lastTimeAdded.minusMinutes(this.redoMinutes);
		} else {
			lastTimeAddedInclRedo = lastTimeAdded;
		}
		
		logger.info("consider joblists between {} and {}", lastTimeAddedInclRedo, newLastTimeToAdd);
		
		// loop all available joblists
		for (Pair<Crawler, Joblist> task : taskList) {
			Joblist jobs = task.getRight();
			
			// has jobs to do?
			if (jobs == null) {
				continue;
			}
			
			// verifiy states and skip inactive, processing or processed joblists. Make sure to mark jobs as processed!
			if (jobs.isProcessing()) {
				continue;
			}
			
			if (jobs.isProcessed() ) {
				continue;
			}
			
			if (!jobs.isActive() ) {
				jobService.updateJoblistState(jobs, JoblistState.SKIPPED_INACTIVE, ZonedDateTime.now(), null, Boolean.TRUE); // set execution time
				continue;
			}
			
			if (jobs.isReviewed()) {
				jobService.updateJoblistState(jobs, JoblistState.SKIPPED_REVIEWED, ZonedDateTime.now(), null, Boolean.TRUE); // set execution time
				continue;
			}
			
			
			// verify timing
			ZonedDateTime startTime = jobs.getStartTime();
			
			// provide joblists starting before the window end to the runner
			if (newLastTimeToAdd.isAfter(startTime)) {
				// calculating execution delay (positive only)
				Long delaySeconds = Math.max(0l, ChronoUnit.SECONDS.between(curRun, startTime));
				delaySeconds = 0l;
				
				logger.info("Preparing execution of {} in {} seconds", task.getRight().getId(), delaySeconds);
				
				// just pass the job to the executor after reaching the starttime
				scheduler.schedule(new Runnable() {
					// Job to run		
					JobListRunner runner = jobListRunnerFactory.createJobListRunner(task.getLeft(), jobs);
					// ID of joblist
					Long jobListId = task.getRight().getId();
					
		            @Override
		            public void run() {
		            	logger.info("Init execution of joblist {}", jobListId);
		            	executorService.submit(runner);
		            }
		            
		        }, delaySeconds, TimeUnit.SECONDS);
			} 
		}
		
		logger.info("Done initializing jobs");
		
		lastExecution = curRun;
		lastTimeAdded = newLastTimeToAdd;
	}
}
