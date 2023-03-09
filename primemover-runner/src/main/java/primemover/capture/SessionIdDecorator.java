package primemover.capture;

import java.util.UUID;

import primemover.model.proxy.IProxy;

/**
 * This class will decorate a Proxy-object with a specific session-id. Thus, any
 * request for the username will consider the following replacement
 * %%SESSIONID%% with the session-id
 * 
 * @author dburkolter
 *
 */
public class SessionIdDecorator implements IProxy {

	private final IProxy innerProxy;
	private final String sessionId;

	SessionIdDecorator(IProxy decorated, String prefix) {
		this.innerProxy = decorated;

		// sessionId = prefix + String.format("%06d", new Random().nextInt(1000000));
		sessionId = prefix + UUID.randomUUID().toString();
	}

	SessionIdDecorator(IProxy decorated) {
		this(decorated, "");
	}

	public IProxy getInnerProxy() {
		return this.innerProxy;
	}

	/**
	 * The current session-id of this instance
	 * 
	 * @return
	 */
	public String getSessionId() {
		return this.sessionId;
	}

	@Override
	public String getUrl() {
		return innerProxy.getUrl();
	}

	@Override
	public Integer getPort() {
		return innerProxy.getPort();
	}

	@Override
	public String getUsername() {
		return innerProxy.getUsername().replace("%%SESSIONID%%", sessionId);
	}

	@Override
	public String getPassword() {
		return innerProxy.getPassword();
	}

}
