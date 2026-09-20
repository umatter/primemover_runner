package primemover.anticaptcha;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The detector has to separate "this session is looking at a block page" from
 * "this page happens to contain a captcha widget". The negative cases below are
 * the shapes that actually made the runner's naive {@code contains("captcha")}
 * check fire on ordinary pages, so they are the ones that matter.
 */
class ChallengePageDetectorTest {

	private static String fixture(String name) throws IOException {
		try (InputStream in = ChallengePageDetectorTest.class.getResourceAsStream("/challenge/" + name)) {
			assertThat(in).as("fixture " + name).isNotNull();
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	@Test
	@DisplayName("the real Google sorry page from experiment 185 is detected")
	void realGoogleSorryFixture() throws IOException {
		String source = fixture("google-sorry-exp185-job40591.html");

		assertThat(ChallengePageDetector.detect("https://www.google.com/sorry/index", source))
				.contains("google sorry page (unusual traffic)");
	}

	@Test
	@DisplayName("the saved page text alone is enough, with no URL")
	void googleSorryByTextOnly() throws IOException {
		String source = fixture("google-sorry-exp185-job40591.html");

		assertThat(ChallengePageDetector.detect(null, source))
				.contains("google sorry page (unusual traffic)");
	}

	@Test
	@DisplayName("the /sorry/ URL alone is enough, with no page source")
	void googleSorryByUrlOnly() {
		assertThat(ChallengePageDetector.detect("https://www.google.com/sorry/index?continue=x", null))
				.contains("google sorry page (unusual traffic)");
		assertThat(ChallengePageDetector.detect("https://google.co.uk/sorry/index", ""))
				.contains("google sorry page (unusual traffic)");
		assertThat(ChallengePageDetector.detect("http://www.google.de/sorry/index", null))
				.contains("google sorry page (unusual traffic)");
	}

	@Test
	@DisplayName("an ordinary google search URL is not a challenge")
	void ordinaryGoogleUrlIsNotAChallenge() {
		assertThat(ChallengePageDetector.detect("https://www.google.com/search?q=sorry+i+am+late",
				"<html><body>results</body></html>")).isEmpty();
	}

	@Test
	@DisplayName("a Cloudflare interstitial is detected by title plus challenge platform")
	void cloudflareChallenge() {
		String source = "<html><head><title>Just a moment...</title></head><body>"
				+ "<div id=\"challenge-running\"></div>"
				+ "<script src=\"/cdn-cgi/challenge-platform/h/b/orchestrate/chl_page/v1\"></script>"
				+ "</body></html>";

		assertThat(ChallengePageDetector.detect("https://example.com/", source))
				.contains("cloudflare challenge page");
	}

	@Test
	@DisplayName("the typographic ellipsis variant of the Cloudflare title also matches")
	void cloudflareChallengeEllipsis() {
		String source = "<html><head><title>Just a moment…</title></head><body>"
				+ "<form id=\"challenge-form\" action=\"/?__cf_chl_f_tk=abc\"></form></body></html>";

		assertThat(ChallengePageDetector.detect("https://example.com/", source))
				.contains("cloudflare challenge page");
	}

	@Test
	@DisplayName("the Cloudflare title without a challenge marker is not a challenge")
	void cloudflareTitleAloneIsNotEnough() {
		assertThat(ChallengePageDetector.detect("https://example.com/",
				"<html><head><title>Just a moment...</title></head><body>loading</body></html>")).isEmpty();
	}

	// --- the false positives the naive contains("captcha") check produced ---

	@Test
	@DisplayName("an invisible reCAPTCHA v3 badge is not a challenge")
	void recaptchaBadgeIsNotAChallenge() {
		String source = "<html><body><article>news</article>"
				+ "<div class=\"grecaptcha-badge\" data-style=\"bottomright\">"
				+ "<iframe src=\"https://www.google.com/recaptcha/api2/anchor?ar=1&amp;k=6Lc\"></iframe>"
				+ "</div></body></html>";

		assertThat(ChallengePageDetector.detect("https://news.example.com/article/1", source)).isEmpty();
	}

	@Test
	@DisplayName("an invisible g-recaptcha widget div is not a challenge")
	void invisibleRecaptchaWidgetIsNotAChallenge() {
		String source = "<html><body><form><div class=\"g-recaptcha g-recaptcha--invisible\" "
				+ "data-sitekey=\"6LcAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA\"></div>"
				+ "<button type=\"submit\">send</button></form></body></html>";

		assertThat(ChallengePageDetector.detect("https://shop.example.com/contact", source)).isEmpty();
	}

	@Test
	@DisplayName("a site key in page javascript is not a challenge")
	void siteKeyInScriptIsNotAChallenge() {
		String source = "<html><body><script>window.config = "
				+ "{\"recaptchaSiteKey\":\"6LcABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789\"};</script></body></html>";

		assertThat(ChallengePageDetector.detect("https://portal.example.com/", source)).isEmpty();
	}

	@Test
	@DisplayName("a YouTube video whose title merely says CAPTCHA is not a challenge")
	void captchaInAVideoTitleIsNotAChallenge() {
		String source = "<html><head><title>The Real CAPTCHA vs. The Fake One - YouTube</title></head>"
				+ "<body><h1>The Real CAPTCHA vs. The Fake One</h1></body></html>";

		assertThat(ChallengePageDetector.detect("https://www.youtube.com/watch?v=abcdefghijk", source)).isEmpty();
	}

	@Test
	@DisplayName("null and empty inputs yield nothing")
	void nullAndEmptyInputs() {
		assertThat(ChallengePageDetector.detect(null, null)).isEmpty();
		assertThat(ChallengePageDetector.detect("", "")).isEmpty();
		assertThat(ChallengePageDetector.detect("   ", "   ")).isEmpty();
	}

	@Test
	@DisplayName("matching is case insensitive on the page text")
	void caseInsensitiveText() {
		Optional<String> label = ChallengePageDetector.detect(null,
				"<p>OUR SYSTEMS HAVE DETECTED UNUSUAL TRAFFIC FROM YOUR COMPUTER NETWORK.</p>");

		assertThat(label).contains("google sorry page (unusual traffic)");
	}
}
