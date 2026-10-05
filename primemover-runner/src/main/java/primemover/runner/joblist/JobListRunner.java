package primemover.runner.joblist;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import primemover.model.Crawler;
import primemover.model.Joblist;
import primemover.model.JoblistState;
import primemover.runner.ITaskOperator;
import primemover.runner.JobListOutcome;
import primemover.runner.JobDiagnostics;
import primemover.service.IJobService;

import javax.annotation.Nonnull;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs joblists on the specified crawler
 * 
 * @author dburkolter
 *
 */
public class JobListRunner implements IJobListRunner {
	private static Logger logger = LoggerFactory.getLogger(JobListRunner.class);

	/**
	 * Canonical correlation-id format, documented in
	 * {@code docs/integration-contract.md} as
	 * {@code exp-{experiment}|cr-{crawler}|q-{queue}|j-{job}}. The runner never
	 * learns the experiment id -- the API's crawler payload does not carry one --
	 * so the {@code exp-} segment is omitted rather than invented, and TaskOperator
	 * appends {@code |j-<id>} for the duration of each job. The individual MDC
	 * keys below stay exactly as they were: promtail extracts crawler_id and
	 * queue_id from {@code crawler.id} / {@code joblist.id} by name.
	 */
	private static final String MDC_CORRELATION_ID = "correlationId";

	/**
	 * Every MDC key this class sets, so {@link #run()} can put them all back the
	 * way it found them. Keeping the list here is what stops the next key from
	 * being added to {@code run()} and forgotten in the cleanup.
	 */
	private static final List<String> MDC_KEYS = List.of("crawler.id", "crawler.name", "joblist.id",
			MDC_CORRELATION_ID);

	private ITaskOperator taskOperator;

	// Set by runJoblist so tearDown can put the real counts in status_message.
	private JobListOutcome lastOutcome;
	private Crawler crawler;
	private Joblist jobs;

