package primemover.model.jobs;

import java.util.Locale;

/**
 * What a {@link ClearStorageJob} wipes.
 *
 * <p>
 * {@link #NONE} exists so that an experiment's control arm can run the <em>same</em>
 * workflow as its treated arm. The alternative -- giving only the treated arm the job --
 * makes the arms differ in job count and session duration as well as in storage, which
 * confounds the manipulation with the shape of the session. A control bot therefore runs
 * the job and clears nothing.
 */
public enum ClearStorageScope {
	/** Clear nothing. The control arm's value; see the class javadoc. */
	NONE,
	/** The current origin's localStorage, via JavaScript. Needs a loaded page of that origin. */
	LOCAL,
	/** The current origin's sessionStorage, via JavaScript. Needs a loaded page of that origin. */
	SESSION,
	/**
	 * Everything this bot's current origin has stored about it: its cookies, its
	 * localStorage and its sessionStorage.
	 *
	 * <p>
	 * Current origin, not browser-wide. WebDriver's {@code deleteAllCookies} removes the
	 * cookies of the current document's domain, and web storage is reachable only from a
	 * loaded page of that origin. Clearing browser-wide in one call would need the Chrome
	 * DevTools Protocol, which Multilogin's driver does not implement. The workflow
	 * therefore visits each origin before clearing it.
	 */
	ALL;

	/** Parse a behaviour value; null, blank or unrecognised is a hard failure, never a silent NONE. */
	public static ClearStorageScope parse(String value) {
		if (value == null || value.trim().isEmpty()) {
			throw new IllegalArgumentException("clear_storage needs a scope: one of none, local, session, all");
		}
		try {
			return valueOf(value.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			// Defaulting here would turn a typo into a bot that silently keeps its storage
			// while the experiment record says it was cleared.
			throw new IllegalArgumentException(
					"clear_storage scope '" + value + "' is not one of none, local, session, all", e);
		}
	}
}
