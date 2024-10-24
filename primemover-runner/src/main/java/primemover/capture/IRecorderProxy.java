package primemover.capture;

import java.net.InetAddress;
import java.util.Optional;

import javax.annotation.Nullable;

import primemover.model.proxy.IProxy;

public interface IRecorderProxy {

	void initProxy(IProxy targetProxy);

	void closeProxy(boolean immediate);

	InetAddress getAddress();

	default Optional<BasicAuthorization> getAuthorization() {
		return Optional.empty();
	}

	int getHttpPort();

	int getHttpsPort();

	public void startCapturing();

	public void startCapturing(String ref);

	public @Nullable String newPage();

	public @Nullable String newPage(String ref);

	public @Nullable String stopCapturing();

}
