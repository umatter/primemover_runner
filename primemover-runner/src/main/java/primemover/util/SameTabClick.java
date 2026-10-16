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
 * A link that announces a popover ({@code aria-haspopup}) does not navigate when
 * clicked: Bing's AI-answer citations are such links, and a click on one opens a
 * "Citation details" card holding a link to the same address. The job then
 * follows that link, as a person would (experiment 200, 2026-10-16: 4 of 4
 * citation clicks stayed on the results page with the card open).
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

	/** Whether the clicked element, or the link around it, announces a popover. */
	static final String OPENS_POPOVER_JS = "var el = arguments[0];"
			+ "var a = (el && el.closest) ? (el.closest('a[href]') || el) : el;"
			+ "var p = (a && a.getAttribute) ? a.getAttribute('aria-haspopup') : null;"
			+ "return p !== null && !/^\\s*false\\s*$/i.test(p);";

	/**
	 * A shown link with the clicked link's href inside a shown dialog, other than the
	 * clicked link itself; null when there is none. A hidden dialog holding the same
	 * link (Bing keeps a hidden "References" panel) is passed over.
	 */
	static final String LINK_IN_POPOVER_JS = "var el = arguments[0];"
			+ "var a = (el && el.closest) ? (el.closest('a[href]') || el) : el;"
			+ "var href = (a && a.getAttribute) ? a.getAttribute('href') : null;"
			+ "if (!href) { return null; }"
			+ "var shown = function (n) { var r = n.getBoundingClientRect(); var s = window.getComputedStyle(n);"
			+ " return r.width > 0 && r.height > 0 && s.display !== 'none' && s.visibility !== 'hidden'; };"
			+ "var ds = document.querySelectorAll('[role=\"dialog\"]');"
			+ "for (var i = 0; i < ds.length; i++) {"
			+ " if (!shown(ds[i])) { continue; }"
			+ " var ls = ds[i].querySelectorAll('a[href]');"
			+ " for (var j = 0; j < ls.length; j++) {"
			+ "  if (ls[j] !== a && ls[j].getAttribute('href') === href && shown(ls[j])) { return ls[j]; }"
			+ " }"
			+ "}"
			+ "return null;";

	/**
	 * The error code when the tab shows the browser's own network-error page, else
	 * null. Chrome serves it from {@code chrome-error://}, with {@code body.neterror},
	 * {@code #main-frame-error} and the code in {@code .error-code}.
	 */
	static final String BROWSER_ERROR_JS = "var b = document.body;"
			+ "var err = location.protocol === 'chrome-error:'"
			+ " || (!!b && !!b.classList && b.classList.contains('neterror')"
			+ "     && !!document.getElementById('main-frame-error'));"
			+ "if (!err) { return null; }"
			+ "var c = document.querySelector('.error-code');"
			+ "var t = c ? (c.textContent || '').trim() : '';"
			+ "return t || 'unknown error';";

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
	 * True when the element about to be clicked announces a popover instead of a
	 * page; never throws, since a failure here must not cost the click.
	 */
	public static boolean opensPopover(JavascriptExecutor js, WebElement element) {
		try {
			return Boolean.TRUE.equals(js.executeScript(OPENS_POPOVER_JS, element));
		} catch (RuntimeException e) {
			return false;
		}
	}

	/**
	 * The error code (e.g. {@code ERR_TUNNEL_CONNECTION_FAILED}) when the tab shows
	 * the browser's network-error page instead of the clicked site, else null. The
	 * click itself navigated, so without this the job reads as a success while the
	 * session dwells on an error page (experiment 200: 7 of 28 clicks, every one a
	 * government site the proxy refuses). Never throws.
	 */
	public static String browserErrorPage(JavascriptExecutor js) {
		try {
			Object code = js.executeScript(BROWSER_ERROR_JS);
			return code == null ? null : code.toString();
		} catch (RuntimeException e) {
			return null;
		}
	}

	/**
	 * Waits, up to {@code timeout}, for the popover a click opened to show a link to
	 * the clicked address, and returns it. Returns null as soon as the page has left
	 * {@code fromUrl} (the click navigated after all), or when no such link appeared.
	 */
	public static WebElement awaitPopoverLink(WebDriver driver, JavascriptExecutor js, WebElement clicked,
			String fromUrl, Duration timeout) throws InterruptedException {
		return awaitPopoverLink(driver, js, clicked, fromUrl, timeout, POLL);
	}

	static WebElement awaitPopoverLink(WebDriver driver, JavascriptExecutor js, WebElement clicked,
			String fromUrl, Duration timeout, Duration poll) throws InterruptedException {
		long deadline = System.nanoTime() + timeout.toNanos();
		while (true) {
			String url = YouTubePage.currentUrl(driver);
			if (url != null && !url.equals(fromUrl)) {
				return null;
			}
			try {
				Object found = js.executeScript(LINK_IN_POPOVER_JS, clicked);
				if (found instanceof WebElement) {
					return (WebElement) found;
				}
			} catch (RuntimeException e) {
				// the popover is still being built, or the page is changing: look again
			}
			if (System.nanoTime() >= deadline) {
				return null;
			}
			Thread.sleep(poll.toMillis());
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
