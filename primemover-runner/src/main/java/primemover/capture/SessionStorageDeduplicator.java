package primemover.capture;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Decides, job by job within one session, whether the session storage is
 * stored in full or as a {@link SessionStorageRef} to the job that already
 * holds it.
 *
 * <p>
 * Two rules keep every pointer resolvable. A pointer always names the job that
 * stored the content in full, never another pointer. And a full copy becomes
 * that reference only once its job's data was written ({@link #written}): a job
 * whose write failed is never pointed at, so the next identical job stores in
 * full again. One instance per session (per {@code TaskOperator}), used from
 * that session's single thread.
 */
public final class SessionStorageDeduplicator {

	/** The last full copy whose write succeeded: what pointers name. */
	private @Nullable Held reference;
	/** The full copy of the job in progress, until its write is known. */
	private @Nullable Held pending;

	private static final class Held {
		final long jobId;
		final String object;
		final String sha256;

		Held(long jobId, String object, String sha256) {
			this.jobId = jobId;
			this.object = object;
			this.sha256 = sha256;
		}
	}

	/**
	 * @param jobId   the job whose session storage this is
	 * @param object  that job's {@code static-jobdata} object identifier
	 * @param storage the session storage JSON, or {@code null} when unreadable
	 * @return a pointer when the content equals the current reference; empty when
	 *         it is to be stored in full (or is {@code null}, which is left alone)
	 */
	public Optional<SessionStorageRef> check(long jobId, String object, @Nullable String storage) {
		pending = null;
		if (storage == null) {
			return Optional.empty();
		}

		String sha = sha256(storage);
		if (reference != null && reference.sha256.equals(sha)) {
			return Optional.of(new SessionStorageRef(reference.jobId, reference.object, sha));
		}

		pending = new Held(jobId, object, sha);
		return Optional.empty();
	}

	/** Report whether {@code jobId}'s data was written; only then can its full copy be pointed at. */
	public void written(long jobId, boolean success) {
		if (pending != null && pending.jobId == jobId && success) {
			reference = pending;
		}
		pending = null;
	}

	private static String sha256(String content) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is always available", e);
		}
	}
}
