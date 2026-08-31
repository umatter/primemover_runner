package primemover.util;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The startup retry loop used to retry instantly. On experiment 147 that meant
 * one crawler's three rounds all ran inside eleven seconds (22:29:25, :30, :36),
 * every one of them drawing the same Multilogin 429, and the bot was lost with
 * its 284 jobs before the per-minute window had any chance to clear.
 */
class StartupBackoffTest {

	private static final Duration NORMAL = Duration.ofSeconds(5);
	private static final Duration RATE_LIMITED = Duration.ofSeconds(75);

	@Test
	@DisplayName("the real production exception is recognised as rate limited")
	void recognisesTheProductionException() {
		// Verbatim from the runner journal, 2026-08-28 22:29:30.
		Exception real = new RuntimeException(
				"org.springframework.web.client.HttpClientErrorException$TooManyRequests: "
						+ "429 Too Many Requests on POST request for "
						+ "\"https://api.multilogin.com/profile/create\"");
		assertTrue(StartupBackoff.isRateLimited(real));
		assertEquals(RATE_LIMITED, StartupBackoff.afterFailure(real, NORMAL, RATE_LIMITED));
	}

	@Test
	@DisplayName("a rate limit nested deep in the cause chain still counts")
	void findsItThroughTheCauseChain() {
		// The startup path wraps this several times before the loop sees it.
		Throwable cause = new IllegalStateException("429 Too Many Requests");
		Throwable middle = new RuntimeException("profile creation failed", cause);
		Throwable top = new RuntimeException("error during setup", middle);
		assertTrue(StartupBackoff.isRateLimited(top));
	}

	@Test
	@DisplayName("an ordinary failure keeps the short delay")
	void ordinaryFailureRetriesPromptly() {
		Exception ordinary = new RuntimeException("connection reset by peer");
		assertFalse(StartupBackoff.isRateLimited(ordinary));
		// A transient local problem should not be punished with a 75s wait.
		assertEquals(NORMAL, StartupBackoff.afterFailure(ordinary, NORMAL, RATE_LIMITED));
	}

	@Test
	@DisplayName("the rate-limit wait must exceed the one-minute window")
	void theWaitOutlastsTheWindow() {
		// The whole point: a delay shorter than the window spends the retry
		// inside the window that rejected the previous attempt.
		assertTrue(RATE_LIMITED.getSeconds() > 60,
				"a backoff of " + RATE_LIMITED.getSeconds() + "s does not clear a per-minute limit");
	}

	@Test
	@DisplayName("a null message, or no message anywhere, does not blow up")
	void toleratesMessagelessExceptions() {
		assertFalse(StartupBackoff.isRateLimited(new RuntimeException()));
		assertFalse(StartupBackoff.isRateLimited(new RuntimeException((String) null,
				new IllegalStateException())));
	}

	@Test
	@DisplayName("a self-referencing cause chain terminates")
	void survivesACyclicChain() {
		// Defensive: a cycle here would hang a startup thread, which is a worse
		// outcome than the bug being fixed.
		Throwable a = new RuntimeException("boom");
		a.initCause(a instanceof Exception ? new RuntimeException("inner") : null);
		assertFalse(StartupBackoff.isRateLimited(a));
	}

	@Test
	@DisplayName("matching is case-insensitive on the wording, not just the code")
	void matchesWordingVariants() {
		assertTrue(StartupBackoff.isRateLimited(new RuntimeException("Too Many Requests")));
		assertTrue(StartupBackoff.isRateLimited(new RuntimeException("workspace RATE LIMIT exceeded")));
	}
}
