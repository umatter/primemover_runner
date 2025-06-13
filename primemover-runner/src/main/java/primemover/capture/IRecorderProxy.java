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
