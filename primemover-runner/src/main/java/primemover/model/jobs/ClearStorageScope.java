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
	 * Everything a privacy-conscious user clears: all cookies and the cache, browser-wide,
	 * plus every storage bucket of each origin the job names. Uses CDP, so unlike
	 * {@link #LOCAL} and {@link #SESSION} it works with no page loaded -- which is where
	 * this job runs, at the head of a session.
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
