package primemover.multilogin.mlx;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

class MlxErrorsTest {

	// The two bodies the launcher returned in experiment 240, verbatim.
	static final String LOCKED = "{\"status\":{\"error_code\":\"LOCK_PROFILE_ERROR\",\"http_code\":500,"
			+ "\"message\":\"can't lock profile\"}}";
	static final String PROXY = "{\"status\":{\"error_code\":\"GET_PROXY_CONNECTION_IP_ERROR\",\"http_code\":400,"
			+ "\"message\":\"couldn't get proxy connection ip data\"}}";

	static HttpServerErrorException locked() {
		return HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
				HttpHeaders.EMPTY, LOCKED.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
	}

	static HttpClientErrorException proxyDead() {
		return HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "Bad Request", HttpHeaders.EMPTY,
				PROXY.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
	}

	@Test
	@DisplayName("a locked profile is recognised, also when wrapped")
	void lockedIsRecognised() {
		assertTrue(MlxErrors.isProfileLocked(locked()));
		assertTrue(MlxErrors.isProfileLocked(new RuntimeException("setup", locked())));
	}

	@Test
	@DisplayName("a dead proxy and an unrelated failure are not a locked profile")
	void othersAreNotLocked() {
		assertFalse(MlxErrors.isProfileLocked(proxyDead()));
		assertEquals("GET_PROXY_CONNECTION_IP_ERROR", MlxErrors.errorCode(proxyDead()));
		assertFalse(MlxErrors.isProfileLocked(new RuntimeException("failed to update proxy")));
		assertNull(MlxErrors.errorCode(null));
	}

	@Test
	@DisplayName("the description names the code, so the two failures read differently")
	void descriptionNamesTheCode() {
		assertEquals("Multilogin LOCK_PROFILE_ERROR (can't lock profile)", MlxErrors.describe(locked()));
		assertEquals("Multilogin GET_PROXY_CONNECTION_IP_ERROR (couldn't get proxy connection ip data)",
				MlxErrors.describe(new RuntimeException("x", proxyDead())));
	}

	@Test
	@DisplayName("a failure without a Multilogin body is described by its own text, on one bounded line")
	void plainFailure() {
		assertEquals("RuntimeException: failed to update proxy",
				MlxErrors.describe(new RuntimeException("failed to update proxy")));
		String longOne = MlxErrors.describe(new IllegalStateException("a\nb " + "x".repeat(500)));
		assertFalse(longOne.contains("\n"));
		assertTrue(longOne.length() <= 200);
	}
}
