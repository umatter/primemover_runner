package primemover.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The YouTube-only steps of a lambda select may be skipped only on a page known to
 * be elsewhere. Anything the check cannot read keeps the YouTube behaviour, so no
 * YouTube study changes because of it.
 */
class YouTubePageTest {

	@ParameterizedTest
	@ValueSource(strings = {
			"https://www.bing.com/search?q=affordable+care+act&form=QBLH",
			"https://search.yahoo.com/search?p=immigration+policy&fr=none",
			"https://www.bing.com/search?q=youtube.com",
			"https://youtube.com.example.org/watch?v=abc",
			"http://notyoutube.com/watch?v=abc",
			"https://www.dailykos.com/",
			"https://www.bing.com/search?q=a|b{c}" })
	@DisplayName("a web page on another host is off YouTube")
	void otherHostsAreOffYouTube(String url) {
		assertTrue(YouTubePage.isOffYouTube(url), url);
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"https://www.youtube.com/watch?v=dQw4w9WgXcQ",
			"https://youtube.com/",
			"https://m.youtube.com/watch?v=dQw4w9WgXcQ",
			"https://music.youtube.com/",
			"HTTPS://WWW.YOUTUBE.COM/results?search_query=news",
			"https://www.youtube.com:443/watch?v=dQw4w9WgXcQ" })
	@DisplayName("youtube.com and its subdomains are YouTube")
	void youTubeIsNotOff(String url) {
		assertFalse(YouTubePage.isOffYouTube(url), url);
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "about:blank", "data:,", "chrome-error://chromewebdata/", "not a url" })
	@DisplayName("a page the check cannot place is not called off YouTube")
	void unknownPagesKeepTheYouTubeBehaviour(String url) {
		assertFalse(YouTubePage.isOffYouTube(url), String.valueOf(url));
	}

	@Test
	@DisplayName("the navigate-by-id fallback exists only for a decision made on YouTube")
	void watchUrlOnlyOnYouTube() {
		assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ",
				YouTubePage.watchUrlFor("https://www.youtube.com/watch?v=prev0000000", "dQw4w9WgXcQ"));
		// websearch_v1's selected id is a digest of the clicked result's URL; on a
		// Bing page it must never become a YouTube address.
		assertNull(YouTubePage.watchUrlFor("https://www.bing.com/search?q=x",
				"5f2b9c0e8d7a41b6a3c2e1f0d9b8a7c6"));
		assertNull(YouTubePage.watchUrlFor("https://www.youtube.com/watch?v=prev0000000", " "));
		assertNull(YouTubePage.watchUrlFor("https://www.youtube.com/watch?v=prev0000000", null));
		// An unknown page keeps the old behaviour.
		assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ",
				YouTubePage.watchUrlFor(null, "dQw4w9WgXcQ"));
	}
}
