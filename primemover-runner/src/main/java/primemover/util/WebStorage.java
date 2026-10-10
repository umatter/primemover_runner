package primemover.util;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;

/**
 * Clearing web storage for the current origin.
 *
 * <p>
 * Reading lives in {@link LocalStorage} and {@link SessionStorage}, which the job-data
 * recorder uses; this is the write half, kept separate so a read helper cannot be
 * mistaken for one that mutates the page.
 *
 * <p>
 * Scope is the current origin only, which is what {@code window.localStorage} addresses.
 * Storage the bot accumulated on <em>other</em> origins is untouched, so a privacy arm
 * built on this clears what the page under test can see, not the profile's whole history.
 * Clearing across origins needs CDP {@code Storage.clearDataForOrigin} per origin, and
 * cookies and IndexedDB are likewise out of scope here.
 */
public final class WebStorage {

	private WebStorage() {
	}

	/** @return the number of entries removed, so a caller can log what the clear actually did */
	public static long clearLocal(WebDriver driver) {
		return clear(driver, "localStorage");
	}

	public static long clearSession(WebDriver driver) {
		return clear(driver, "sessionStorage");
	}

	private static long clear(WebDriver driver, String store) {
		// Counted before clearing and returned, because "cleared" with no number cannot be
		// told apart from "there was nothing and the call did nothing".
		Object removed = ((JavascriptExecutor) driver).executeScript(
				"try { var s = window." + store + "; if (!s) { return -1; }"
						+ " var n = s.length; s.clear(); return n; }"
						// A cross-origin or sandboxed frame throws on access rather than
						// returning null; -1 says "could not reach the store", which is not
						// the same as "the store was empty".
						+ " catch (e) { return -1; }");
		return removed instanceof Number ? ((Number) removed).longValue() : -1L;
	}
}
