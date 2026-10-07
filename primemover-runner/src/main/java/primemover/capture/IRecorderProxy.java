package primemover.capture;

import primemover.model.proxy.IProxy;
import primemover.runner.context.IRunnerContext;

import javax.annotation.Nullable;
import java.net.InetAddress;
import java.util.Optional;

public interface IRecorderProxy {

	boolean initProxy(IProxy targetProxy);

	void closeProxy(boolean immediate);

	boolean applyContext(IRunnerContext<?> context);

	InetAddress getAddress();

	default Optional<BasicAuthorization> getAuthorization() {
		return Optional.empty();
	}

	/**
	 * The upstream proxy session, for out-of-band requests that must leave through
	 * the <em>same exit address</em> as the browser but must not be recorded.
	 *
	 * <p>
	 * {@link #getAddress()} is the address the browser talks to, which for a
	 * man-in-the-middle recorder is the recorder's own local listener. A plain HTTP
	 * client sent there fails TLS verification, because the recorder presents its
	 * generated CA and only the browser is launched to trust it. This returns the
	 * chained proxy instead, so such a client reaches the internet directly.
	 *
	 * <p>
	 * It must be the <em>same</em> {@link SessionDecorator} the recorder chained to,
	 * never a fresh one: the session id in the username is what pins the exit
	 * address, so a new decorator would measure a different exit than the browser is
	 * using -- a wrong answer, which is worse than none.
	 *
	 * @return the chained session, or empty when there is no separate upstream (an
	 *         implementation that is not a man-in-the-middle already returns it from
	 *         {@link #getAddress()})
	 */
	default Optional<SessionDecorator> getUpstreamSession() {
		return Optional.empty();
	}

	int getHttpPort();

	default Optional<Integer> getHttpsPort() {
		return Optional.empty();
	}

	public void startCapturing();

	public void startCapturing(String ref);

	public @Nullable String newPage();

	public @Nullable String newPage(String ref);

	public @Nullable String stopCapturing();
}
