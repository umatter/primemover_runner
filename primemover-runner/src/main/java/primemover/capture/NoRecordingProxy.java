package primemover.capture;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import primemover.model.proxy.IProxy;
import primemover.runner.context.IRunnerContext;

public class NoRecordingProxy implements IRecorderProxy {

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
	private ProxyDecoratorFactory sessionDecoratorFactory;

	private SessionDecorator proxy;

	IRunnerContext<?> context;

	@Override
	public boolean initProxy(IProxy targetProxy) {
		if (targetProxy == null) {
			return true;
		}
		proxy = sessionDecoratorFactory.decorateProxy(targetProxy);

		return true;
	}

	@Override
	public Optional<BasicAuthorization> getAuthorization() {
		return Optional.of(new BasicAuthorization(proxy.getUsername(), proxy.getPassword()));
	}

	@Override
	public void closeProxy(boolean immediate) {
		// nothing to do
	}

	@Override
	public InetAddress getAddress() {
		try {
			return InetAddress.getByName(proxy.getUrl());
		} catch (UnknownHostException e) {
			throw new RuntimeException("invalid proxy url", e);
		}
	}

	@Override
	public int getHttpPort() {
		return proxy.getPort().intValue();
	}

	@Override
	public Optional<Integer> getHttpsPort() {
		return Optional.of(proxy.getPort().intValue());
	}

	@Override
	public void startCapturing() {
		// no recording
	}

	@Override
	public void startCapturing(String ref) {
		// no recording
	}

	@Override
	public String newPage() {
		// no recording
		return null;
	}

	@Override
	public String newPage(String ref) {
		// no recording
		return null;
	}

	@Override
	public String stopCapturing() {
		// no recording
		return null;
	}

	@Override
	public boolean applyContext(IRunnerContext<?> context) {
		this.context = context;

		return true;
	}

}
