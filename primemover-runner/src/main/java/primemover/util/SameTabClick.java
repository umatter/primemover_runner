package primemover.util;

import java.time.Duration;
import java.util.Locale;
import java.util.Set;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

/**
 * Keeps a result click in the tab the session runs in, and says whether the
 * click left the page it was made on.
 *
 * <p>
 * Bing marks every result link {@code target="_blank"}. The select job clicks by
 * script and goes on driving the tab it is in, so the clicked page opened -- if
 * at all -- in a tab nothing looked at: the capture after the click was the
 * results page again, the dwell that followed was spent on it, and whether the
 * click reached Bing's {@code /ck/a} redirect at all was up to the popup blocker,
 * since a scripted click carries no user activation. Observed on experiment 201
 * (bing_ai_polarization): in 15 of 15 sampled clicks the page captured after the
 * click was the results page.
 *
 * <p>
 * Two measures, in order. Before the click, the link's new-tab target is removed,
 * so the browser follows it in place. After it, if a new tab appeared anyway (a
 * page that opens one from a script), the session follows that tab and closes the
 * one the decision was made on, so the browser keeps a single tab as before.
 *
 * <p>
 * Only used off YouTube: recommendation links there never carry a target, and the
 * YouTube path stays exactly as it was.
 */
public final class SameTabClick {

	/** What the click did, as far as the browser shows. */
	public enum Outcome {
		/** The tab now shows another page. */
		NAVIGATED,
		/** A new tab opened; the session now runs in it and the old one is closed. */
		FOLLOWED_NEW_TAB,
		/** Neither: the tab still shows the page the decision was made on. */
		STAYED
	}

	/**
	 * Removes a new-tab target from the clicked element or the link around it, and
	 * returns the removed value (null when there was none).
	 */
	static final String KEEP_IN_TAB_JS = "var el = arguments[0];"
			+ "var a = (el && el.closest) ? (el.closest('a[target]') || el) : el;"
			+ "if (!a || !a.getAttribute) { return null; }"
			+ "var t = a.getAttribute('target');"
			+ "if (t === null || /^\\s*$/.test(t) || /^\\s*_(self|top|parent)\\s*$/i.test(t)) { return null; }"
			+ "a.removeAttribute('target');"
			+ "return t;";

	static final Duration POLL = Duration.ofMillis(250);
	/** How long the URL must stay put before the page counts as arrived (redirects pass through). */
	static final Duration SETTLE = Duration.ofMillis(1000);

	private SameTabClick() {
	}

	/** True for a target that makes the browser open another browsing context. */
	static boolean opensNewBrowsingContext(String target) {
		if (target == null) {
			return false;
		}
		String t = target.trim().toLowerCase(Locale.ROOT);
		return !(t.isEmpty() || t.equals("_self") || t.equals("_top") || t.equals("_parent"));
	}

	/**
	 * Makes the coming click open in this tab. Returns the target it removed, or
	 * null when there was none or the page would not say; never throws, since a
	 * failure here must not cost the click.
	 */
	public static String keepInTab(JavascriptExecutor js, WebElement element) {
		try {
			Object removed = js.executeScript(KEEP_IN_TAB_JS, element);
			return removed == null ? null : removed.toString();
		} catch (RuntimeException e) {
			return null;
		}
	}

	/**
	 * Waits, up to {@code timeout}, for the click to take effect. A new tab is
	 * followed at once. A changed URL counts once it has stayed the same for
	 * {@link #SETTLE}, so a redirect page (Bing's {@code /ck/a}) is passed through
	 * to the page it leads to; a URL that is still changing at the timeout counts
	 * as navigated too, since the page has left.
	 *
	 * @param fromUrl       the URL of the page the decision was made on
	 * @param handlesBefore the window handles just before the click
	 * @param decisionTab   the handle of the tab the click was made in
	 */
	public static Outcome awaitNavigation(WebDriver driver, String fromUrl, Set<String> handlesBefore,
			String decisionTab, Duration timeout) throws InterruptedException {
		return awaitNavigation(driver, fromUrl, handlesBefore, decisionTab, timeout, POLL, SETTLE);
	}

	static Outcome awaitNavigation(WebDriver driver, String fromUrl, Set<String> handlesBefore,
			String decisionTab, Duration timeout, Duration poll, Duration settle) throws InterruptedException {
		long deadline = System.nanoTime() + timeout.toNanos();
		String lastUrl = null;
		long lastChange = System.nanoTime();
		while (true) {
			String opened = newHandle(driver, handlesBefore);
			if (opened != null) {
				follow(driver, opened, decisionTab);
				return Outcome.FOLLOWED_NEW_TAB;
			}
			String url = YouTubePage.currentUrl(driver);
			long now = System.nanoTime();
			if (url != null && !url.equals(lastUrl)) {
				lastUrl = url;
				lastChange = now;
			}
			boolean left = lastUrl != null && !lastUrl.equals(fromUrl);
			if (left && now - lastChange >= settle.toNanos()) {
				return Outcome.NAVIGATED;
			}
			if (now >= deadline) {
				return left ? Outcome.NAVIGATED : Outcome.STAYED;
			}
			Thread.sleep(poll.toMillis());
		}
	}

	/** A handle that was not there before the click, or null. */
	private static String newHandle(WebDriver driver, Set<String> handlesBefore) {
		if (handlesBefore == null) {
			return null;
		}
		Set<String> now;
		try {
			now = driver.getWindowHandles();
		} catch (RuntimeException e) {
			return null;
		}
		for (String h : now) {
			if (!handlesBefore.contains(h)) {
				return h;
			}
		}
		return null;
	}

	/** Closes the decision tab and continues in the one the click opened. */
	private static void follow(WebDriver driver, String opened, String decisionTab) {
		if (decisionTab != null) {
			driver.switchTo().window(decisionTab);
			driver.close();
		}
		driver.switchTo().window(opened);
	}
}
