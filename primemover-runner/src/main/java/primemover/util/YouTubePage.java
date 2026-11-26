package primemover.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.openqa.selenium.WebDriver;

/**
 * Whether the page a lambda select is made on is YouTube's.
 *
 * <p>
 * Two steps of a lambda select were written for YouTube and ran on every page: the
 * watch-page anchor ({@link WatchPageAnchor}), which waits for the recommendation
 * tiles and reloads the page when they are missing, and the navigate-by-id fallback,
 * which opens {@code youtube.com/watch?v=} plus the decision function's selected id
 * when the chosen element cannot be clicked. On a Bing results page (experiment 199,
 * pm-vm, 2026-10-12) the anchor waited 10 s, reloaded the page -- sending the search
 * to Bing a second time -- and waited 15 s more, so the click was chosen on the
 * reload; and the fallback would have sent the bot to a YouTube address built from
 * websearch_v1's selected id, which is a digest of the result's URL.
 *
 * <p>
 * Only a page known to be elsewhere counts as elsewhere: an http(s) URL whose host is
 * neither youtube.com nor a subdomain of it. A URL that is missing or of another
 * scheme -- a driver that could not answer, Chrome's own error page -- keeps the
 * YouTube behaviour, so no YouTube study changes because of this.
 */
public final class YouTubePage {

	/** Scheme and host, read leniently: a search URL is not always a valid URI. */
	private static final Pattern HTTP_HOST = Pattern.compile("^\\s*(?i:https?)://([^/:?#\\s]+)");

	private YouTubePage() {
	}

	/** True only for an http(s) page whose host is not youtube.com or a subdomain of it. */
	public static boolean isOffYouTube(String url) {
		if (url == null) {
			return false;
		}
		Matcher m = HTTP_HOST.matcher(url);
		if (!m.find()) {
			return false;
		}
		String host = m.group(1).toLowerCase(Locale.ROOT);
		return !(host.equals("youtube.com") || host.endsWith(".youtube.com"));
	}

	/**
	 * The watch page to fall back to for a selected video id, or null when there is
	 * no id or the decision was made on a page known to be off YouTube.
	 */
	public static String watchUrlFor(String pageUrl, String videoId) {
		if (videoId == null || videoId.isBlank() || isOffYouTube(pageUrl)) {
			return null;
		}
		return "https://www.youtube.com/watch?v=" + videoId;
	}

	/** The driver's current URL, or null when it cannot say. */
	public static String currentUrl(WebDriver driver) {
		try {
			return driver.getCurrentUrl();
		} catch (Exception e) {
			return null;
		}
	}
}
