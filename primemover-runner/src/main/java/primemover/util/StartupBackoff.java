package primemover.util;

import java.time.Duration;

/**
 * How long to wait before retrying a failed crawler startup.
 *
 * <p>
 * Why this exists. The startup retry loop caught its exception, logged it, and
 * immediately went round again with no delay whatsoever. That is fine for a
 * transient local failure and useless for the one that actually costs bots: the
 * Multilogin workspace allows 50 API operations per minute, and
 * {@code POST /profile/create} returns 429 once a launch burst crosses it.
 * Three retries spaced by nothing all land inside the same one-minute window, so
 * all three get the same 429 and the bot is lost before the limit has had any
 * chance to clear.
 *
 * <p>
 * Measured on ex63, experiment 147 (2026-08-28): twelve bots started in the same
 * second; {@code soak3_us_01} failed profile-create with 429 and retried at
 * 22:29:25, :30 and :36 -- the whole allowance of retries spent in eleven
 * seconds. Its queue was marked "initialisation failed" and its 284 jobs never
 * ran. Note the workspace guidance in CLAUDE.md said ~15 simultaneous launches
 * was clean; twelve was enough, so the guidance was optimistic.
 *
 * <p>
 * A rate limit is a clock, not a coin flip: the only thing that clears it is
 * time. So a 429 waits out the window, and everything else keeps a short delay
 * so a genuinely transient failure still retries promptly.
 */
public final class StartupBackoff {

	/**
	 * Substrings that identify a rate-limit rejection in an exception chain.
	 * Matched on the message rather than the type so this does not depend on
	 * which HTTP client the generated Multilogin API happens to use.
	 */
	private static final String[] RATE_LIMIT_MARKERS = { "429", "too many requests", "rate limit", "ratelimit" };

	private StartupBackoff() {
	}

	/**
	 * True when the failure, or anything that caused it, is a rate-limit
	 * rejection.
	 */
	public static boolean isRateLimited(Throwable failure) {
		int guard = 0; // cause chains can be cyclic; never spin on one
		for (Throwable t = failure; t != null && guard < 32; t = t.getCause(), guard++) {
			String message = t.getMessage();
			if (message != null) {
				String lower = message.toLowerCase();
				for (String marker : RATE_LIMIT_MARKERS) {
					if (lower.contains(marker)) {
						return true;
					}
				}
			}
			if (t.getCause() == t) {
				break;
			}
		}
		return false;
	}

	/**
	 * How long to wait before the next startup attempt.
	 *
	 * @param failure     what went wrong on the attempt just made
	 * @param normal      delay for an ordinary failure
	 * @param rateLimited delay for a 429; should exceed the limit's window
	 * @return the delay to sleep before retrying
	 */
	public static Duration afterFailure(Throwable failure, Duration normal, Duration rateLimited) {
		return isRateLimited(failure) ? rateLimited : normal;
	}
}
