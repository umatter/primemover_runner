package primemover.multilogin.mlx;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.web.client.RestClientResponseException;

/**
 * Reads the error Multilogin put into a failed response.
 *
 * <p>
 * Multilogin answers a failed call with
 * {@code {"status":{"error_code":"LOCK_PROFILE_ERROR","http_code":500,"message":"can't lock profile"}}}.
 * The code is the only thing that tells a stale profile lock from a dead proxy
 * ({@code GET_PROXY_CONNECTION_IP_ERROR}): both arrive as an exception out of
 * the same start call, and both used to end as the same fixed queue message.
 */
public final class MlxErrors {

	/** Start refused because the profile is locked in Multilogin's cloud. */
	public static final String LOCK_PROFILE_ERROR = "LOCK_PROFILE_ERROR";

	private static final Pattern CODE = Pattern.compile("\"error_code\"\\s*:\\s*\"([A-Za-z0-9_]+)\"");
	private static final Pattern MESSAGE = Pattern.compile("\"message\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
	private static final int MAX_DESCRIPTION = 200;

	private MlxErrors() {
	}

	/** The Multilogin error code in this failure or one of its causes, else null. */
	public static String errorCode(Throwable failure) {
		String body = responseBody(failure);
		if (body == null) {
			return null;
		}
		Matcher m = CODE.matcher(body);
		return m.find() ? m.group(1) : null;
	}

	public static boolean isProfileLocked(Throwable failure) {
		return LOCK_PROFILE_ERROR.equals(errorCode(failure));
	}

	/**
	 * One line saying why a startup failed, for a status message: the Multilogin
	 * code and message when there is one, else the exception's own text.
	 */
	public static String describe(Throwable failure) {
		if (failure == null) {
			return null;
		}
		String text;
		String code = errorCode(failure);
		if (code != null) {
			Matcher m = MESSAGE.matcher(responseBody(failure));
			text = "Multilogin " + code + (m.find() && !m.group(1).isBlank() ? " (" + m.group(1) + ")" : "");
		} else {
			String msg = failure.getMessage();
			text = failure.getClass().getSimpleName() + (msg == null || msg.isBlank() ? "" : ": " + msg);
		}
		text = text.replaceAll("\\s+", " ").trim();
		return text.length() <= MAX_DESCRIPTION ? text : text.substring(0, MAX_DESCRIPTION - 3) + "...";
	}

	private static String responseBody(Throwable failure) {
		// Bounded: a cause chain can loop.
		Throwable t = failure;
		for (int depth = 0; t != null && depth < 10; depth++, t = t.getCause()) {
			if (t instanceof RestClientResponseException) {
				String body = ((RestClientResponseException) t).getResponseBodyAsString();
				if (body != null && !body.isBlank()) {
					return body;
				}
			}
		}
		return null;
	}
}
