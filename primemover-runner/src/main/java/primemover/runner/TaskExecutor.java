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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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

	// One crawler is one Multilogin profile, and a profile runs one browser at a
	// time: a second joblist of a crawler whose browser is still open fails its
	// setup with PROFILE_ALREADY_RUNNING (about 110 joblists of experiment 189 on
	// 2026-10-05). So every crawler gets a lane. Its first entry is the joblist
	// handed to the pool; the others wait behind it in arrival order without
	// holding a pool thread, and the next is handed over when the first finishes.
	// A crawler with nothing running or waiting has no lane. Guarded by itself.
	private final Map<Long, Deque<Turn>> lanes = new HashMap<>();

	/** A joblist in its crawler's lane, and the future its caller was given. */
	private static final class Turn {
		final IJobListRunner runner;
		final CompletableFuture<Boolean> result = new CompletableFuture<>();

		Turn(IJobListRunner runner) {
			this.runner = runner;
		}
	}

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

	/**
	 * Runs a joblist without regard to its crawler. Only for runners that have no
	 * crawler; {@link #run(Crawler, Joblist)} goes through the crawler's lane.
	 */
	public CompletableFuture<Boolean> run(IJobListRunner runner) {
		running.put(runner.getJobListId(), runner);
		return submit(runner, () -> {
		});
	}

	/**
	 * Runs a joblist once no other joblist of the same crawler is running here.
	 * Until then it waits in the crawler's lane and holds no pool thread; when it
	 * does start, {@code JobListRunner.setup()} skips it as usual if it is by then
	 * more than MaxRedoMinutes late.
	 *
	 * <p>
	 * A joblist that is already in its crawler's lane is not taken a second time.
	 * TaskManager hands over every due, not-yet-processing joblist on each poll
	 * (once a minute on ex63), so one that waits gets handed over again until it
	 * starts; a second copy would run the session again after the first.
	 */
	public CompletableFuture<Boolean> run(Long crawlerId, IJobListRunner runner) {
		if (crawlerId == null) {
			return run(runner);
		}

		long jobListId = runner.getJobListId();
		Turn turn = new Turn(runner);
		Turn ahead;
		synchronized (lanes) {
			Deque<Turn> lane = lanes.computeIfAbsent(crawlerId, id -> new ArrayDeque<>());
			for (Turn held : lane) {
				if (held.runner.getJobListId() == jobListId) {
					logger.info("joblist {} of crawler {} is already running or waiting here; not taking it again",
							jobListId, crawlerId);
					return held.result;
				}
			}

			ahead = lane.peekFirst();
			lane.addLast(turn);
			if (ahead == null) {
				running.put(jobListId, runner);
			}
		}

		if (ahead == null) {
			start(crawlerId, turn);
		} else {
			logger.info("crawler {} is busy: joblist {} waits until joblist {} of the same crawler has finished",
					crawlerId, jobListId, ahead.runner.getJobListId());
		}

		return turn.result;
	}

	public CompletableFuture<Boolean> run(Crawler crawler, Joblist jobs) {
		// run and remove from hash
		logger.info("Init execution of joblist {}", jobs.getId());
		IJobListRunner runner = jobListRunnerFactory.createJobListRunner(crawler, jobs);

		return run(crawler.getId(), runner);
	}

	private void start(Long crawlerId, Turn turn) {
		submit(turn.runner, () -> release(crawlerId, turn)).whenComplete((result, error) -> {
			if (error != null) {
				turn.result.completeExceptionally(error);
			} else {
				turn.result.complete(result);
			}
		});
	}

	/**
	 * The crawler's running joblist has finished: hand its next waiting joblist to
	 * the pool, or forget the crawler when none is waiting.
	 */
	private void release(Long crawlerId, Turn finished) {
		Turn next;
		synchronized (lanes) {
			Deque<Turn> lane = lanes.get(crawlerId);
			lane.remove(finished);
			next = lane.peekFirst();
			if (next == null) {
				lanes.remove(crawlerId);
			} else {
				// under the lock, so abort() finds it either waiting or running
				running.put(next.runner.getJobListId(), next.runner);
			}
		}

		if (next != null) {
			logger.info("crawler {} is free: starting its waiting joblist {}", crawlerId, next.runner.getJobListId());
			start(crawlerId, next);
		}
	}

	private CompletableFuture<Boolean> submit(IJobListRunner runner, Runnable whenFinished) {
		CompletableFuture<Boolean> execution = CompletableFuture //
				.supplyAsync(runner, executorService)//
				.whenComplete((result, error) -> {
					running.remove(runner.getJobListId(), runner);
					whenFinished.run();
				})//
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

	/**
	 * Cuts off a joblist that is queued or running here, so its worker thread and
	 * browser are released. Returns at once; the browser is ended in the
	 * background. A joblist still waiting for its crawler is dropped instead and
	 * never starts.
	 *
	 * @return false when this executor holds no such joblist -- it finished, or
	 *         another runner started it
	 */
	public boolean abort(long jobListId) {
		List<Turn> dropped = new ArrayList<>();
		synchronized (lanes) {
			for (Deque<Turn> lane : lanes.values()) {
				Iterator<Turn> turns = lane.iterator();
				turns.next(); // the first is in the pool; it is cut off below
				while (turns.hasNext()) {
					Turn turn = turns.next();
					if (turn.runner.getJobListId() == jobListId) {
						turns.remove();
						dropped.add(turn);
					}
				}
			}
		}

		for (Turn turn : dropped) {
			logger.warn("aborting joblist {}, which was waiting for its crawler; it will not start", jobListId);
			turn.result.complete(false);
		}

		IJobListRunner runner = running.get(jobListId);
		if (runner == null) {
			return !dropped.isEmpty();
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
