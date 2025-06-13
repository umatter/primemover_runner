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
		CompletableFuture<Boolean> execution = CompletableFuture //
				.supplyAsync(runner, executorService)//
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

	public void shutdown() {
		this.executorService.shutdown();
	}
}
