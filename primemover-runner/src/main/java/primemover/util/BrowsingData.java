package primemover.util;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chromium.HasCdp;
import org.openqa.selenium.remote.Augmenter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Clearing the browsing data a privacy-conscious user clears.
 *
 * <p>
 * Web storage alone is not that. The state that actually follows a user between sessions
 * is cookies first, then cache and the per-origin site storage (IndexedDB, CacheStorage,
 * service workers, WebSQL); {@code window.localStorage} is one slice of it and is reachable
 * only from a loaded page of that origin.
 *
 * <p>
 * That last point decides the mechanism. This runs at the head of a session, when the
 * browser is still on {@code about:blank} and there is no origin to address, so a
 * JavaScript clear would run against nothing. {@code Network.clearBrowserCookies} and
 * {@code Network.clearBrowserCache} take no parameters and are browser-wide, so they work
 * with no page loaded; {@code Storage.clearDataForOrigin} is given each origin explicitly.
 * A wildcard origin is not used: it is not a documented guarantee, only an implementation
 * detail that may or may not hold.
 */
public final class BrowsingData {

	private static final Logger logger = LoggerFactory.getLogger(BrowsingData.class);

	/** Every per-origin storage bucket CDP knows, which is what a "clear site data" means. */
	private static final String ALL_STORAGE_TYPES = "all";

	private BrowsingData() {
	}

	/**
	 * @return the driver as a CDP-capable one
	 * @throws IllegalStateException when the driver cannot speak CDP -- deliberately, not a
	 *         warning. This is how a privacy treatment is administered; a bot that silently
	 *         failed to clear would be an untreated bot recorded as treated, which is the
	 *         same silent-second-control-arm failure the design generator refuses elsewhere.
	 *         A failed job is visible in the queue's bad-job count; a skipped treatment is not.
	 */
	private static HasCdp cdp(WebDriver driver) {
		// A local ChromeDriver already implements HasCdp; only a RemoteWebDriver needs
		// augmenting, and Augmenter requires the driver to expose capabilities, so trying it
		// first turns a driver that was already usable into an IllegalArgumentException.
		if (driver instanceof HasCdp) {
			return (HasCdp) driver;
		}
		try {
			WebDriver augmented = new Augmenter().augment(driver);
			if (augmented instanceof HasCdp) {
				return (HasCdp) augmented;
			}
		} catch (RuntimeException e) {
			throw new IllegalStateException(
					"clear_storage needs CDP to clear cookies and site data, and this driver could not"
							+ " be augmented to speak it. Failing rather than leaving the bot untreated.", e);
		}
		throw new IllegalStateException(
				"clear_storage needs CDP to clear cookies and site data, and this driver does not"
						+ " support it. Failing rather than leaving the bot untreated.");
	}

	/** Browser-wide, no origin needed, so it works before any page has loaded. */
	public static void clearCookiesAndCache(WebDriver driver) {
		HasCdp d = cdp(driver);
		d.executeCdpCommand("Network.clearBrowserCookies", Map.of());
		d.executeCdpCommand("Network.clearBrowserCache", Map.of());
		logger.debug("cleared all browser cookies and cache");
	}

	/** Every storage bucket for one origin: IndexedDB, CacheStorage, service workers, web storage. */
	public static void clearSiteData(WebDriver driver, String origin) {
		cdp(driver).executeCdpCommand("Storage.clearDataForOrigin",
				Map.of("origin", origin, "storageTypes", ALL_STORAGE_TYPES));
		logger.debug("cleared all site data for {}", origin);
	}
}
