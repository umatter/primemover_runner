package primemover.multilogin.mlx;

import static org.junit.jupiter.api.Assertions.*;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

/**
 * The unlock endpoint without a body unlocks every profile of the workspace.
 * These pin what actually goes over the wire.
 */
class HttpProfileUnlockerTest {

	private HttpServer server;
	private final AtomicReference<String> method = new AtomicReference<>();
	private final AtomicReference<String> body = new AtomicReference<>();
	private final AtomicReference<String> auth = new AtomicReference<>();
	private final AtomicInteger calls = new AtomicInteger();
	private volatile int answer = 200;

	@BeforeEach
	void start() throws Exception {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext(HttpProfileUnlocker.PATH, exchange -> {
			calls.incrementAndGet();
			method.set(exchange.getRequestMethod());
			auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
			body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			byte[] out = "{\"status\":{\"error_code\":\"\",\"http_code\":200,\"message\":\"\"},\"data\":null}"
					.getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(answer, out.length);
			exchange.getResponseBody().write(out);
			exchange.close();
		});
		server.start();
	}

	@AfterEach
	void stop() {
		server.stop(0);
	}

	private HttpProfileUnlocker unlocker() {
		return new HttpProfileUnlocker("http://127.0.0.1:" + server.getAddress().getPort() + "/", () -> "tok",
				Duration.ofSeconds(5));
	}

	@Test
	@DisplayName("sends a GET whose body names exactly the one profile")
	void sendsTheOneId() {
		assertTrue(unlocker().unlock("5b25dfc0-ff61-45b1-af5d-b92d5eb0acfe"));
		assertEquals("GET", method.get());
		assertEquals("{\"ids\":[\"5b25dfc0-ff61-45b1-af5d-b92d5eb0acfe\"]}", body.get());
		assertEquals("Bearer tok", auth.get());
	}

	@Test
	@DisplayName("a blank id is refused before any request: no body means unlock everything")
	void blankIdNeverReachesTheWire() {
		assertThrows(IllegalArgumentException.class, () -> unlocker().unlock(" "));
		assertThrows(IllegalArgumentException.class, () -> unlocker().unlock(null));
		assertEquals(0, calls.get());
	}

	@Test
	@DisplayName("a refusal or an unreachable server is false, not an exception")
	void failureIsFalse() {
		answer = 401;
		assertFalse(unlocker().unlock("p1"));
		server.stop(0);
		assertFalse(unlocker().unlock("p1"));
	}
}
