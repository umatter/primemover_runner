package primemover.util;

import java.time.Duration;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WindowType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bounds a single page load and leaves the browser usable after one that never
 * finished.
 *
 * <p>
 * Why this exists. On experiment 189 (2026-10-05) a bot-protection page on
 * yelp.com never finished loading. The visit job did not fail quickly: it ended
 * in {@code TimeoutException} only after a median of 1,620 s (max 2,701 s over
 * 130 jobs), and because the browser stayed stuck, every later visit in the same
 * session waited just as long. Fifteen of 29 measurement sessions were then cut
 * off by the three-hour processing timeout. Selenium's own page-load timeout
 * defaults to 300 s, and the stuck browser answered the follow-up commands (DOM
 * wait, JS-ready wait, captcha checks) each with another multi-minute read
 * timeout, so the cost was many multiples of one page load.
 *
 * <p>
 * Two halves, both needed: a short page-load timeout so the navigation itself
 * fails in about a minute, and a recovery step so the <em>next</em> job starts
 * on a browser that answers.
 *
 * <p>
 * The recovery has a third rung since experiment 189 on 2026-10-07: 38 of 40
 * measurement sessions lost jobs, 29% of their jobs were skipped, and the
 * dominant cause was a renderer that froze on a page load (rottentomatoes.com
 * in 13 sessions, bing.com as the first job in 3). Such a tab answers neither
 * {@code window.stop()} nor {@code about:blank}, but the browser around it may
 * still open a fresh tab. Each rung is bounded in wall time
 * ({@link #STEP_BOUND}), because a frozen tab holds a command for as long as the
 * driver lets it.
 */
public final class PageLoadGuard {

	private static final Logger logger = LoggerFactory.getLogger(PageLoadGuard.class);

	private static final String BLANK_URL = "about:blank";

	/** A script any live tab answers at once. */
	private static final String PROBE_SCRIPT = "return 1;";

	/**
	 * Wall-time bound for one recovery step. A browser that answers at all
	 * answers these in well under a second; a frozen one held each for ~60 s on
	 * experiment 189. A browser that cannot even open a tab is given up on after
	 * four steps (~40 s), one that can after at most six (~60 s); the session's
	 * own fallback, a profile restart, comes after that.
	 */
	static final Duration STEP_BOUND = Duration.ofSeconds(10);

	/**
	 * How the browser came back after a page-load timeout. The label is what the
	 * job's status message says after "browser ", so the study can count each
	 * kind; "recovered" and "unresponsive" keep the wording they had before the
	 * new-tab step existed.
	 */
	public enum Recovery {
		/** {@code window.stop()} answered. */
		STOPPED("recovered"),
		/** The tab went to {@code about:blank}. */
		BLANK("recovered"),
		/** The frozen tab was abandoned for a fresh one that answers. */
		NEW_TAB("recovered (new tab)"),
		/** Nothing answered. */
		UNRESPONSIVE("unresponsive");

		private final String label;

		Recovery(String label) {
			this.label = label;
		}

		public String label() {
			return label;
		}

		public boolean usable() {
			return this != UNRESPONSIVE;
		}
	}

	private PageLoadGuard() {
	}

	/**
	 * Sets the driver's page-load timeout.
	 *
	 * @param seconds the bound; zero or less leaves Selenium's default in place
	 * @return true when a bound is now in force
	 */
	public static boolean apply(WebDriver driver, int seconds) {
		if (driver == null || seconds <= 0) {
			return false;
		}

		try {
			driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(seconds));
			logger.info("page load timeout set to {} s", seconds);
			return true;
		} catch (Exception e) {
			// A browser that cannot take a timeout is no worse off than before.
			logger.warn("could not set the page load timeout to {} s", seconds, e);
			return false;
		}
	}

	/**
	 * Navigates, and on a page-load timeout stops the load before rethrowing, so
	 * the caller fails the job at once and the next job finds a usable browser.
	 *
	 * @throws TimeoutException when the page did not finish loading in time; the
	 *                          subclass {@link BrowserUnresponsiveException} when the
	 *                          browser also stopped answering
	 */
	public static void navigate(WebDriver driver, String url, long jobId) {
		try {
			driver.navigate().to(url);
		} catch (TimeoutException e) {
			Recovery recovery = recover(driver, jobId, STEP_BOUND);
			logger.warn("page load timed out for {} (job {}); browser {}", url, jobId, recovery.label());
			String message = "Page load timed out for " + url + " (job " + jobId + "); browser " + recovery.label();
			throw recovery.usable() ? new TimeoutException(message, e) : new BrowserUnresponsiveException(message, e);
		}
	}

	/**
	 * Stops whatever the page is still loading, falling back to a blank page and
	 * then to a fresh tab.
	 *
	 * @return true when the browser answered one of them
	 */
	public static boolean recoverAfterTimeout(WebDriver driver, long jobId) {
		return recover(driver, jobId, STEP_BOUND).usable();
	}

	/**
	 * The recovery, rung by rung, each bounded by {@code stepBound}.
	 *
	 * <p>
	 * {@code window.stop()} first because it keeps the tab and the profile as they
	 * are. A tab too wedged to run it is navigated to {@code about:blank} instead;
	 * that also drops the page's scripts, which is the point. A tab that cannot
	 * even do that is left behind for a new one. An interrupted caller (the
	 * processing-timeout abort) gets {@link Recovery#UNRESPONSIVE} without the
	 * remaining rungs being tried.
	 */
	static Recovery recover(WebDriver driver, long jobId, Duration stepBound) {
		try {
			BoundedCall.call(() -> ((JavascriptExecutor) driver).executeScript("window.stop();"), stepBound);
			return Recovery.STOPPED;
		} catch (Exception stopFailure) {
			logger.warn("window.stop() failed after a page load timeout in job {}; trying {}", jobId, BLANK_URL,
					stopFailure);
		}

		if (!Thread.currentThread().isInterrupted()) {
			try {
				BoundedCall.call(() -> {
					driver.navigate().to(BLANK_URL);
					return null;
				}, stepBound);
				return Recovery.BLANK;
			} catch (Exception blankFailure) {
				logger.warn("{} failed after a page load timeout in job {}; trying a new tab", BLANK_URL, jobId,
						blankFailure);
			}
		}

		if (!Thread.currentThread().isInterrupted() && openFreshTab(driver, jobId, stepBound)) {
			return Recovery.NEW_TAB;
		}

		logger.error("browser did not answer after a page load timeout in job {}", jobId);
		return Recovery.UNRESPONSIVE;
	}

	/**
	 * Moves the driver to a new tab that answers, closing the frozen one if it
	 * can. The frozen tab's renderer may still be spinning, so leaving it open
	 * costs CPU on a box that runs dozens of browsers; failing to close it does
	 * not, however, make the browser unusable.
	 *
	 * @return true when the driver now points at a tab that answered a script
	 */
	private static boolean openFreshTab(WebDriver driver, long jobId, Duration stepBound) {
		String frozen = null;
		try {
			frozen = BoundedCall.call(driver::getWindowHandle, stepBound);
		} catch (Exception e) {
			logger.warn("could not read the frozen tab's handle in job {}; it will be left open", jobId, e);
		}

		String fresh;
		try {
			fresh = BoundedCall.call(() -> {
				driver.switchTo().newWindow(WindowType.TAB);
				((JavascriptExecutor) driver).executeScript(PROBE_SCRIPT);
				return driver.getWindowHandle();
			}, stepBound);
		} catch (Exception e) {
			logger.warn("a new tab did not answer either after the page load timeout in job {}", jobId, e);
			return false;
		}

		if (frozen != null && !frozen.equals(fresh)) {
			String frozenHandle = frozen;
			try {
				BoundedCall.call(() -> {
					driver.switchTo().window(frozenHandle);
					driver.close();
					return null;
				}, stepBound);
			} catch (Exception e) {
				logger.warn("could not close the frozen tab in job {}; leaving it open", jobId, e);
			}

			// Back to the fresh tab whatever happened above: after a close the
			// driver points at a window that no longer exists, after a failed one
			// at the frozen tab. Only a tab that answers again counts.
			try {
				BoundedCall.call(() -> {
					driver.switchTo().window(fresh);
					return ((JavascriptExecutor) driver).executeScript(PROBE_SCRIPT);
				}, stepBound);
			} catch (Exception e) {
				logger.warn("could not return to the new tab in job {}", jobId, e);
				return false;
			}
		}

		logger.warn("frozen tab replaced by a new tab in job {}", jobId);
		return true;
	}
}
