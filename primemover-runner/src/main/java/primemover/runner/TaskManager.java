package primemover.runner;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
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
import primemover.runner.joblist.JobListRunner;
import primemover.runner.joblist.JobListRunnerFactory;
import primemover.service.ICrawlerService;
import primemover.service.IJobService;
import primemover.util.threading.MdcAwareThreadPoolExecutor;

/**
 * The Task-Manager is responsible for checking for newly available tasks and
 * delegates it to the operator for execution. The manager will check the
 * database for new jobs in a regular interval. The manager ensures that the all
 * tasks will execute with best effort at the intended point in time. The
 * limiting factor are the available resources (mainly cpu) which restricts the
 * number of parallel executions. 2 thread pools handle the resource allocation:
 * a scheduler (single thread) that will feed tasks to the executor thread pool.
 * The exeuctor thread pool will limit degree of parallelism.
 * 
 * @author dburkolter
 *
 */
public class TaskManager implements ITaskManager {
	private static Logger logger = LoggerFactory.getLogger(TaskManager.class);

	@Value("${global.mode:PRODUCTION}")
	private StartupMode mode;

	/**
	 * maxThreads "equals" to the number of open browsers (running bots) in parallel
	 */
	@Value("${runner.MaxParallelExecution:4}")
	private Integer maxThreads = 4; // maximum parallel executions of joblists

	@Value("${runner.UpdateInterval:15}")
	private Integer updateInterval; // Update Interval to grab planned jobs in minutes

	@Value("${runner.MaxRedoMinutes:45}")
	private Integer redoMinutes; // Update Interval to grab planned jobs in minutes

	@Value("${runner.ProcessingTimeoutMinutes:180}")
	private Integer processingTimeoutMinutes; // Update Interval to grab planned jobs in minutes

	private ZonedDateTime lastExecution = null; // last execution time of scheduler
	private ZonedDateTime lastTimeAdded = ZonedDateTime.now(); // the max datetime of the jobs added during the last run

	private ExecutorService executorService;

	private ScheduledExecutorService scheduler;

	@Autowired
	private ICrawlerService crawlerService;

	@Autowired
	JobListRunnerFactory jobListRunnerFactory;

	@Autowired
	private IJobService jobService;

	/*
	 * (non-Javadoc)
	 * 
	 * @see primemover.runner.IJobManager#setup()
	 */
	@Override
	public void setup() {

		// make sure to create an executor that will clean the MDC-state after
		// execution.
		executorService = new MdcAwareThreadPoolExecutor(0, maxThreads, 10, TimeUnit.SECONDS,
				new LinkedBlockingQueue<Runnable>());

		// executorService = Executors.newFixedThreadPool(maxThreads);
		scheduler = Executors.newSingleThreadScheduledExecutor(); // jobs will only pass the execution to the executor
																	// service
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see primemover.runner.IJobManager#run()
	 */

	/**
	 * This method is running periodic checks for new TaskLists (aka Queues) to run.
	 * It uses the Spring-Scheduling. The initial delay makes sure that all other
	 * classes are loaded and spring initialised.
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
			Crawler crawler = task.getLeft();
			Joblist jobs = task.getRight();

			// has jobs to do?
			if (jobs == null) {
				logger.info("empty queue for crawler {}", crawler.getId());
				jobService.updateJoblistState(jobs, JoblistState.DONE_JOBLIST_EMPTY, ZonedDateTime.now(),
						ZonedDateTime.now(), Boolean.TRUE, Boolean.FALSE); // set execution time

				continue;
			}

			// verifiy states and skip inactive, processing or processed joblists. Make sure
			// to mark jobs as processed!
			if (jobs.isProcessing()) {
				// We may had a teardown before, or there was a fatal error -> mark processing
				// timeout
				if (ChronoUnit.MINUTES.between(jobs.getStartTime(), curRun) > processingTimeoutMinutes) {
					logger.info("Timming out processing joblist {}", jobs.getId());
					jobService.updateJoblistState(jobs, JoblistState.DONE_PROCESSING_TIMEOUT, null, ZonedDateTime.now(),
							Boolean.TRUE, Boolean.FALSE); // set execution time
				}
				;

				continue;
			}

			if (jobs.isProcessed()) {
				continue;
			}

			if (!jobs.isActive()) {
				jobService.updateJoblistState(jobs, JoblistState.SKIPPED_INACTIVE, ZonedDateTime.now(), null,
						Boolean.TRUE, null); // set execution time
				continue;
			}

			if (jobs.isReviewed()) {
				jobService.updateJoblistState(jobs, JoblistState.SKIPPED_REVIEWED, ZonedDateTime.now(), null,
						Boolean.TRUE, null); // set execution time
				continue;
			}

			// verify timing
			ZonedDateTime startTime = jobs.getStartTime();

			// provide joblists starting before the window end to the runner
			if (newLastTimeToAdd.isAfter(startTime)) {
				// calculating execution delay (positive only)
				Long delaySeconds = Math.max(0l, ChronoUnit.SECONDS.between(curRun, startTime));

				logger.info("Preparing execution of joblist {} in {} seconds", task.getRight().getId(), delaySeconds);

				// just pass the job to the executor after reaching the starttime
				scheduler.schedule(new Runnable() {

					// crawler
					Crawler crawler = task.getLeft();

					// jobs
					Joblist jobs = task.getRight();

					@Override
					public void run() {
						// run
						logger.info("Init execution of joblist {}", jobs.getId());
						JobListRunner runner = jobListRunnerFactory.createJobListRunner(crawler, jobs);
						executorService.submit(runner);
					}

				}, delaySeconds, TimeUnit.SECONDS);
			}
		}

		// log out stats
		if (executorService instanceof ThreadPoolExecutor) {
			ThreadPoolExecutor implementation = (ThreadPoolExecutor) executorService;
			int size = implementation.getQueue().size();
			logger.info("{} joblist are ready to execute; {} are currently executing", size,
					Math.min(size, maxThreads));
		}

		lastExecution = curRun;
		lastTimeAdded = newLastTimeToAdd;
	}
}
