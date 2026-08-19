package primemover.capture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import primemover.model.proxy.HttpProxy;
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
	@DisplayName("a plain HTTP proxy is untouched - no tokens, no placeholders")
	void plainProxyUnaffected() {
		HttpProxy plain = new HttpProxy("proxy.example.com", 8080, "user", "pass");

		assertThat(decorate(plain, true).getUsername()).isEqualTo("user");
	}

	@Test
	@DisplayName("the session id uses only characters Oxylabs documents for sessid (0-9, A-Z)")
	void sessionIdIsWithinDocumentedCharset() {
		SessionDecorator decorator = decorate(proxy("US-IL-CHICAGO"), true);

		// A hyphen would be worse than a wrong case: the whole username is a
		// hyphen-delimited token stream, so one inside the value would corrupt it.
		assertThat(decorator.getSessionId()).matches("[0-9A-Z]+");
	}
}
