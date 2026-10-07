package primemover.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import primemover.model.LocationInfo;
import primemover.model.ipcheck.OxylabsIpCheck;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * The exit-IP recording path.
 *
 * <p>
 * These tests exist because the path was silently dead in production: every session's
 * before/after IP recording failed, the exception was swallowed by
 * {@code TaskOperator}, and the only symptom was a job archive with no location entry
 * while the queue reported every job successful.
 */
class IpServiceTest {

	/** Shape of the backend's {@code /ip} response. */
	private static final String BACKEND_PAYLOAD = "{\"result\":{\"ip\":\"203.0.113.7\",\"type\":\"ipv4\","
			+ "\"country_code\":\"US\",\"country_name\":\"United States\",\"region_code\":\"MA\","
			+ "\"region_name\":\"Massachusetts\",\"city\":\"Boston\",\"zip\":\"02108\","
			+ "\"latitude\":42.36,\"longitude\":-71.06}}";

	/** Shape of an ip.oxylabs.io/location response, providers disagreeing on city. */
	private static final String OXYLABS_PAYLOAD = "{\"ip\":\"198.51.100.22\",\"providers\":{"
			+ "\"dbip\":{\"country\":\"US\",\"city\":\"Boston\"},"
			+ "\"ip2location\":{\"country\":\"US\",\"city\":\"Boston\"},"
			+ "\"ipinfo\":{\"country\":\"US\",\"city\":\"\"},"
			+ "\"maxmind\":{\"country\":\"US\",\"city\":\"\"}}}";

	private HttpServer server;
	private String baseUrl;

	@BeforeEach
	void startServer() throws Exception {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/ip", exchange -> respond(exchange, BACKEND_PAYLOAD));
		server.createContext("/location", exchange -> respond(exchange, OXYLABS_PAYLOAD));
		server.start();
		baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
	}

	@AfterEach
	void stopServer() {
		if (server != null) {
			server.stop(0);
		}
	}

	private static void respond(com.sun.net.httpserver.HttpExchange exchange, String body) throws java.io.IOException {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().add("Content-Type", "application/json");
		exchange.sendResponseHeaders(200, bytes.length);
		try (OutputStream out = exchange.getResponseBody()) {
			out.write(bytes);
		}
	}

	private IpService oxylabsService(String checkUrl) {
		IpService svc = service("http://127.0.0.1:1/unused");
		ReflectionTestUtils.setField(svc, "ipCheckSource", "oxylabs");
		ReflectionTestUtils.setField(svc, "oxylabsCheckUrl", checkUrl);
		return svc;
	}

	private IpService service(String endpoint) {
		IpService svc = new IpService();
		// Exactly how IpServiceFactory builds it: a plain object, never a Spring AOP
		// proxy, so @Retryable is inert and no RetryContext is bound to the thread.
		svc.setProxy(Proxy.NO_PROXY, Optional.empty());
		ReflectionTestUtils.setField(svc, "endpointPathReports", endpoint);
		return svc;
	}

	@Test
	@DisplayName("an un-proxied instance reads the location instead of throwing NPE")
	void unproxiedInstanceDoesNotThrow() {
		IpService svc = service(baseUrl + "ip");

		assertThatCode(svc::getLocationInfo).doesNotThrowAnyException();

		LocationInfo info = svc.getLocationInfo();
		assertThat(info).isNotNull();
		assertThat(info.getIp()).isEqualTo("203.0.113.7");
		assertThat(info.getCity()).isEqualTo("Boston");
		assertThat(info.getCountryCode()).isEqualTo("US");
	}

	@Test
	@DisplayName("an unreachable endpoint yields null, not an exception")
	void unreachableEndpointYieldsNull() {
		IpService svc = service("http://127.0.0.1:1/ip");

		assertThatCode(svc::getLocationInfo).doesNotThrowAnyException();
		assertThat(svc.getLocationInfo()).isNull();
	}

	@Test
	@DisplayName("the oxylabs source reads the exit address from ip.oxylabs.io/location")
	void oxylabsSourceReadsExitAddress() {
		IpService svc = oxylabsService(baseUrl + "location");

		LocationInfo info = svc.getLocationInfo();

		assertThat(info).isNotNull();
		assertThat(info.getIp()).isEqualTo("198.51.100.22");
		assertThat(info.getCountryCode()).isEqualTo("US");
		assertThat(info.getCity()).isEqualTo("boston");
	}

	@Test
	@DisplayName("a city only some providers report is still recorded")
	void mapsPayloadWhereProvidersDisagree() {
		OxylabsIpCheck check = new OxylabsIpCheck();
		check.setIp("198.51.100.22");
		OxylabsIpCheck.Provider reports = new OxylabsIpCheck.Provider();
		reports.setCountry("US");
		reports.setCity("Los Angeles");
		OxylabsIpCheck.Provider blank = new OxylabsIpCheck.Provider();
		blank.setCountry("US");
		blank.setCity("");
		check.setProviders(new java.util.LinkedHashMap<>(java.util.Map.of("maxmind", blank)));
		check.getProviders().put("dbip", reports);

		LocationInfo info = IpService.toLocationInfo(check);

		// Oxylabs targets from MaxMind, which often reports no city for an address the
		// others place. Taking only MaxMind would record no city at all.
		assertThat(info.getCity()).isEqualTo("los_angeles");
		assertThat(info.getCountryCode()).isEqualTo("US");
	}

	@Test
	@DisplayName("a null check maps to an empty LocationInfo rather than throwing")
	void nullCheckMapsToEmpty() {
		assertThatCode(() -> IpService.toLocationInfo(null)).doesNotThrowAnyException();
		assertThat(IpService.toLocationInfo(null).getIp()).isNull();
	}
}
