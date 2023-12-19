package primemover.runner.joblist;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.function.Supplier;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import primemover.model.Crawler;
import primemover.model.Joblist;
import primemover.model.JoblistState;
import primemover.runner.ITaskOperator;
import primemover.service.IJobService;

/**
 * Runs joblists on the specified crawler
 * 
 * @author dburkolter
 *
 */
public class JobListRunner implements Supplier<Boolean> {
	private static Logger logger = LoggerFactory.getLogger(JobListRunner.class);

	private ITaskOperator taskOperator;
	private Crawler crawler;
	private Joblist jobs;

	@Value("${runner.MaxRedoMinutes:45}")
	private Long redoMinutes = 45l;

	@Autowired
	private IJobService jobService;

	private enum SetupState {
		SUCCESS, FAILURE, SKIPPED, NO_JOBS
	}

	private SetupState setup() {
		// check for empty joblist
		if (jobs == null) {
			logger.warn("failure running jobs for {} ({}), joblist is empty", crawler.getId(), crawler.getName());
			jobService.updateJoblistState(jobs, JoblistState.RUNNING_INPROGRESS, ZonedDateTime.now(), null, null,
					Boolean.TRUE);
			return SetupState.NO_JOBS;
		}

		// Check for max delay
		long diff = ChronoUnit.MINUTES.between(jobs.getStartTime(), ZonedDateTime.now());
		logger.info("setup crawler for {} ({}) with delay of {} minutes", crawler.getId(), crawler.getName(), diff);
		jobService.updateJoblistState(jobs, JoblistState.RUNNING_INPROGRESS, ZonedDateTime.now(), null, null,
				Boolean.TRUE); // set start time and processing, commit later

		if (diff > redoMinutes) {
			logger.info("skipping run as the jobs timed out after {} minutes. Max delay is {} minutes", diff,
					redoMinutes);

			return SetupState.SKIPPED;
		}

		// starting up
		logger.debug("Setting up crawler {} for joblist {}", crawler.getId(), jobs.getId());
		Boolean isStartedUp = taskOperator.setup(crawler);

		if (isStartedUp) {
			logger.debug("setup completed successfully for crawler {} for joblist {}", crawler.getId(), jobs.getId());
			jobService.updateJoblistState(jobs, JoblistState.RUNNING_INITDONE, ZonedDateTime.now(), null, null, null); // set
																														// new
																														// state
			return SetupState.SUCCESS;
		} else {
			logger.warn("setup failure for crawler {} for joblist {}", crawler.getId(), jobs.getId());
			jobService.updateJoblistState(jobs, JoblistState.RUNNING_INITFAILED, ZonedDateTime.now(), null, null, null); // set
																															// new
			// state
			return SetupState.FAILURE;
		}
	}

	private enum RunningState {
		NOTSTARTED, SUCCESS, FAILURE
	}

	private RunningState runJoblist() {
		logger.trace("running joblist {} for crawler {}", jobs.getId(), crawler.getId());

		Boolean jobsRes = taskOperator.runJobList(jobs);
		if (jobsRes) {
			logger.debug("success running jobs for {} ({})", crawler.getId(), crawler.getName());
			jobService.updateJoblistState(jobs, JoblistState.RUNNING_JOBSDONE, null, null, null, null);
			return RunningState.SUCCESS;
		} else {
			logger.warn("failure running jobs for {} ({})", crawler.getId(), crawler.getName());
			jobService.updateJoblistState(jobs, JoblistState.RUNNING_JOBSFAILURE, null, null, null, null);
			return RunningState.FAILURE;
		}
	}

	private void tearDown(@Nonnull SetupState setupRes, @Nonnull RunningState jobsRes) {
		// release resources
		logger.debug("tearing down crawler {} after running joblist {}", crawler.getId(), jobs.getId());
		taskOperator.tearDown(false);

		switch (setupRes) {
		case FAILURE:
			jobService.updateJoblistState(jobs, JoblistState.DONE_INITFAILED, null, ZonedDateTime.now(), Boolean.TRUE,
					Boolean.FALSE);

			break;
		case SKIPPED:
			jobService.updateJoblistState(jobs, JoblistState.SKIPPED_TIMEDOUT, null, ZonedDateTime.now(), Boolean.TRUE,
					Boolean.FALSE); // set execution end time
			break;
		case NO_JOBS:
			jobService.updateJoblistState(jobs, JoblistState.DONE_JOBLIST_EMPTY, null, ZonedDateTime.now(),
					Boolean.TRUE, Boolean.FALSE); // set execution end time
			break;

		case SUCCESS:

			switch (jobsRes) {
			case SUCCESS:
				jobService.updateJoblistState(jobs, JoblistState.DONE_SUCCESS, null, ZonedDateTime.now(), Boolean.TRUE,
						Boolean.FALSE);
				break;
			case FAILURE:
				jobService.updateJoblistState(jobs, JoblistState.DONE_JOBSFAILURE, null, ZonedDateTime.now(),
						Boolean.TRUE, Boolean.FALSE);
				break;
			}
			break;
		}
	}

	public Boolean run() {
		// add some context
		MDC.put("crawler.id", crawler.getId().toString());
		MDC.put("crawler.name", crawler.getName());
		MDC.put("joblist.id", jobs.getId().toString());
		MDC.put("correlationId", "cr-" + crawler.getId().toString() + "|jl-" + jobs.getId().toString());

		// running joblist
		try {
			SetupState setupRes = null;
			RunningState jobsRes = RunningState.NOTSTARTED;

			setupRes = setup();

			switch (setupRes) {
			case FAILURE:
				tearDown(setupRes, jobsRes);
				return false;
			case NO_JOBS:
				tearDown(setupRes, jobsRes);
				return true;
			case SKIPPED:
				tearDown(setupRes, jobsRes);
				return true;
			default:
				jobsRes = runJoblist();
				tearDown(setupRes, jobsRes);
				return true;
			}

		} catch (Exception e) {
			try {
				logger.error("uncaught error during execution of joblist {} for crawler {}", crawler.getName(),
						crawler.getId(), e);
				jobService.updateJoblistState(jobs, JoblistState.DONE_FAILURE, null, ZonedDateTime.now(), Boolean.TRUE,
						Boolean.FALSE);

				// make sure to tear down!
				logger.debug("tearing down crawler {} running joblistr {}", crawler.getId(), jobs.getId());
				taskOperator.tearDown(false);
			} catch (Exception ex) {
				logger.warn("error tearing down taskOperator after run-failure for crawler {}", crawler.getId(), ex);
			}

			return false; // exit due to error
		}
	}

	public JobListRunner(ITaskOperator taskOperator, Crawler crawler, Joblist jobs) {
		super();
		this.taskOperator = taskOperator;
		this.crawler = crawler;
		this.jobs = jobs;
	}

	@Override
	public Boolean get() {
		return run();
	}
}
