package primemover.util;

import java.time.Duration;
import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Makes sure a watch page actually carries recommendations before a decision is
 * asked for, and re-anchors it once if it does not.
 *
 * <p>
 * Why this exists. A YouTube SPA navigation can leave the shell in place --
 * {@code ytd-watch-flexy} and an empty {@code #related} -- while the data fetch
 * that fills it never lands. The page is then permanently useless: no
 * recommendations, no {@code ytInitialData} entries, not even a present-video
 * id. The decision function correctly reports that it cannot choose, the job
 * fails, and the next job navigates nowhere, because the workflow's two
 * {@code visitjob}s run only at the start. So one bad landing costs the whole
 * remainder of the session rather than one job.
 *
 * <p>
 * Measured on ex63, experiment 147 (2026-08-28): bot {@code soak3_us_04} clicked
 * a recommendation at 23:01 and spent its remaining 49 decisions and 147 skipped
 * jobs on the resulting shell, finishing 86 of 284 jobs where the other ten bots
 * finished 270-278. That single stuck page accounts for 49 of the run's 60 job
 * failures.
 *
 * <p>
 * A reload re-requests the document, and a shell left empty by a failed data
 * fetch comes back populated. The recovery is deliberately bounded: one reload,
 * never a loop. Retrying forever against a page that is genuinely empty -- a
 * members-only video, a removed video -- would burn the session just as
 * thoroughly as the bug it fixes, only more slowly.
 *
 * <p>
 * A recovered page is <em>not</em> an ordinary one: it is not what the previous
 * click produced. The caller is expected to mark the job degraded on
 * {@link Outcome#RECOVERED} so a rescued session never reads as normal.
 */
public final class WatchPageAnchor {

	private static final Logger logger = LoggerFactory.getLogger(WatchPageAnchor.class);

	/** The recommendation tiles, in either of the containers YouTube uses. */
	private static final By RECOMMENDATIONS = By
			.cssSelector("#related yt-lockup-view-model, #secondary yt-lockup-view-model");

	/** How long to wait for the page to render normally. */
	private static final Duration FIRST_WAIT = Duration.ofSeconds(10);

	/** How long to wait after the reload; a cold document fetch is slower. */
	private static final Duration SECOND_WAIT = Duration.ofSeconds(15);

	private static final Duration POLL = Duration.ofMillis(500);

	/** What the page turned out to be. */
	public enum Outcome {
		/** Recommendations were there; nothing was done. */
		PRESENT,
		/** The page was empty and a reload brought it back. Degraded, not normal. */
		RECOVERED,
		/** Still no recommendations. The decision will fail, and should. */
		ABSENT
	}

	private WatchPageAnchor() {
	}

	public static Outcome ensureRecommendations(WebDriver driver, long jobId, boolean recoveryEnabled) {
		return ensureRecommendations(driver, jobId, recoveryEnabled, FIRST_WAIT, SECOND_WAIT, POLL);
	}

	/**
	 * @param driver          the session's driver
	 * @param jobId           for logging only
	 * @param recoveryEnabled false restores the old behaviour: report ABSENT
	 *                        without reloading
	 * @param firstWait       how long to wait before deciding the page is empty
	 * @param secondWait      how long to wait after the reload
	 * @param poll            interval between presence checks
	 */
	static Outcome ensureRecommendations(WebDriver driver, long jobId, boolean recoveryEnabled,
			Duration firstWait, Duration secondWait, Duration poll) {

		if (awaitRecommendations(driver, firstWait, poll)) {
			return Outcome.PRESENT;
		}

		if (!recoveryEnabled) {
			logger.warn("no recommendation container for job {}; recovery disabled, proceeding as-is", jobId);
			return Outcome.ABSENT;
		}

		logger.warn("no recommendation container for job {}; reloading once to re-anchor", jobId);
		try {
			driver.navigate().refresh();
		} catch (Exception e) {
			// A driver that cannot reload is a driver in trouble; say so and let
			// the decision fail normally rather than throwing from here.
			logger.warn("reload failed for job {}; continuing on the existing page", jobId, e);
			return Outcome.ABSENT;
		}

		if (awaitRecommendations(driver, secondWait, poll)) {
			logger.info("recommendation container recovered after reload for job {}", jobId);
			return Outcome.RECOVERED;
		}

		logger.warn("recommendation container still absent after reload for job {}; proceeding as-is", jobId);
		return Outcome.ABSENT;
	}

	private static boolean awaitRecommendations(WebDriver driver, Duration timeout, Duration poll) {
		long deadline = System.nanoTime() + timeout.toNanos();
		while (true) {
			if (present(driver)) {
				return true;
			}
			if (System.nanoTime() >= deadline) {
				return false;
			}
			try {
				Thread.sleep(poll.toMillis());
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return false;
			}
		}
	}

	/**
	 * A driver that throws while being asked is treated as "not present" rather
	 * than being allowed to abort the job: the caller's next step reports the
	 * missing recommendations properly.
	 */
	private static boolean present(WebDriver driver) {
		try {
			List<WebElement> found = driver.findElements(RECOMMENDATIONS);
			return found != null && !found.isEmpty();
		} catch (Exception e) {
			return false;
		}
	}

	/** The selector, exposed so a test can pin it against the handler's. */
	static By recommendationsSelector() {
		return RECOMMENDATIONS;
	}
}
