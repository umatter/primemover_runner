package primemover.util;

import java.time.Duration;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
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
 */
public final class PageLoadGuard {

	private static final Logger logger = LoggerFactory.getLogger(PageLoadGuard.class);

	private static final String BLANK_URL = "about:blank";

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
	 * @throws TimeoutException when the page did not finish loading in time
	 */
	public static void navigate(WebDriver driver, String url, long jobId) {
		try {
			driver.navigate().to(url);
		} catch (TimeoutException e) {
			boolean usable = recoverAfterTimeout(driver, jobId);
			logger.warn("page load timed out for {} (job {}); browser {}", url, jobId,
					usable ? "recovered" : "unresponsive");
			throw new TimeoutException("Page load timed out for " + url + " (job " + jobId + "); browser "
					+ (usable ? "recovered" : "unresponsive"), e);
		}
	}

	/**
	 * Stops whatever the page is still loading, falling back to a blank page.
	 *
	 * <p>
	 * {@code window.stop()} first because it keeps the tab and the profile as they
	 * are. A tab too wedged to run it is navigated to {@code about:blank} instead;
	 * that also drops the page's scripts, which is the point.
	 *
	 * @return true when the browser answered one of the two
	 */
	public static boolean recoverAfterTimeout(WebDriver driver, long jobId) {
		try {
			((JavascriptExecutor) driver).executeScript("window.stop();");
			return true;
		} catch (Exception stopFailure) {
			logger.warn("window.stop() failed after a page load timeout in job {}; trying {}", jobId, BLANK_URL,
					stopFailure);
		}

		try {
			driver.navigate().to(BLANK_URL);
			return true;
		} catch (Exception blankFailure) {
			logger.error("browser did not answer after a page load timeout in job {}", jobId, blankFailure);
			return false;
		}
	}
}
