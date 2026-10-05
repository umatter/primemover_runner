package primemover.runner;

import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import primemover.model.Crawler;
import primemover.model.Joblist;
import primemover.runner.joblist.IJobListRunner;
import primemover.runner.joblist.JobListRunnerFactory;
import primemover.util.threading.MdcAwareThreadPoolExecutor;

import java.util.concurrent.*;

public class TaskExecutor {
	private static Logger logger = LoggerFactory.getLogger(TaskExecutor.class);

	/**
	 * maxThreads "equals" to the number of open browsers (running bots) in parallel
	 */
	@Value("${runner.MaxParallelExecution:2}")
	private Integer maxThreads; // maximum parallel executions of joblists

	@Autowired
	JobListRunnerFactory jobListRunnerFactory;

	private ExecutorService executorService;

	// Runners that have been handed to the pool and not yet finished, by joblist
	// id. This is what lets TaskManager reach a joblist it has just timed out:
	// cancelling the CompletableFuture would not stop the thread, so the runner
	// itself is kept.
	private final ConcurrentHashMap<Long, IJobListRunner> running = new ConcurrentHashMap<>();

	// Cutting off a joblist ends its browser, which can take a while. That must not
	// run on the scheduler thread that noticed the timeout.
	private final ExecutorService abortExecutor = Executors.newCachedThreadPool(
			new BasicThreadFactory.Builder().namingPattern("abort-%d").daemon(true).build());
	private boolean isSetup = false;

	public void setup() {
		// make sure to create an executor that will clean the MDC-state after
		// execution.
		BasicThreadFactory executorThreadFactory = new BasicThreadFactory.Builder() //
				.namingPattern("executor-%d")//
				.priority(Thread.MAX_PRIORITY)//
				.build();

		// needs to set core pool size as we have an unbounded queue (see documentation)
		executorService = new MdcAwareThreadPoolExecutor(maxThreads, maxThreads, 10, TimeUnit.SECONDS,
				new LinkedBlockingQueue<Runnable>(), executorThreadFactory);

		// executorService = Executors.newFixedThreadPool(maxThreads);
		this.isSetup = true;
	}

	public TaskExecutor() {
		// nothing to do
	}

	public CompletableFuture<Boolean> run(IJobListRunner runner) {
		running.put(runner.getJobListId(), runner);

		CompletableFuture<Boolean> execution = CompletableFuture //
				.supplyAsync(runner, executorService)//
				.whenComplete((result, error) -> running.remove(runner.getJobListId(), runner))//
				.thenComposeAsync(runnerResult -> CompletableFuture.supplyAsync(() -> {
					logger.info("execution of joblist {} finished", runner.getJobListId());
					return runnerResult;
				}))//
				.exceptionallyAsync((ex) -> {
					logger.warn("Execution of joblist {} failed with error:\n", runner.getJobListId(), ex);
					return false;
				});

		// log out stats
		if (executorService instanceof ThreadPoolExecutor) {
			ThreadPoolExecutor implementation = (ThreadPoolExecutor) executorService;
			int size = implementation.getQueue().size();
			logger.info("{} joblist are ready to execute; {} are currently executing", size,
					Math.min(size, maxThreads));
		}

		return execution;
	}

	public CompletableFuture<Boolean> run(Crawler crawler, Joblist jobs) {
		// run and remove from hash
		logger.info("Init execution of joblist {}", jobs.getId());
		IJobListRunner runner = jobListRunnerFactory.createJobListRunner(crawler, jobs);

		return run(runner);
	}

	/**
	 * Cuts off a joblist that is queued or running here, so its worker thread and
	 * browser are released. Returns at once; the browser is ended in the
	 * background.
	 *
	 * @return false when this executor holds no such joblist -- it finished, or
	 *         another runner started it
	 */
	public boolean abort(long jobListId) {
		IJobListRunner runner = running.get(jobListId);
		if (runner == null) {
			return false;
		}

		logger.warn("aborting joblist {}", jobListId);
		abortExecutor.execute(() -> {
			try {
				runner.abort();
			} catch (Exception e) {
				logger.warn("error aborting joblist {}", jobListId, e);
			}
		});
		return true;
	}

	public void shutdown() {
		this.executorService.shutdown();
		this.abortExecutor.shutdown();
	}
}
