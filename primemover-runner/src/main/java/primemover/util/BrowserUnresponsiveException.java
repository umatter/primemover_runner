package primemover.util;

import org.openqa.selenium.TimeoutException;

/**
 * A page load timed out and the browser then answered neither
 * {@code window.stop()} nor a navigation to {@code about:blank}.
 *
 * <p>
 * On experiment 189 such a browser never came back: of 116 cases only 3 saw a
 * later job succeed, and every later visit cost ~4 minutes of its own timeout
 * before failing the same way. Distinct from a plain {@link TimeoutException}
 * (the page was slow, the browser is fine) so the job loop can end the session
 * instead of feeding it more jobs.
 */
public class BrowserUnresponsiveException extends TimeoutException {

	private static final long serialVersionUID = 1L;

	public BrowserUnresponsiveException(String message, Throwable cause) {
		super(message, cause);
	}

	/** True when this throwable, or anything in its cause chain, is one. */
	public static boolean isCausedBy(Throwable t) {
		for (int depth = 0; t != null && depth < 20; depth++, t = t.getCause()) {
			if (t instanceof BrowserUnresponsiveException) {
				return true;
			}
		}
		return false;
	}
}
