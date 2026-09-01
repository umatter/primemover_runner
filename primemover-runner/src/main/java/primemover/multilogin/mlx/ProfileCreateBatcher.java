package primemover.multilogin.mlx;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Collects concurrent profile-creation requests into one API call.
 *
 * <p>
 * Multilogin allows 50 API operations per minute per workspace, and says so in
 * the body of the rejection it sends when a launch burst crosses that:
 *
 * <pre>
 * 429 ... "Failed to create profile: too many requests.
 *          Try sending a single request with \"times\" parameter instead"
 * </pre>
 *
 * Crawlers start on independent threads from the executor pool, so twelve bots
 * launching together made twelve separate {@code POST /profile/create} calls in
 * the same second. On 2026-08-31 that cost one bot its entire run, and on
 * 2026-09-01 it cost four.
 *
 * <p>
 * The first thread into an empty batch becomes its leader. It waits a short
 * window for company, then issues one call with {@code times = batch size} and
 * hands one id to each waiter. Everyone else blocks until the leader is done.
 *
 * <p>
 * <b>Why identical profiles are safe here.</b> A batch is created from one
 * member's template, so all of them share that template's proxy and name. The
 * proxy does not matter: {@code ContextBuilder} calls
 * {@code ProfileService.updateProxy} for every session before the browser is
 * used, addressed by profile id, and <em>throws</em> if it fails. So a profile
 * cannot silently run on another bot's exit IP -- the crawler fails instead,
 * which for a study measuring per-bot personalisation is the only acceptable
 * outcome. The shared name is cosmetic and affects only what a human sees in the
 * Multilogin console.
 *
 * <p>
 * Failure is shared too: if the call throws, every waiter in the batch receives
 * that exception, so the existing startup retry and rate-limit backoff behave
 * exactly as they did per-thread.
 */
public class ProfileCreateBatcher {

	private static final Logger logger = LoggerFactory.getLogger(ProfileCreateBatcher.class);

	/** Issues one create for {@code times} identical profiles. */
	public interface BatchCreator {
		List<String> create(String templateJson, int times) throws Exception;
	}

	/** One thread waiting for its profile id. */
	private static final class Waiter {
		private final String templateJson;
		private final CountDownLatch done = new CountDownLatch(1);
		private volatile String id;
		private volatile Exception failure;

		Waiter(String templateJson) {
			this.templateJson = templateJson;
		}

		void succeed(String value) {
			this.id = value;
			done.countDown();
		}

		void fail(Exception e) {
			this.failure = e;
			done.countDown();
		}
	}

	/**
	 * Multilogin caps {@code times} at 10. A request for more is rejected whole:
	 * "400 BAD_REQUEST_BODY: invalid profile times value. Should be from 1 to
	 * 10". That is not a per-caller mistake to guard at the config boundary --
	 * it is a hard limit of the endpoint, so it is enforced here where the call
	 * is made, and a config value above it is clamped rather than trusted. A
	 * launch larger than 10 simply forms more than one batch.
	 */
	static final int API_MAX_TIMES = 10;

	private final int maxBatch;
	private final long windowMillis;
	private final long waitTimeoutMillis;

	private final Object lock = new Object();
	private final List<Waiter> pending = new ArrayList<>();
	private boolean leaderActive;

	public ProfileCreateBatcher(int maxBatch, long windowMillis, long waitTimeoutMillis) {
		this.maxBatch = Math.min(API_MAX_TIMES, Math.max(1, maxBatch));
		this.windowMillis = Math.max(0, windowMillis);
		this.waitTimeoutMillis = waitTimeoutMillis;
	}

	/** The effective batch size after clamping to the API maximum. */
	int maxBatch() {
		return maxBatch;
	}

	/**
	 * Returns one freshly created profile id, batching with any concurrent
	 * callers.
	 *
	 * @throws Exception whatever the API call threw, or on timeout
	 */
	public String acquire(String templateJson, BatchCreator creator) throws Exception {
		Waiter me = new Waiter(templateJson);
		boolean lead;

		synchronized (lock) {
			pending.add(me);
			lead = !leaderActive;
			if (lead) {
				leaderActive = true;
			} else {
				// Wake the leader: it may now have a full batch and can stop waiting.
				lock.notifyAll();
			}
		}

		if (lead) {
			runBatch(creator);
		}

		if (!me.done.await(waitTimeoutMillis, TimeUnit.MILLISECONDS)) {
			throw new IllegalStateException(
					"timed out after " + waitTimeoutMillis + "ms waiting for a batched profile creation");
		}
		if (me.failure != null) {
			throw me.failure;
		}
		return me.id;
	}

	/** Runs as the batch leader: collect, call once, distribute. */
	private void runBatch(BatchCreator creator) {
		List<Waiter> batch;

		synchronized (lock) {
			long deadline = System.currentTimeMillis() + windowMillis;
			// Wait for company, but never past the window, and never once full.
			while (pending.size() < maxBatch) {
				long remaining = deadline - System.currentTimeMillis();
				if (remaining <= 0) {
					break;
				}
				try {
					lock.wait(remaining);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					break;
				}
			}
			batch = new ArrayList<>(pending.subList(0, Math.min(pending.size(), maxBatch)));
			pending.subList(0, batch.size()).clear();
			// Released before the network call so late arrivals form the next batch
			// instead of blocking behind this one.
			leaderActive = false;
			lock.notifyAll();
		}

		if (batch.isEmpty()) {
			return;
		}

		try {
			List<String> ids = creator.create(batch.get(0).templateJson, batch.size());
			distribute(batch, ids);
		} catch (Exception e) {
			// Everyone in the batch sees the same failure, so the caller's retry
			// and rate-limit backoff behave exactly as they did per-thread.
			for (Waiter w : batch) {
				w.fail(e);
			}
		}
	}

	/**
	 * Hands out one id each. A short answer fails the remainder rather than
	 * reusing an id: two crawlers sharing a profile would interleave their
	 * cookies and browsing history, which is worse than either of them failing.
	 */
	private void distribute(List<Waiter> batch, List<String> ids) {
		int available = ids == null ? 0 : ids.size();
		if (available < batch.size()) {
			logger.warn("profile batch asked for {} ids and got {}", batch.size(), available);
		} else if (batch.size() > 1) {
			logger.info("created {} profiles in one call", batch.size());
		}

		for (int i = 0; i < batch.size(); i++) {
			Waiter w = batch.get(i);
			if (i < available && ids.get(i) != null && !ids.get(i).isEmpty()) {
				w.succeed(ids.get(i));
			} else {
				w.fail(new IllegalStateException(
						"profile creation returned " + available + " id(s) for a batch of " + batch.size()));
			}
		}
	}
}
