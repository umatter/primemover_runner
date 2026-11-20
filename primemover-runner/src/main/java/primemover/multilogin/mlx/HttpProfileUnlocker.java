package primemover.multilogin.mlx;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Calls Multilogin's "Unlock Locked Profiles" for exactly one profile.
 *
 * <p>
 * Written by hand, not through the generated client, for one reason: the
 * endpoint is a GET that takes its profile ids in a request body
 * ({@code {"ids": ["..."]}}), and <b>without a body it unlocks every profile in
 * the workspace</b>, running ones included. The OpenAPI file declares no body, so
 * the generated {@code BrowserProfileDataApi.unlockLockedProfiles(accept)} can
 * only make the unlock-everything call. This class cannot: it refuses a blank id
 * and always sends one.
 */
public class HttpProfileUnlocker implements ProfileUnlocker {

	private static final Logger logger = LoggerFactory.getLogger(HttpProfileUnlocker.class);

	static final String PATH = "/bpds/profile/unlock_profiles";

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private final String basePath;
	private final Supplier<String> token;
	private final HttpClient http;
	private final Duration timeout;

	public HttpProfileUnlocker(String basePath, Supplier<String> token) {
		this(basePath, token, Duration.ofSeconds(20));
	}

	HttpProfileUnlocker(String basePath, Supplier<String> token, Duration timeout) {
		this.basePath = basePath.endsWith("/") ? basePath.substring(0, basePath.length() - 1) : basePath;
		this.token = token;
		this.timeout = timeout;
		this.http = HttpClient.newBuilder().connectTimeout(timeout).build();
	}

	@Override
	public boolean unlock(String profileId) {
		if (profileId == null || profileId.isBlank()) {
			// An empty id list is the unlock-everything request.
			throw new IllegalArgumentException("refusing to unlock without a profile id");
		}

		try {
			ObjectNode body = MAPPER.createObjectNode();
			body.putArray("ids").add(profileId);

			HttpRequest request = HttpRequest.newBuilder(URI.create(basePath + PATH)).timeout(timeout)
					.header("Accept", "application/json").header("Content-Type", "application/json")
					.header("Authorization", "Bearer " + token.get())
					.method("GET", HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body))).build();

			HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
			boolean ok = response.statusCode() / 100 == 2;
			if (!ok) {
				String answer = response.body() == null ? "" : response.body().replaceAll("\\s+", " ");
				logger.warn("unlocking profile {} was refused: HTTP {} {}", profileId, response.statusCode(),
						answer.length() > 200 ? answer.substring(0, 200) : answer);
			}
			return ok;
		} catch (InterruptedException ie) {
			Thread.currentThread().interrupt();
			return false;
		} catch (Exception ex) {
			logger.warn("unlocking profile {} failed: {}", profileId, ex.toString());
			return false;
		}
	}
}
