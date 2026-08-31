package primemover.capture;

import java.util.List;
import java.util.concurrent.TimeUnit;

import com.browserup.harreader.model.Har;
import com.browserup.harreader.model.HarEntry;
import com.browserup.harreader.model.HarTiming;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Replaces impossible durations in a captured HAR with the format's own
 * "not available" marker.
 *
 * <p>
 * A request that never received a response has its {@code wait} timing computed
 * against a response time that was never set, so the recorder emits something
 * like:
 *
 * <pre>
 * "time": -1303303604,
 * "timings": {"blocked": -1, "dns": -1, "connect": 486,
 *             "send": 0, "wait": -1303304090, "receive": 0}
 * </pre>
 *
 * That is roughly minus fifteen days. On one real job (13451, 2026-08-28) 14 of
 * 53 entries looked like that, and a consumer summing them reported a total page
 * time of -18246271267 ms.
 *
 * <p>
 * The HAR specification already has a way to say "this was not measured": the
 * value {@code -1}. So that is what gets written. Note the asymmetry -- {@code -1}
 * is legitimate and common for {@code blocked}, {@code dns} and {@code ssl}, so
 * only values below {@code -1} are treated as garbage; a genuine {@code -1} is
 * left exactly as it is.
 *
 * <p>
 * Zero is deliberately not used. A request nobody timed is not a request that
 * took no time, and writing {@code 0} would make it indistinguishable from an
 * instantaneous one, dragging every average down while looking perfectly
 * healthy.
 *
 * <p>
 * This runs unconditionally, unlike {@link HarContentFilter}, which is an
 * opt-in size optimisation. Emitting a number that cannot be a duration is a
 * correctness problem, not a matter of taste.
 */
public final class HarTimingSanitizer {

	private static final Logger logger = LoggerFactory.getLogger(HarTimingSanitizer.class);

	/** The HAR format's marker for a timing that was not measured. */
	private static final int NOT_AVAILABLE = -1;

	private HarTimingSanitizer() {
	}

	/**
	 * Rewrites impossible timings in place.
	 *
	 * @param har the captured archive; null is tolerated
	 * @return how many entries were corrected
	 */
	public static int sanitize(Har har) {
		if (har == null || har.getLog() == null) {
			return 0;
		}

		List<HarEntry> entries = har.getLog().getEntries();
		if (entries == null || entries.isEmpty()) {
			return 0;
		}

		int corrected = 0;
		for (HarEntry entry : entries) {
			if (entry == null) {
				continue;
			}

			boolean impossible = sanitizeTimings(entry.getTimings());

			Integer time = entry.getTime();
			if (time != null && time < NOT_AVAILABLE) {
				impossible = true;
			}

			if (impossible) {
				// The total is the sum of the parts, so once any part is unknown
				// the total is unknown too.
				entry.setTime(NOT_AVAILABLE);
				corrected++;
			}
		}

		if (corrected > 0) {
			logger.debug("HAR timings: {} of {} entries had no usable duration and were marked -1", corrected,
					entries.size());
		}
		return corrected;
	}

	/**
	 * @return true when at least one field was below -1 and had to be rewritten
	 */
	private static boolean sanitizeTimings(HarTiming timings) {
		if (timings == null) {
			return false;
		}

		boolean rewrote = false;

		if (timings.getBlocked(TimeUnit.MILLISECONDS) < NOT_AVAILABLE) {
			timings.setBlocked(NOT_AVAILABLE, TimeUnit.MILLISECONDS);
			rewrote = true;
		}
		if (timings.getDns(TimeUnit.MILLISECONDS) < NOT_AVAILABLE) {
			timings.setDns(NOT_AVAILABLE, TimeUnit.MILLISECONDS);
			rewrote = true;
		}
		if (timings.getConnect(TimeUnit.MILLISECONDS) < NOT_AVAILABLE) {
			timings.setConnect(NOT_AVAILABLE, TimeUnit.MILLISECONDS);
			rewrote = true;
		}
		if (timings.getSend(TimeUnit.MILLISECONDS) < NOT_AVAILABLE) {
			timings.setSend(NOT_AVAILABLE, TimeUnit.MILLISECONDS);
			rewrote = true;
		}
		if (timings.getWait(TimeUnit.MILLISECONDS) < NOT_AVAILABLE) {
			timings.setWait(NOT_AVAILABLE, TimeUnit.MILLISECONDS);
			rewrote = true;
		}
		if (timings.getReceive(TimeUnit.MILLISECONDS) < NOT_AVAILABLE) {
			timings.setReceive(NOT_AVAILABLE, TimeUnit.MILLISECONDS);
			rewrote = true;
		}

		return rewrote;
	}
}
