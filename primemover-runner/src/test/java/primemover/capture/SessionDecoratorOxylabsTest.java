package primemover.capture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import primemover.model.proxy.GeoSurfProxy;
import primemover.model.proxy.OxylabsProxy;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end shape of the Oxylabs username, i.e. what actually reaches
 * {@code chainedProxyAuthorization(...)}.
 */
class SessionDecoratorOxylabsTest {

	private static SessionDecorator decorate(primemover.model.proxy.IProxy proxy, boolean oneIp) {
		SessionDecorator decorator = new SessionDecorator(proxy);
		// The @Value fields are populated by Spring in production; set directly here.
		ReflectionTestUtils.setField(decorator, "oxylabsSessionOneIp", oneIp);
		return decorator;
	}

	private static OxylabsProxy proxy(String location) {
		return new OxylabsProxy("pr.oxylabs.io", 7777, "acme", "secret", location);
	}

	@Test
	@DisplayName("location precedes the session token, matching every documented example")
	void locationPrecedesSession() {
		String username = decorate(proxy("US-CO-COLORADO_SPRINGS"), true).getUsername();

		assertThat(username).startsWith("customer-acme-cc-US-city-colorado_springs-");
		assertThat(username.indexOf("cc-US")).isLessThan(username.indexOf("sessid"));
		assertThat(username).endsWith("-sesstime-30");
	}

	@Test
	@DisplayName("sessid_oneip is used by default so a lost exit node 502s instead of silently rotating")
	void defaultsToOneIpSession() {
		String username = decorate(proxy("US-IL-CHICAGO"), true).getUsername();

		assertThat(username).contains("-sessid_oneip-");
	}

	@Test
	@DisplayName("plain sessid remains available for rotation-tolerant work")
	void plainSessidWhenConfigured() {
		String username = decorate(proxy("US-IL-CHICAGO"), false).getUsername();

		assertThat(username).contains("-sessid-").doesNotContain("sessid_oneip");
	}

	@Test
	@DisplayName("the session placeholder is substituted, never emitted literally")
	void substitutesSessionId() {
		SessionDecorator decorator = decorate(proxy("US-IL-CHICAGO"), true);

		assertThat(decorator.getUsername()) //
				.doesNotContain("%%SESSIONID%%") //
				.doesNotContain("%%SESSIONTOKEN%%") //
				.contains(decorator.getSessionId());
	}

	@Test
	@DisplayName("an untargeted proxy emits no empty location segment")
	void noEmptyLocationSegment() {
		SessionDecorator decorator = decorate(proxy(null), true);

		assertThat(decorator.getUsername())
				.isEqualTo("customer-acme-sessid_oneip-" + decorator.getSessionId() + "-sesstime-30");
	}

	@Test
	@DisplayName("the whole username is a clean token stream with no stray separators")
	void wellFormedTokenStream() {
		String username = decorate(proxy("US-CO-COLORADO_SPRINGS"), true).getUsername();

		assertThat(username).doesNotContain("--").doesNotContain("null").doesNotStartWith("-").doesNotEndWith("-");
	}

	@Test
	@DisplayName("GeoSurf is untouched - it targets via the loc header, not the username")
	void geoSurfUnaffected() {
		GeoSurfProxy geo = new GeoSurfProxy("us-30m.geosurf.io", 8000, "user", "pass", "US-MA-BOSTON");

		SessionDecorator decorator = decorate(geo, true);

		assertThat(decorator.getUsername()).isEqualTo("user");
		assertThat(geo.getLocation()).isEqualTo("US-MA-BOSTON");
	}
}
