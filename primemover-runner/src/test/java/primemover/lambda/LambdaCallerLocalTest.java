package primemover.lambda;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The local-mode half of {@link LambdaCaller}: what it does when the decision
 * server answers badly, and what it does when it does not answer at all.
 *
 * <p>
 * Both behaviours were missing until 2026-08-27.
 *
 * <ul>
 * <li>The non-2xx branch was written as an {@code if} on the returned status,
 * which never ran: {@code RestTemplate} raises {@link
 * org.springframework.web.client.HttpStatusCodeException} first. So the lambda
 * server's {@code {"error": ...}} body -- the only text that says why a decision
 * failed -- reached no log line and no status_message.</li>
 * <li>The client was a bare {@code new RestTemplate()}, which has no timeouts at
 * all. One wedged Flask worker held a browser thread forever, and there are only
 * {@code runner.MaxParallelExecution} of those.</li>
 * </ul>
 */
class LambdaCallerLocalTest {

	private HttpServer server;
	private String url;

	/** Headers of the last request the stub server received. */
	private final AtomicReference<Headers> lastRequestHeaders = new AtomicReference<>();

	/** Released in teardown, so a stalling stub never outlives its test. */
	private final CountDownLatch release = new CountDownLatch(1);

	@AfterEach
	void stopServer() {
		release.countDown();
		if (server != null) {
			server.stop(0);
			server = null;
		}
	}

