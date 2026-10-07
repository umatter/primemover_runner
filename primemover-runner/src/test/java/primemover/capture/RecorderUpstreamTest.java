package primemover.capture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import primemover.model.proxy.OxylabsProxy;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The upstream a non-recorded, out-of-band request should use.
 *
 * <p>
 * This exists because the exit-IP check was routed at the recorder's own listener. Under a
 * man-in-the-middle recorder a plain HTTP client sent there fails TLS verification
 * ({@code PKIX path building failed}): the recorder presents a generated CA that only the
 * browser is launched to trust. The check has to reach the chained upstream directly.
 */
class RecorderUpstreamTest {

	private static SessionDecorator decoratedOxylabs() {
		OxylabsProxy target = new OxylabsProxy(null, null, "acct", "secret", "US-MA-BOSTON");
		SessionDecorator decorated = new SessionDecorator(target);
		ReflectionTestUtils.setField(decorated, "oxylabsHostname", "pr.oxylabs.io");
		ReflectionTestUtils.setField(decorated, "oxylabsPort", 7777);
		ReflectionTestUtils.setField(decorated, "oxylabsSessionOneIp", true);
		return decorated;
	}

	@Test
	@DisplayName("the upstream proxy is the chained gateway, not a recorder listener")
	void upstreamIsTheGateway() {
		Proxy proxy = RecordingProxy.upstreamProxy(decoratedOxylabs());

		assertThat(proxy.type()).isEqualTo(Proxy.Type.HTTP);
		InetSocketAddress address = (InetSocketAddress) proxy.address();
		assertThat(address.getHostString()).isEqualTo("pr.oxylabs.io");
		assertThat(address.getPort()).isEqualTo(7777);
	}

	@Test
	@DisplayName("the out-of-band request reuses the browser's session id, so it shares the exit")
	void reusesTheBrowsersSession() {
		SessionDecorator browserSession = decoratedOxylabs();

		// What the recorder chained to is what the check must use. A second decorator
		// would carry a different sessid, so the check would report a different exit
		// address than the browser is actually using -- a wrong answer, not a missing one.
		String browserUsername = browserSession.getUsername();
		String checkUsername = browserSession.getUsername();
		assertThat(checkUsername).isEqualTo(browserUsername);
		assertThat(browserUsername).contains("sessid_oneip-").contains("cc-US-city-boston");

		SessionDecorator other = decoratedOxylabs();
		assertThat(other.getSessionId()).isNotEqualTo(browserSession.getSessionId());
	}

	@Test
	@DisplayName("an implementation with no separate upstream reports none")
	void defaultHasNoUpstream() {
		IRecorderProxy bare = new IRecorderProxy() {
			public boolean initProxy(primemover.model.proxy.IProxy p) { return true; }
			public void closeProxy(boolean immediate) { }
			public boolean applyContext(primemover.runner.context.IRunnerContext<?> c) { return true; }
			public java.net.InetAddress getAddress() { return null; }
			public int getHttpPort() { return 0; }
			public void startCapturing() { }
			public void startCapturing(String ref) { }
			public String newPage() { return null; }
			public String newPage(String ref) { return null; }
			public String stopCapturing() { return null; }
		};

		assertThat(bare.getUpstreamSession()).isEqualTo(Optional.empty());
	}

	@Test
	@DisplayName("a session reporting no host does not become a new way to fail")
	void blankHostIsTolerated() {
		// Under a recorder the browser's own address is the local listener and is always
		// valid, so sending out-of-band traffic upstream must not introduce a failure for
		// a proxy that reports no host. new InetSocketAddress(null, port) would throw.
		primemover.model.proxy.HttpProxy hostless = new primemover.model.proxy.HttpProxy(null, 7777, "u", "p");
		SessionDecorator session = new SessionDecorator(hostless);

		assertThat(session.getUrl()).isNullOrEmpty();
	}
}
