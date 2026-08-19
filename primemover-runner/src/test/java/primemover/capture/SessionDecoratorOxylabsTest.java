package primemover.capture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import primemover.model.proxy.HttpProxy;
import primemover.model.proxy.OxylabsProxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end shape of the Oxylabs username, i.e. what actually reaches
 * {@code chainedProxyAuthorization(...)}.
 */
class SessionDecoratorOxylabsTest {

	private static SessionDecorator decorate(primemover.model.proxy.IProxy proxy, boolean oneIp) {
		SessionDecorator decorator = new SessionDecorator(proxy);
		// The @Value fields are populated by Spring in production; set directly here.
		ReflectionTestUtils.setField(decorator, "oxylabsSessionOneIp", oneIp);
		ReflectionTestUtils.setField(decorator, "oxylabsHostname", "pr.oxylabs.io");
		ReflectionTestUtils.setField(decorator, "oxylabsPort", 7777);
		return decorator;
	}

	/** A decorator with a host-level Oxylabs account configured. */
	private static SessionDecorator withHostAccount(primemover.model.proxy.IProxy proxy) {
		SessionDecorator decorator = decorate(proxy, true);
		ReflectionTestUtils.setField(decorator, "oxylabsCustomer", "acme");
		ReflectionTestUtils.setField(decorator, "oxylabsPassword", "s3cret");
		return decorator;
	}

	/** A proxy record carrying nothing but its type, as a minimal spec would produce. */
	private static OxylabsProxy bareProxy(String location) {
		OxylabsProxy proxy = new OxylabsProxy();
		proxy.setLocation(location);
		return proxy;
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
	@DisplayName("a proxy record with no credentials falls back to the host-level account")
	void fillsInHostLevelAccount() {
		SessionDecorator decorator = withHostAccount(bareProxy("US-IL-CHICAGO"));

		assertThat(decorator.getUsername())
				.isEqualTo("customer-acme-cc-US-city-chicago-sessid_oneip-" + decorator.getSessionId()
						+ "-sesstime-30");
		assertThat(decorator.getPassword()).isEqualTo("s3cret");
	}

	@Test
	@DisplayName("a bare record also inherits the host-level endpoint")
	void fillsInHostLevelEndpoint() {
		SessionDecorator decorator = withHostAccount(bareProxy("US-IL-CHICAGO"));

		assertThat(decorator.getUrl()).isEqualTo("pr.oxylabs.io");
		assertThat(decorator.getPort()).isEqualTo(7777);
	}

	@Test
	@DisplayName("credentials on the record still win over the host-level account")
	void recordCredentialsTakePrecedence() {
		SessionDecorator decorator = withHostAccount(proxy("US-IL-CHICAGO"));

		assertThat(decorator.getUsername()).startsWith("customer-acme-");
		assertThat(decorator.getPassword()).isEqualTo("secret");
		assertThat(decorator.getUrl()).isEqualTo("pr.oxylabs.io");
	}

	@Test
	@DisplayName("a missing account fails loudly rather than emitting a literal placeholder")
	void missingAccountFailsLoudly() {
		SessionDecorator decorator = decorate(bareProxy("US-IL-CHICAGO"), true);

		assertThatThrownBy(decorator::getUsername)
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("oxylabs.customer");
	}

	@Test
	@DisplayName("a missing host-level password also fails loudly")
	void missingPasswordFailsLoudly() {
		SessionDecorator decorator = decorate(bareProxy("US-IL-CHICAGO"), true);
		ReflectionTestUtils.setField(decorator, "oxylabsCustomer", "acme");

		assertThatThrownBy(decorator::getPassword)
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("oxylabs.password");
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
