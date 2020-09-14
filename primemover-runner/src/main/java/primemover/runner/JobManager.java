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
import primemover.service.ICrawlerService;

public class JobManager implements IJobManager {
	private static Logger logger = LoggerFactory.getLogger(JobManager.class);
	
	@Value("${global.mode:PRODUCTION}")
	private StartupMode mode;
	
	@Value("${runner.MaxParallelExecution:4}")
	private Integer maxThreads = 4; // maximum parallel executions of joblists
	
	@Value("${runner.UpdateInterval:5}")
	private Integer updateInterval = 5; // Update Interval to grab planned jobs in minutes
	
	@Value("${runner.MaxRedoMinutes:15}")
	private Integer redoMinutes = 15; // Update Interval to grab planned jobs in minutes
	
	private ZonedDateTime lastExecution = null; // last execution time of scheduler
	private ZonedDateTime lastTimeAdded = ZonedDateTime.now(); // the max datetime of the jobs added during the last run
	
	private ExecutorService executorService;
	
	private ScheduledExecutorService scheduler;


	@Autowired
	private ICrawlerService crawlerService;
	
	@Autowired
	TaskOperatorFactory taskServiceFactory;
	
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
		
		// add scheduler tasks
		ZonedDateTime newLastTimeToAdd = curRun.plusMinutes(updateInterval);
		ZonedDateTime lastTimeAddedInclRedo = lastTimeAdded.minusMinutes(this.redoMinutes);
		
		for (Pair<Crawler, Joblist> task : taskList) {
		
			Joblist jobs = task.getRight();
			ZonedDateTime startTime = jobs.getStartTime();
			
			if (lastTimeAddedInclRedo.isBefore(startTime) && newLastTimeToAdd.isAfter(startTime)) {
				// calculating execution delay (positive only)
				Long delaySeconds = Math.max(0l, ChronoUnit.SECONDS.between(curRun, startTime));
				
				logger.info("Preparing execution of {} in {} seconds", task.getRight().getId(), delaySeconds);
				
				// just pass the job to the executor after reaching the starttime
				scheduler.schedule(new Runnable() {
					// Job to run		
					JobListRunner runner = new JobListRunner(taskServiceFactory.getItem(), task.getLeft(), jobs);
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
		
		lastExecution = curRun;
		lastTimeAdded = newLastTimeToAdd;
	}
}
