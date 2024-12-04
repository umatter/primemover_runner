package primemover.capture.wiremock;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.common.ProxySettings;
import com.github.tomakehurst.wiremock.common.Slf4jNotifier;

import primemover.capture.IRecorderProxy;
import primemover.capture.ProxyDecoratorFactory;
import primemover.capture.SessionDecorator;
import primemover.model.proxy.IProxy;
import primemover.runner.context.IRunnerContext;

public class WireMockRecorderProxy implements IRecorderProxy {
	private static final Logger log = LoggerFactory.getLogger(WireMockRecorderProxy.class);

	@Value("${recorder.certificate.type}")
	private String certType;

	@Value("${recorder.certificate.path}")
	private String certPath;

	@Value("${recorder.certificate.alias}")
	private String certAlias;

	@Value("${recorder.certificate.password}")
	private String certPassword;

	@Autowired
	private ProxyDecoratorFactory sessionDecoratorFactory;

	public WireMockRecorderProxy() {
	}

	private WireMockServer wireMockServer;

	@Override
	public boolean initProxy(IProxy targetProxy) {
		SessionDecorator decoratedProxy = sessionDecoratorFactory.decorateProxy(targetProxy);
		log.debug("generated session-id: {}", decoratedProxy.getSessionId());

		ProxySettings proxySettings = new ProxySettings(decoratedProxy.getUrl(), decoratedProxy.getPort());
		proxySettings.setUsername(decoratedProxy.getUsername());
		proxySettings.setPassword(decoratedProxy.getPassword());

		wireMockServer = new WireMockServer(options() //
				.enableBrowserProxying(true) //
				.proxyPassThrough(true) //
				.proxyVia(proxySettings) //
				.preserveHostHeader(true) //
				.preserveUserAgentProxyHeader(true) //
				.dynamicPort() //
				// .dynamicHttpsPort() //
				.caKeystorePassword(certPassword) //
				.caKeystorePath("/cert/2024/ca-cert.p12") //
				.caKeystoreType(certType) //
				.disableConnectionReuse(false) //
				.notifier(new Slf4jNotifier(true)) //
				.usingFilesUnderDirectory("/"));
		wireMockServer.start();

		wireMockServer.stubFor(
				get(urlEqualTo("https://localhost/blank")).willReturn(aResponse().withBody("This is a blank page.")));

		if (wireMockServer.isHttpsEnabled()) {
			log.info("proxy started on port {} (http) and {} (https)", wireMockServer.port(),
					wireMockServer.httpsPort());
		} else {
			log.info("proxy started on port {} (http)", wireMockServer.port());

		}

		// add / remove extra header
		// stubFor(get(urlMatching(".*"))
		// .willReturn(aResponse()
		// .proxiedFrom("http://otherhost.com")
		// .withAdditionalRequestHeader("User-Agent", "Mozilla/5.0 (iPhone; U; CPU
		// iPhone)")));
		// .withRemoveRequestHeader("User-Agent")));

		return true;
	}

	@Override
	public void closeProxy(boolean immediate) {
		wireMockServer.shutdown();
	}

	@Override
	public InetAddress getAddress() {
		try {
			return InetAddress.getLocalHost();
		} catch (UnknownHostException e) {
			log.warn("Error grabing InetAddress", e);
			throw new RuntimeException("Error grabing InetAddress", e);
		}
	}

	@Override
	public int getHttpPort() {
		return wireMockServer.port();
	}

	@Override
	public Optional<Integer> getHttpsPort() {
		if (wireMockServer.isHttpsEnabled()) {
			return Optional.of(wireMockServer.httpsPort());
		}

		return Optional.empty();
	}

	@Override
	public void startCapturing() {
		// TODO Auto-generated method stub

	}

	@Override
	public void startCapturing(String ref) {
		// TODO Auto-generated method stub

	}

	@Override
	public String newPage() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String newPage(String ref) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String stopCapturing() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean applyContext(IRunnerContext<?> context) {
		return true;
	}

}
