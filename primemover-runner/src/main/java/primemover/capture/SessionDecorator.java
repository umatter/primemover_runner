package primemover.capture;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;

import io.opentelemetry.api.internal.StringUtils;
import primemover.model.proxy.GeoSurfProxy;
import primemover.model.proxy.IProxy;
import primemover.model.proxy.OxylabsProxy;
import primemover.model.proxy.Utils;

/**
 * This class will decorate a Proxy-object with a specific session-id. Thus, any
 * request for the username will consider the following replacement
 * %%SESSIONID%% with the session-id
 * 
 * @author dburkolter
 *
 */
public class SessionDecorator implements IProxy {

	@Value("${geosurf.overwrite.user:}")
	private static String geosurfUserOverwrite;
	@Value("${geosurf.overwrite.password:}")
	private static String geosurfPasswordOverwrite;

	@Value("${oxylabs.overwrite.user:}")
	private static String oxylabsUserOverwrite;
	@Value("${oxylabs.overwrite.password:}")
	private static String oxylabsPasswordOverwrite;

	private final IProxy innerProxy;
	private final String sessionId;

	SessionDecorator(IProxy decorated, String prefix) {
		this.innerProxy = decorated;

		// sessionId = prefix + String.format("%06d", new Random().nextInt(1000000));
		sessionId = prefix + UUID.randomUUID().toString().replace("-", "");
	}

	SessionDecorator(IProxy decorated) {
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

	private String runUsernameReplacement(String input) {
		return input //
				.replace(Utils.SessionPlaceholder, this.getSessionId()) //
				.replace(Utils.PasswordPlaceholder, this.getPassword());
	}

	@Override
	public String getUsername() {

		String tmp = innerProxy.getUsername();

		if (!StringUtils.isNullOrEmpty(geosurfUserOverwrite) && innerProxy instanceof GeoSurfProxy) {
			tmp = geosurfUserOverwrite;
		}

		if (!StringUtils.isNullOrEmpty(oxylabsUserOverwrite) && innerProxy instanceof OxylabsProxy) {
			tmp = oxylabsUserOverwrite;
		}

		return runUsernameReplacement(tmp);
	}

	@Override
	public String getPassword() {
		if (!StringUtils.isNullOrEmpty(geosurfPasswordOverwrite) && innerProxy instanceof GeoSurfProxy) {
			return geosurfPasswordOverwrite;
		}

		if (!StringUtils.isNullOrEmpty(oxylabsPasswordOverwrite) && innerProxy instanceof OxylabsProxy) {
			return oxylabsPasswordOverwrite;
		}

		return innerProxy.getPassword();
	}

}
