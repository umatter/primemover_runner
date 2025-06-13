package primemover.anticaptcha;

import primemover.model.proxy.IProxy;

import javax.annotation.Nonnull;

public class FeedbackProxy implements AutoCloseable {
	private int port;
	private @Nonnull FeedbackProxyFactory fac;
	private @Nonnull IProxy target;

	protected FeedbackProxy(FeedbackProxyFactory fac, int port, IProxy target) {
		if (port <= 0 || port > 65535) {
			throw new IllegalArgumentException("port must be >0 and <65536");
		}

		this.port = port;
		this.fac = fac;
		this.target = target;
	}

	public int getPort() {
		return port;
	}

	@Override
	public void close() throws Exception {
		fac.releasePort(this);
	}

}