	/**
	 * Start a stub decision server on an ephemeral loopback port.
	 *
	 * @param stall when true, answer only once the test is over -- a stand-in for
	 *              the wedged Flask worker the read timeout exists for
	 */
	private void startServer(int status, String body, boolean stall) throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/invoke", exchange -> {
			lastRequestHeaders.set(exchange.getRequestHeaders());
			if (stall) {
				try {
					release.await(30, TimeUnit.SECONDS);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}

			byte[] payload = body.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			try {
				exchange.sendResponseHeaders(status, payload.length);
				try (OutputStream out = exchange.getResponseBody()) {
					out.write(payload);
				}
			} catch (IOException closed) {
				// the client gave up first; that is the point of the timeout test
			}
		});
		server.start();
		url = "http://127.0.0.1:" + server.getAddress().getPort() + "/invoke";
	}

	private LambdaCaller localCaller(int readTimeoutSeconds) {
		LambdaCaller caller = new LambdaCaller();
		ReflectionTestUtils.setField(caller, "lambdaMode", "local");
		ReflectionTestUtils.setField(caller, "localLambdaUrl", url);
		ReflectionTestUtils.setField(caller, "localConnectTimeoutSeconds", 5);
		ReflectionTestUtils.setField(caller, "localReadTimeoutSeconds", readTimeoutSeconds);
		return caller;
	}

	@Test
	@DisplayName("a 200 is passed through unchanged")
	void successfulCallReturnsThePayload() throws Exception {
		startServer(200, "{\"xpath\": \"//a\", \"selection_mode\": \"llm\"}", false);

		LambdaInvokeResult result = localCaller(30).invoke("bot-decision-yt", "{}");

		assertThat(result.getStatusCode()).isEqualTo(200);
		assertThat(result.getPayload()).contains("selection_mode");
		assertThat(result.getFunctionError()).isNull();
	}

	@Test
	@DisplayName("a 500 carries the server's error body into the exception message")
	void serverErrorBodyReachesTheException() throws Exception {
		// This body is what the job's status_message has to end up quoting: without it
		// the failure reads "Failure calling local Lambda function" and stops there.
		startServer(500, "{\"error\": \"select_recommendation raised KeyError: 'channel'\"}", false);

		assertThatThrownBy(() -> localCaller(30).invoke("bot-decision-yt", "{}"))
				.isInstanceOf(LambdaException.class)
				.hasMessageContaining("HTTP 500")
				.hasMessageContaining("KeyError")
				.hasMessageContaining(url);
	}

	@Test
	@DisplayName("a 4xx is reported the same way, naming the url that failed")
	void clientErrorIsReportedToo() throws Exception {
		startServer(404, "{\"error\": \"no such endpoint\"}", false);

		assertThatThrownBy(() -> localCaller(30).invoke("bot-decision-yt", "{}"))
				.isInstanceOf(LambdaException.class)
				.hasMessageContaining("HTTP 404")
				.hasMessageContaining("no such endpoint")
				.hasMessageContaining(url);
	}

	@Test
	@DisplayName("a server that never answers is abandoned at the read timeout, not waited on forever")
	void readTimeoutIsEnforced() throws Exception {
		startServer(200, "{\"xpath\": \"//a\"}", true);

		long started = System.currentTimeMillis();
		assertThatThrownBy(() -> localCaller(1).invoke("bot-decision-yt", "{}"))
				.isInstanceOf(LambdaException.class)
				.hasMessageContaining("did not answer")
				.hasMessageContaining("readTimeout=1s");
		long elapsed = System.currentTimeMillis() - started;

		assertThat(elapsed)
				.as("must give up at the configured timeout rather than pinning the thread")
				.isLessThan(10_000L);
	}

	@Test
	@DisplayName("a caller built outside Spring still gets bounded timeouts, never an infinite wait")
	void uninjectedTimeoutsFallBackToTheDefaults() throws Exception {
		// An uninjected int is 0, and 0 means "wait forever" to the request factory --
		// which is the very failure being fixed here.
		startServer(200, "{\"xpath\": \"//a\"}", false);

		LambdaCaller caller = new LambdaCaller();
		ReflectionTestUtils.setField(caller, "lambdaMode", "local");
		ReflectionTestUtils.setField(caller, "localLambdaUrl", url);

		assertThat(caller.invoke("bot-decision-yt", "{}").getStatusCode()).isEqualTo(200);
		assertThat(ReflectionTestUtils.getField(caller, "localReadTimeout")).isEqualTo(Duration.ofSeconds(480));
		assertThat(ReflectionTestUtils.getField(caller, "localConnectTimeout")).isEqualTo(Duration.ofSeconds(5));
	}

	@Test
	@DisplayName("the timeouts are configurable, and their documented defaults are the ones in the code")
	void timeoutsAreConfigurable() throws Exception {
		assertThat(valueAnnotationOf("localConnectTimeoutSeconds"))
				.isEqualTo("${lambda.local.connectTimeoutSeconds:5}");
		// 480s: the decision function allows OpenAI openai_api_timeout_secs=60 with
		// openai_api_max_retries=1, i.e. 120s per call site, and abandons the rest once
		// a failure is classified fatal -- roughly two call sites' worth, 240s, plus
		// page parsing. ~300s worst legitimate case, so 480s cuts off nothing that
		// works while still bounding a wedged worker. The 300s first set here came
		// from an arithmetic that dropped the retry term.
		assertThat(valueAnnotationOf("localReadTimeoutSeconds"))
				.isEqualTo("${lambda.local.readTimeoutSeconds:480}");
	}

	@Test
	@DisplayName("the job's correlation id travels with the call, so the server's logs can be joined to it")
	void correlationIdIsSentAsAHeader() throws Exception {
		// The decision server logs every request under X-Correlation-Id
		// (orchestrator/lambda_service/server.py::_correlation_id). Without this header
		// it generates its own id, which appears nowhere else -- so the server's half
		// of a decision cannot be tied to the job that asked for it.
		startServer(200, "{\"xpath\": \"//a\"}", false);

		MDC.put("correlationId", "cr-14|q-4711|j-3828");
		try {
			localCaller(30).invoke("bot-decision-yt", "{}");
		} finally {
			MDC.remove("correlationId");
		}

		assertThat(lastRequestHeaders.get().getFirst("X-Correlation-Id")).isEqualTo("cr-14|q-4711|j-3828");
	}

	@Test
	@DisplayName("with no composite id in the MDC, one is assembled from the individual keys")
	void correlationIdFallsBackToTheIndividualMdcKeys() throws Exception {
		startServer(200, "{\"xpath\": \"//a\"}", false);

		MDC.put("crawler.id", "14");
		MDC.put("joblist.id", "4711");
		MDC.put("job.id", "3828");
		try {
			localCaller(30).invoke("bot-decision-yt", "{}");
		} finally {
			MDC.clear();
		}

		assertThat(lastRequestHeaders.get().getFirst("X-Correlation-Id")).isEqualTo("cr-14|q-4711|j-3828");
	}

	@Test
	@DisplayName("outside a job the header is simply omitted, never sent empty")
	void noCorrelationIdMeansNoHeader() throws Exception {
		startServer(200, "{\"xpath\": \"//a\"}", false);

		MDC.clear();
		localCaller(30).invoke("bot-decision-yt", "{}");

		assertThat(lastRequestHeaders.get().getFirst("X-Correlation-Id")).isNull();
	}

	@Test
	@DisplayName("the function name travels with the call, so the server can dispatch to it")
	void functionNameIsSentAsAHeader() throws Exception {
		startServer(200, "{\"xpath\": \"//a\"}", false);

		localCaller(30).invoke("bot-decision-de", "{}");

		assertThat(lastRequestHeaders.get().getFirst("X-Function-Name")).isEqualTo("bot-decision-de");
	}

	@Test
	@DisplayName("a blank function name sends no header, so an old or unconfigured server sees today's behaviour")
	void blankFunctionNameMeansNoHeader() throws Exception {
		startServer(200, "{\"xpath\": \"//a\"}", false);

		localCaller(30).invoke("  ", "{}");

		assertThat(lastRequestHeaders.get().getFirst("X-Function-Name")).isNull();
	}

	@Test
	@DisplayName("a null function name sends no header either")
	void nullFunctionNameMeansNoHeader() throws Exception {
		startServer(200, "{\"xpath\": \"//a\"}", false);

		localCaller(30).invoke(null, "{}");

		assertThat(lastRequestHeaders.get().getFirst("X-Function-Name")).isNull();
	}

	@Test
	@DisplayName("the function name header travels alongside the correlation header, not instead of it")
	void functionNameAndCorrelationIdTravelTogether() throws Exception {
		startServer(200, "{\"xpath\": \"//a\"}", false);

		MDC.put("correlationId", "cr-14|q-4711|j-3828");
		try {
			localCaller(30).invoke("bot-decision-yt", "{}");
		} finally {
			MDC.remove("correlationId");
		}

		Headers headers = lastRequestHeaders.get();
		assertThat(headers.getFirst("X-Function-Name")).isEqualTo("bot-decision-yt");
		assertThat(headers.getFirst("X-Correlation-Id")).isEqualTo("cr-14|q-4711|j-3828");
	}

	@Test
	@DisplayName("a correlation id is never blank or malformed on the wire")
	void correlationIdIsSingleLineAndUnpunctuated() {
		MDC.clear();
		MDC.put("crawler.id", "14");
		MDC.put("job.id", "3828");
		try {
			String id = LambdaCaller.currentCorrelationId();

			// logback renders the MDC comma-separated and promtail scrapes
			// correlationId=(?P<correlation_id>[^\s,]+)
			assertThat(id).isEqualTo("cr-14|j-3828");
			assertThat(id).doesNotContain(",").doesNotContain(" ");
		} finally {
			MDC.clear();
		}
	}

	private static String valueAnnotationOf(String fieldName) throws NoSuchFieldException {
		Field field = LambdaCaller.class.getDeclaredField(fieldName);
		Value annotation = field.getAnnotation(Value.class);
		assertThat(annotation).as("%s should be injected via @Value", fieldName).isNotNull();
		return annotation.value();
	}
}
