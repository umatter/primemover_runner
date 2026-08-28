package primemover.util;

import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bounces the Chrome renderer process to reclaim leaked V8 heap.
 *
 * <p>
 * Why this exists. ChromeDriver leaks roughly 14 MB of V8 old-space per
 * same-site SPA navigation (Selenium #13510 / #13533: detached {@code Window}
 * objects from previous page instances are never destroyed). One renderer
 * serves an entire session, so the heap grows monotonically with the number of
 * navigations, not with wall-clock time. Against the
 * {@code --max-old-space-size} cap set in {@code ProfileService} it aborts with
 * "Reached heap limit", Selenium reports {@code WebDriverException: tab
 * crashed}, and every remaining job in the queue is skipped.
 *
 * <p>
 * Measured on ex63 2026-08-27: five 113-job YouTube queues, all five dead at
 * 27-37 completed lambda-select navigations (mean 33.6, CV ~11%) after 34-69
 * minutes (CV ~30%). Navigations, not minutes, is the predictor. The same
 * workload at 8-13 navigations per session on 2026-08-24 produced 1828 jobs and
 * zero crashes -- nothing broke, the queues simply got long enough to cross a
 * threshold that was always there.
 *
 * <p>
 * Raising the cap only rescales that ceiling; it does not remove it. Navigating
 * cross-origin makes Chrome tear the youtube.com renderer down and build a new
 * one, which reclaims <em>all</em> of the leak, so peak heap becomes O(1) in
 * queue length instead of O(navigations). The profile, its cookies and the
 * network stack are untouched -- the leak lives in the renderer's V8 heap, not
 * in the profile -- so login and session state survive.
 *
 * <p>
 * Cost is one cross-origin navigation plus one navigation back, ~2-3 s, with no
 * Multilogin API call. That matters: recycling the whole MLX profile instead
 * would cost roughly 3-4 minutes of dead time per recycle
 * ({@code TaskOperator.tearDown} alone sleeps 25 s and can wait ten further
 * rounds, and bring-up to a live renderer measured 154-181 s) and would consume
 * one of the 50 profile operations per minute the workspace allows.
 */
public final class RendererRecycler {

	private static final Logger logger = LoggerFactory.getLogger(RendererRecycler.class);

	/**
	 * A cross-origin target that reliably lands in its own process and loads
	 * without network access. {@code about:blank} is deliberately not used: it can
	 * inherit the current SiteInstance, which leaves the leaking renderer alive.
	 */
	private static final String NEUTRAL_URL = "chrome://version";

	private RendererRecycler() {
	}

	/**
	 * Navigates away from the current origin and back, so Chrome discards the
	 * renderer holding the leaked heap.
	 *
	 * <p>
	 * Only http(s) pages are recycled. Anything else -- {@code about:blank}, a
	 * {@code chrome-extension://} page mid-startup, a driver that cannot report a
	 * URL -- is left alone, because there is either nothing to reclaim or nothing
	 * safe to return to.
	 *
	 * @return true when the bounce completed and the original URL was restored
	 */
	public static boolean recycle(WebDriver driver, long jobId) {
		if (driver == null) {
			return false;
		}

		String url;
		try {
			url = driver.getCurrentUrl();
		} catch (Exception e) {
			logger.warn("renderer recycle skipped for job {}: driver would not report a URL", jobId, e);
			return false;
		}

		if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) {
			logger.debug("renderer recycle skipped for job {}: current page is {}", jobId, url);
			return false;
		}

		try {
			logger.info("recycling renderer before job {} (heap reclaim; returning to {})", jobId, url);
			driver.get(NEUTRAL_URL);
			driver.get(url);
			logger.debug("renderer recycled, back on {}", url);
			return true;
		} catch (Exception e) {
			// A failed bounce must never fail the job: the worst case is that we
			// keep the old renderer and its heap, which is exactly where we were.
			logger.warn("renderer recycle failed before job {}; continuing on the existing renderer", jobId, e);
			return false;
		}
	}

	/**
	 * True when the driver is currently showing a page whose renderer is worth
	 * recycling. Used to avoid counting navigations that never built one.
	 */
	public static boolean onRecyclablePage(WebDriver driver) {
		if (driver == null) {
			return false;
		}
		try {
			String url = driver.getCurrentUrl();
			return url != null && (url.startsWith("http://") || url.startsWith("https://"));
		} catch (Exception e) {
			return false;
		}
	}

	/** Exposed for tests: the target used to force a cross-origin process swap. */
	public static String neutralUrl() {
		return NEUTRAL_URL;
	}
}
