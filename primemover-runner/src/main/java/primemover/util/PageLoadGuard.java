package primemover.util;

import java.time.Duration;
import java.util.concurrent.Callable;

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
 * {@code window.stop()} nor {@code about:blank}; a new tab is tried after them.
 *
 * <p>
 * How the rungs are timed follows from how ChromeDriver treats a frozen tab,
 * measured with ChromeDriver 144 and Chrome 154 (2026-10-07):
 * <ul>
 * <li>It runs a session's commands one at a time. A command the client stops
 * waiting for still runs, and the next one queues behind it.</li>
 * <li>It holds every command to the tab -- {@code window.stop()},
 * {@code about:blank}, even NewWindow -- for the session's page-load timeout
 * and then answers with "Timed out receiving message from renderer" (with the
 * timeout at 20 s each failed after 20.0 s). A renderer that was only busy is
 * answered as soon as it is free.</li>
 * </ul>
 * So each rung waits for the driver's own answer, bounded at the page-load
 * timeout plus {@link Bounds#margin()} only as a backstop, and nothing more is
 * sent once a command went unanswered: it would wait behind the one still held,
 * and a browser that answered late would be reported unresponsive. Bounding each
 * rung at 10 s did exactly that to a renderer that answered {@code window.stop()}
 * 45 s after the timeout, which the earlier, unbounded recovery had saved.
 *
 * <p>
 * On a frozen tab NewWindow does not open a tab; it fails after the page-load
 * timeout like the rest (in the same measurement). The new-tab rung therefore
 * runs under a page-load timeout of {@link Bounds#newTabPageLoad()} and sets the
 * session's own back afterwards, so it costs seconds rather than another minute.
 * It can still help where the tab was not frozen, and with a driver that opens
 * the tab without waiting on the old one. A tab opened through the browser's own
 * DevTools endpoint ({@code PUT /json/new}) did answer at once beside a frozen
 * one in the same measurement; that route is not used here.
 */
public final class PageLoadGuard {

	private static final Logger logger = LoggerFactory.getLogger(PageLoadGuard.class);

	private static final String BLANK_URL = "about:blank";

	/** A script any live tab answers at once. */
	private static final String PROBE_SCRIPT = "return 1;";

	/**
	 * Wall-time bounds of the recovery.
	 *
	 * @param margin           added to the page-load timeout for a command the
	 *                         driver holds that long: the backstop for a driver
	 *                         that does not answer at all
	 * @param quick            a command that does not wait on the tab (handles,
	 *                         timeouts, switching, closing) or goes to a fresh tab
	 * @param newTabPageLoad   the page-load timeout NewWindow runs under
	 * @param fallbackPageLoad assumed when the driver does not report its own
	 */
	record Bounds(Duration margin, Duration quick, Duration newTabPageLoad, Duration fallbackPageLoad) {
	}

	/**
	 * A frozen tab costs two page-load timeouts plus about five seconds: 125 s at
	 * runner.PageLoadTimeoutSeconds=60. A busy tab is saved if it answers within
	 * two page-load timeouts, as before the new-tab rung existed.
	 */
	static final Bounds BOUNDS = new Bounds(Duration.ofSeconds(15), Duration.ofSeconds(10), Duration.ofSeconds(5),
			Duration.ofSeconds(60));

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

	/** How one command to the recovering browser ended. */
	private enum Answer {
		/** It ran. */
		DONE,
		/**
		 * The driver answered with an error, e.g. its renderer timeout. The session
		 * is free for the next command.
		 */
		FAILED,
		/**
		 * No answer within the bound, or the worker was cut off. The driver may
		 * still hold the command, so nothing more is sent.
		 */
		NONE
	}

	private record Reply<T>(Answer answer, T value) {
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
			Recovery recovery = recover(driver, jobId, BOUNDS);
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
		return recover(driver, jobId, BOUNDS).usable();
	}

	/**
	 * The recovery, rung by rung.
	 *
	 * <p>
	 * {@code window.stop()} first because it keeps the tab and the profile as they
	 * are. A tab too wedged to run it is navigated to {@code about:blank} instead;
	 * that also drops the page's scripts, which is the point. A tab that cannot
	 * even do that is left behind for a new one. Each rung is sent only after the
	 * driver answered the one before; an unanswered command, or an interrupted
	 * caller (the processing-timeout abort), ends the recovery as
	 * {@link Recovery#UNRESPONSIVE} with nothing more sent.
	 */
	static Recovery recover(WebDriver driver, long jobId, Bounds bounds) {
		// What the driver holds each command to the tab for. A session command,
		// answered at once while nothing else is pending.
		Reply<Duration> configured = ask("reading the page-load timeout", jobId,
				() -> driver.manage().timeouts().getPageLoadTimeout(), bounds.quick());
		if (configured.answer() == Answer.NONE) {
			return unresponsive(jobId);
		}
		Duration pageLoad = configured.answer() == Answer.DONE && configured.value() != null
				&& configured.value().toMillis() > 0 ? configured.value() : null;
		Duration answerBound = (pageLoad != null ? pageLoad : bounds.fallbackPageLoad()).plus(bounds.margin());

		Answer stop = ask("window.stop()", jobId, () -> ((JavascriptExecutor) driver).executeScript("window.stop();"),
				answerBound).answer();
		if (stop == Answer.DONE) {
			return Recovery.STOPPED;
		}
		if (stop == Answer.NONE) {
			return unresponsive(jobId);
		}

		Answer blank = ask(BLANK_URL, jobId, () -> {
			driver.navigate().to(BLANK_URL);
			return null;
		}, answerBound).answer();
		if (blank == Answer.DONE) {
			return Recovery.BLANK;
		}
		if (blank == Answer.NONE) {
			return unresponsive(jobId);
		}

		if (pageLoad == null) {
			// without the session's own value, the short timeout the new tab runs
			// under could not be set back
			logger.warn("the driver did not report its page-load timeout; no new tab tried in job {}", jobId);
			return unresponsive(jobId);
		}
		if (openFreshTab(driver, jobId, pageLoad, bounds)) {
			return Recovery.NEW_TAB;
		}
		return unresponsive(jobId);
	}

	private static Recovery unresponsive(long jobId) {
		logger.error("browser did not answer after a page load timeout in job {}", jobId);
		return Recovery.UNRESPONSIVE;
	}

	/**
	 * Moves the driver to a new tab that answers, closing the frozen one if it
	 * can. The frozen tab's renderer may still be spinning, so leaving it open
	 * costs CPU on a box that runs dozens of browsers; failing to close it does
	 * not, however, make the browser unusable.
	 *
	 * <p>
	 * NewWindow runs under {@link Bounds#newTabPageLoad()}, and the session's own
	 * page-load timeout is set back whatever NewWindow did. A browser whose
	 * timeout cannot be set back is not used: every later visit in it would fail
	 * after a few seconds.
	 *
	 * @param pageLoad the session's page-load timeout
	 * @return true when the driver now points at a tab that answered a script
	 */
	private static boolean openFreshTab(WebDriver driver, long jobId, Duration pageLoad, Bounds bounds) {
		Reply<String> frozen = ask("reading the frozen tab's handle", jobId, driver::getWindowHandle,
				bounds.quick());
		if (frozen.answer() == Answer.NONE) {
			return false;
		}

		Duration shortLoad = bounds.newTabPageLoad();
		if (ask("lowering the page-load timeout to " + seconds(shortLoad) + " for a new tab", jobId, () -> {
			driver.manage().timeouts().pageLoadTimeout(shortLoad);
			return null;
		}, bounds.quick()).answer() != Answer.DONE) {
			return false;
		}

		// NewWindow, the probe and the handle: three commands, each answered
		// within the short timeout
		Reply<String> fresh = ask("a new tab", jobId, () -> {
			driver.switchTo().newWindow(WindowType.TAB);
			((JavascriptExecutor) driver).executeScript(PROBE_SCRIPT);
			return driver.getWindowHandle();
		}, shortLoad.multipliedBy(3).plus(bounds.margin()));
		if (fresh.answer() == Answer.NONE) {
			return false;
		}

		if (ask("setting the page-load timeout back to " + seconds(pageLoad), jobId, () -> {
			driver.manage().timeouts().pageLoadTimeout(pageLoad);
			return null;
		}, bounds.quick()).answer() != Answer.DONE) {
			logger.warn("the page-load timeout is still {} after a new tab in job {}; this browser is not used",
					seconds(shortLoad), jobId);
			return false;
		}

		if (fresh.answer() != Answer.DONE) {
			return false;
		}

		if (frozen.answer() == Answer.DONE && frozen.value() != null && !frozen.value().equals(fresh.value())) {
			String frozenHandle = frozen.value();
			Answer closed = ask("closing the frozen tab", jobId, () -> {
				driver.switchTo().window(frozenHandle);
				driver.close();
				return null;
			}, bounds.quick()).answer();
			if (closed == Answer.NONE) {
				return false;
			}

			// Back to the fresh tab whatever happened above: after a close the
			// driver points at a window that no longer exists, after a failed one
			// at the frozen tab. Only a tab that answers again counts.
			if (ask("returning to the new tab", jobId, () -> {
				driver.switchTo().window(fresh.value());
				return ((JavascriptExecutor) driver).executeScript(PROBE_SCRIPT);
			}, bounds.quick()).answer() != Answer.DONE) {
				return false;
			}
		} else if (frozen.answer() != Answer.DONE) {
			logger.warn("the frozen tab's handle is unknown in job {}; it is left open", jobId);
		}

		logger.warn("frozen tab replaced by a new tab in job {}", jobId);
		return true;
	}

	/**
	 * Sends one command and waits for the driver's answer, at most
	 * {@code bound}. Nothing is sent from an interrupted (cut-off) worker.
	 */
	private static <T> Reply<T> ask(String what, long jobId, Callable<T> call, Duration bound) {
		if (Thread.currentThread().isInterrupted()) {
			logger.warn("session cut off; {} not sent after the page load timeout in job {}", what, jobId);
			return new Reply<>(Answer.NONE, null);
		}

		try {
			return new Reply<>(Answer.DONE, BoundedCall.call(call, bound));
		} catch (java.util.concurrent.TimeoutException e) {
			logger.warn("no answer to {} within {} after the page load timeout in job {}; nothing more is sent "
					+ "to this browser", what, seconds(bound), jobId);
			return new Reply<>(Answer.NONE, null);
		} catch (InterruptedException e) {
			logger.warn("session cut off while waiting for {} after the page load timeout in job {}", what, jobId);
			return new Reply<>(Answer.NONE, null);
		} catch (Exception e) {
			logger.warn("{} failed after the page load timeout in job {}", what, jobId, e);
			return new Reply<>(Answer.FAILED, null);
		}
	}

	private static String seconds(Duration d) {
		return String.format(java.util.Locale.ROOT, "%.1f s", d.toMillis() / 1000.0);
	}
}
