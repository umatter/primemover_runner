package primemover.capture;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy.Type;
import java.net.SocketAddress;
import java.util.Optional;

import javax.annotation.Nullable;

import org.openqa.selenium.Proxy;
import org.openqa.selenium.Proxy.ProxyType;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import primemover.model.proxy.IProxy;
import primemover.runner.context.IRunnerContext;
import primemover.service.IpService;

/**
 * A proxy for recording all data that is communicated while opening a webpage.
 * The proxy will need to act as a man-in-the-middle. Extra functionality is
 * added in order to verify connections, pinging connections to keep the "line"
 * open, add auth for Geosurf and also to intercept packages to hide the proxy.
 * 
 * @author Dominik
 *
 */
public class RecordingProxy {
	private static final Logger LOGGER = LoggerFactory.getLogger(RecordingProxy.class);

	@Value("${recorder.gracefullTeardownSeconds:20}")
	private Integer gracefullTeardownSeconds;

	@Value("${recorder.connectTimeoutSeconds:45}")
	private Integer connectTimeoutSeconds;

	@Value("${recorder.iddleConnectionTimeoutSeconds:60}")
	private Integer iddleConnectionTimeoutSeconds;

	@Value("${recorder.requestTimeoutSeconds:300}")
	private Integer requestTimeoutSeconds;

	@Value("${recorder.connectivityRetryCount:3}")
	private Integer connectivityRetryCount;

	@Value("${recorder.connectivityDelaySeconds:30}")
	private Integer connectivityDelaySeconds;

	@Value("${proxy.verifyConnections:true}")
	private Boolean verifyConnections;

	@Value("${proxy.pingConnections:true}")
	private Boolean pingConnections;

	@Autowired
	private KeepAlivePingFactory keepAlivePingFactory;

	@Autowired
	private UrlVerificatorFactory urlVerificatorFactory;

	@Autowired
	private IRecorderProxy recorderProxy;

	private KeepAlivePing keepAliveHelper;

	public void tearDown(boolean immediate) {
		// stopping ping
		if (keepAliveHelper != null) {
			try {
				keepAliveHelper.close();
			} catch (Exception e) {
				LOGGER.warn("failed to close keepAliveHelper", e);
			}
		}

		recorderProxy.closeProxy(immediate);
	}

	public void tearDown() {
		tearDown(false);
	}

	/**
	 * Will change the headers of the proxy
	 * 
	 * @param targetProxy
	 */
	public void reset(IProxy targetProxy, IRunnerContext<?> context) {
		tearDown();
		setup(targetProxy);
	}

	private void buildMitmProxy(IProxy targetProxy) {
		// simple decorator for usernames with session parameters
		if (!recorderProxy.initProxy(targetProxy)) {
			throw new IllegalStateException("proxy failed to initialize");
		}
	}

	private boolean verifyConnectivity() {
		if (urlVerificatorFactory == null) {
			return true;
		}

		return urlVerificatorFactory.getInstance(getProxy()).runCheck();
	}

	private void setupKeepAlive() {
		if (!pingConnections) {
			return;
		}

		this.keepAliveHelper = keepAlivePingFactory.getInstance(getProxy());
		this.keepAliveHelper.start();
	}

	protected void setup(@Nullable IProxy targetProxy) throws IllegalStateException {
		LOGGER.debug("starting recorder");

		// proxy creation without connectivity verification
		if (!verifyConnections) {
			LOGGER.debug("creating proxy without connectivity verification");
			buildMitmProxy(targetProxy);
			this.setupKeepAlive();
			return;
		}

		// proxy creation with connectivity verification
		int curRound = 1;
		while (curRound <= connectivityRetryCount) {
			LOGGER.debug("creating proxy with connectivity verification ({} of {})", curRound, connectivityRetryCount);
			buildMitmProxy(targetProxy);

			if (verifyConnectivity()) {
				LOGGER.debug("proxy started and connection verified");
				this.setupKeepAlive();
				return;
			}

			tearDown(true);

			try {
				Thread.sleep(connectivityDelaySeconds * 1000);
			} catch (InterruptedException e) {
				// nothing to do
			}
			curRound++;
		}

		LOGGER.error("proxy was not started properly");
		throw new IllegalStateException("Proxy was not started properly");
	}

	public java.net.Proxy getProxy() {
		InetAddress address = recorderProxy.getAddress();
		int port = recorderProxy.getHttpPort();

		SocketAddress socket = new InetSocketAddress(address, port);
		return new java.net.Proxy(Type.HTTP, socket);
	}

	public void applyProxy(IpService ipService) {
		java.net.Proxy javaProxy = getProxy();

		ipService.setProxy(javaProxy, this.recorderProxy.getAuthorization());
	}

	public DesiredCapabilities applyProxy(DesiredCapabilities cap) {
		LOGGER.debug("applying proxy to capabilities");

		Proxy seleniumProxy = new Proxy();

		InetAddress adr = recorderProxy.getAddress();
		String host = adr.getHostAddress();

		Optional<BasicAuthorization> auth = recorderProxy.getAuthorization();

		String httpPort = String.valueOf(recorderProxy.getHttpPort());
		String httpProxy = host + ":" + httpPort;
		if (auth.isPresent()) {
			final String authPrefix = auth.get().getUsername() + ":" + auth.get().getPassword() + "@";
			httpProxy = authPrefix + httpProxy;
		}
		seleniumProxy.setHttpProxy(httpProxy);
		Optional<Integer> optHttpsPort = recorderProxy.getHttpsPort();

		if (optHttpsPort.isPresent()) {
			String httpsPort = String
					.valueOf(recorderProxy.getHttpsPort().orElseGet(() -> recorderProxy.getHttpPort()));
			String httpsProxy = host + ":" + httpsPort;
			if (auth.isPresent()) {
				final String authPrefix = auth.get().getUsername() + ":" + auth.get().getPassword() + "@";
				httpsProxy = authPrefix + httpsProxy;
			}
			seleniumProxy.setSslProxy(httpsProxy);
		}

		seleniumProxy.setProxyType(ProxyType.MANUAL);
		cap.setCapability(CapabilityType.PROXY, seleniumProxy);
		cap.setCapability(CapabilityType.ACCEPT_INSECURE_CERTS, true);

		LOGGER.debug("setting http-proxy to {}", seleniumProxy.getHttpProxy());

		if (optHttpsPort.isPresent()) {
			LOGGER.debug("setting https-proxy to {}", seleniumProxy.getSslProxy());
		}

		return cap;
	}

	public String getType() {
		return "HTTP";
	}

	public Optional<BasicAuthorization> getAuthorization() {
		return recorderProxy.getAuthorization();
	}

	public String getIp() {
		return recorderProxy.getAddress().getHostAddress();
	}

	public Integer getHttpPort() {
		return recorderProxy.getHttpPort();
	}

	public Optional<Integer> getHttpsPort() {
		return recorderProxy.getHttpsPort();
	}

	public void newPage() {
		recorderProxy.newPage();
	}

	public void startCapturing() {
		recorderProxy.startCapturing();
	}

	public void startCapturing(String ref) {
		recorderProxy.startCapturing(ref);
	}

	public String stopCapturing() {
		return recorderProxy.stopCapturing();
	}

	boolean applyContext(IRunnerContext<?> context) {
		return this.recorderProxy.applyContext(context);
	}
}