	// Set by abort(). Once true this runner reports nothing more about its joblist:
	// TaskManager has already recorded DONE_PROCESSING_TIMEOUT, and a late
	// "jobs done" or "failure" from the worker would overwrite it.
	private final AtomicBoolean aborted = new AtomicBoolean(false);
	private volatile Thread workerThread;

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
			reportJoblistState(JoblistState.RUNNING_INPROGRESS, null, ZonedDateTime.now(), null, null, Boolean.TRUE);
			return SetupState.NO_JOBS;
		}

		// Check for max delay
		long diff = ChronoUnit.MINUTES.between(jobs.getStartTime(), ZonedDateTime.now());
		logger.info("setup crawler for {} ({}) with delay of {} minutes", crawler.getId(), crawler.getName(), diff);
		// set start time and processing, commit later
		reportJoblistState(JoblistState.RUNNING_INPROGRESS, null, ZonedDateTime.now(), null, null, Boolean.TRUE);

		if (diff > redoMinutes) {
			logger.info("skipping run as the jobs timed out after {} minutes. Max delay is {} minutes", diff,
					redoMinutes);

			return SetupState.SKIPPED;
		}

		// starting up
		logger.debug("Setting up crawler {} for joblist {}", crawler.getId(), jobs.getId());
		Boolean isStartedUp = taskOperator.prepareExeuction(crawler);

		if (isStartedUp) {
			logger.debug("setup completed successfully for crawler {} for joblist {}", crawler.getId(), jobs.getId());
			reportJoblistState(JoblistState.RUNNING_INITDONE, null, ZonedDateTime.now(), null, null, null); // set new state
			return SetupState.SUCCESS;
		} else {
			logger.warn("setup failure for crawler {} for joblist {}", crawler.getId(), jobs.getId());
			reportJoblistState(JoblistState.RUNNING_INITFAILED,
					"browser/profile startup did not complete for crawler " + crawler.getId(), ZonedDateTime.now(), null,
					null, null); // set new state
			return SetupState.FAILURE;
		}
	}

	private enum RunningState {
		NOTSTARTED, SUCCESS, WARNING, FAILURE
	}

	private RunningState runJoblist() {
		logger.trace("running joblist {} for crawler {}", jobs.getId(), crawler.getId());

		JobListOutcome jobsRes = taskOperator.runJobList(jobs);
		lastOutcome = jobsRes;

		// A run that lost jobs is neither a success nor a total failure. Reporting
		// it as either is what recorded five truncated 113-job sessions as
		// "success, done" on 2026-08-27.
		if (jobsRes.isTotalFailure()) {
			logger.warn("failure running jobs for {} ({}): {}", crawler.getId(), crawler.getName(),
					jobsRes.describe());
			reportJoblistState(JoblistState.RUNNING_JOBSFAILURE, null, null, null, null, null);
			return RunningState.FAILURE;
		} else if (jobsRes.isClean()) {
			logger.debug("success running jobs for {} ({}): {}", crawler.getId(), crawler.getName(),
					jobsRes.describe());
			reportJoblistState(JoblistState.RUNNING_JOBSDONE, null, null, null, null, null);
			return RunningState.SUCCESS;
		} else {
			logger.warn("{} for {} ({}): {}", jobsRes.hasLostJobs() ? "completed with lost jobs" : "completed, some jobs degraded",
					crawler.getId(), crawler.getName(), jobsRes.describe());
			reportJoblistState(JoblistState.RUNNING_JOBSDONE, null, null, null, null, null);
			return RunningState.WARNING;
		}
	}

	private void tearDown(@Nonnull SetupState setupRes, @Nonnull RunningState jobsRes) {
		// release resources
		logger.debug("tearing down crawler {} after running joblist {}", crawler.getId(), jobs.getId());
		taskOperator.tearDown(false);

		switch (setupRes) {
		case FAILURE:
			reportJoblistState(JoblistState.DONE_INITFAILED,
					"browser/profile startup did not complete for crawler " + crawler.getId(), null,
					ZonedDateTime.now(), Boolean.TRUE, Boolean.FALSE);

			break;
		case SKIPPED:
			reportJoblistState(JoblistState.SKIPPED_TIMEDOUT,
					"start time was more than " + redoMinutes + " minutes ago", null, ZonedDateTime.now(), Boolean.TRUE,
					Boolean.FALSE); // set execution end time
			break;
		case NO_JOBS:
			reportJoblistState(JoblistState.DONE_JOBLIST_EMPTY, null, null, ZonedDateTime.now(), Boolean.TRUE,
					Boolean.FALSE); // set execution end time
			break;

		case SUCCESS:

			switch (jobsRes) {
			case SUCCESS:
				reportJoblistState(JoblistState.DONE_SUCCESS, lastOutcome == null ? null : lastOutcome.describe(), null,
						ZonedDateTime.now(), Boolean.TRUE, Boolean.FALSE);
				break;
			case WARNING:
				// Completed, but lost jobs. The counts go into status_message so the
				// run's own record says how much of it survived.
				reportJoblistState(lastOutcome != null && !lastOutcome.hasLostJobs()
						? JoblistState.DONE_JOBSDEGRADED : JoblistState.DONE_JOBSWARNING,
						lastOutcome == null ? "completed with lost jobs" : lastOutcome.describe(), null,
						ZonedDateTime.now(), Boolean.TRUE, Boolean.FALSE);
				break;
			case FAILURE:
				reportJoblistState(JoblistState.DONE_JOBSFAILURE,
						lastOutcome == null ? "at least one job did not finish successfully" : lastOutcome.describe(),
						null, ZonedDateTime.now(), Boolean.TRUE, Boolean.FALSE);
				break;
			default:
				break;
			}
			break;
		}
	}

	/**
	 * Report a queue state and complain loudly if the report did not land.
	 *
	 * <p>
	 * {@code updateJoblistState} returns {@code false} when the update fails, and
	 * every caller used to ignore that -- which is how a queue ends up stuck at
	 * {@code processing=1, processed=0} with nothing in the log explaining it.
	 *
	 * <p>
	 * Exceptions are deliberately <b>not</b> caught here -- {@link #run()} already
	 * has a catch-all that reports DONE_FAILURE and tears the crawler down.
	 */
	private void reportJoblistState(JoblistState state, String detail, ZonedDateTime startTime, ZonedDateTime endTime,
			Boolean processed, Boolean processing) {
		if (aborted.get()) {
			logger.info("joblist {} was cut off; not reporting {}", jobs == null ? null : jobs.getId(), state);
			return;
		}

		Boolean reported = jobService.updateJoblistState(jobs, state, detail, startTime, endTime, processed,
				processing);

		if (!Boolean.TRUE.equals(reported)) {
			logger.error("Could not report state {} for joblist {}."
					+ " The queue stays at its previous state in the database.", state,
					jobs == null ? null : jobs.getId());
		}
	}

	/**
	 * Run this joblist inside a logging context that is removed again afterwards.
	 *
	 * <p>
	 * These four keys used to be put and never removed. The runner executes
	 * joblists on a pooled executor ({@code runner.MaxParallelExecution} threads),
	 * so the keys outlived the run and stamped the <em>next</em> joblist's -- and
	 * every between-jobs -- log line with the previous crawler's id, name and
	 * correlation id. Promtail turns those into Loki labels, so the damage is not
	 * cosmetic: lines get filed under the wrong crawler and a trace query answers
	 * with another bot's work. {@code TaskOperator} already does this for
	 * {@code job.id} one level down; the enclosing keys get the same discipline.
	 *
	 * <p>
	 * Prior values are restored rather than blindly removed, so a caller that
	 * established its own context keeps it.
	 */
	public Boolean run() {
		workerThread = Thread.currentThread();
		Map<String, String> previous = new LinkedHashMap<>();
		for (String key : MDC_KEYS) {
			previous.put(key, MDC.get(key));
		}

		try {
			// add some context
			MDC.put("crawler.id", crawler.getId().toString());
			MDC.put("crawler.name", crawler.getName());
			MDC.put("joblist.id", jobs.getId().toString());
			// canonical form; the exp- segment is omitted because the runner is never
			// told the experiment id (the API's crawler payload carries none)
			MDC.put(MDC_CORRELATION_ID, JobDiagnostics.correlationId(null, crawler.getId(), jobs.getId(), null));

			return runInContext();
		} finally {
			workerThread = null;
			for (Map.Entry<String, String> entry : previous.entrySet()) {
				if (entry.getValue() == null) {
					MDC.remove(entry.getKey());
				} else {
					MDC.put(entry.getKey(), entry.getValue());
				}
			}
		}
	}

	private Boolean runInContext() {
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
				logger.info("start running joblist {}", jobs.getId());
				jobsRes = runJoblist();
				logger.info("done running joblist {}", jobs.getId());
				tearDown(setupRes, jobsRes);
				return true;
			}

		} catch (Exception e) {
			try {
				logger.error("uncaught error during execution of joblist {} for crawler {}", crawler.getName(),
						crawler.getId(), e);
				reportJoblistState(JoblistState.DONE_FAILURE, JobDiagnostics.describe(e), null, ZonedDateTime.now(),
						Boolean.TRUE, Boolean.FALSE);

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

	@Override
	public void abort() {
		if (!aborted.compareAndSet(false, true)) {
			return;
		}

		logger.warn("cutting off joblist {} for crawler {}", jobs.getId(), crawler.getId());

		// Kill the browser first: a worker blocked in a WebDriver call only comes back
		// when that call fails, and an interrupt does not reach it.
		try {
			taskOperator.tearDown(true);
		} catch (Exception e) {
			logger.warn("error ending the browser of cut-off joblist {}", jobs.getId(), e);
		}

		Thread worker = workerThread;
		if (worker != null) {
			worker.interrupt();
		}
	}

	@Override
	public long getJobListId() {
		return this.jobs.getId();
	}
}
