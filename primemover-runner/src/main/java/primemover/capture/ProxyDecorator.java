package primemover.capture;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;

import io.opentelemetry.api.internal.StringUtils;
import primemover.model.proxy.GeoSurfProxy;
import primemover.model.proxy.IProxy;

/**
 * This class will decorate a Proxy-object with a specific session-id. Thus, any
 * request for the username will consider the following replacement
 * %%SESSIONID%% with the session-id
 * 
 * @author dburkolter
 *
 */
public class ProxyDecorator implements IProxy {

	@Value("${geosurf.overwrite.user:}")
	private static String geosurfUserOverwrite;
	@Value("${geosurf.overwrite.password:}")
	private static String geosurfPasswordOverwrite;

	private final IProxy innerProxy;
	private final String sessionId;

	ProxyDecorator(IProxy decorated, String prefix) {
		this.innerProxy = decorated;

		// sessionId = prefix + String.format("%06d", new Random().nextInt(1000000));
		sessionId = prefix + UUID.randomUUID().toString();
	}

	ProxyDecorator(IProxy decorated) {
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
		if (!StringUtils.isNullOrEmpty(geosurfUserOverwrite) && innerProxy instanceof GeoSurfProxy) {
			return geosurfUserOverwrite.replace("%%SESSIONID%%", sessionId);
		} else {
			return innerProxy.getUsername().replace("%%SESSIONID%%", sessionId);
		}
	}

	@Override
	public String getPassword() {
		if (!StringUtils.isNullOrEmpty(geosurfPasswordOverwrite) && innerProxy instanceof GeoSurfProxy) {
			return geosurfPasswordOverwrite;
		} else {
			return innerProxy.getPassword();
		}
	}

}
