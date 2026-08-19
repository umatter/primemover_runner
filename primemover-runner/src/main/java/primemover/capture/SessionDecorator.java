package primemover.capture;

import io.opentelemetry.api.internal.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import primemover.model.proxy.IProxy;
import primemover.model.proxy.OxylabsProxy;
import primemover.model.proxy.Utils;

import java.util.Locale;
import java.util.UUID;

/**
 * This class will decorate a Proxy-object with a specific session-id. Thus, any
 * request for the username will consider the following replacement
 * %%SESSIONID%% with the session-id
 * 
 * @author dburkolter
 *
 */
public class SessionDecorator implements IProxy {

	@Value("${oxylabs.overwrite.user:}")
	private String oxylabsUserOverwrite;
	@Value("${oxylabs.overwrite.password:}")
	private String oxylabsPasswordOverwrite;

	/**
	 * Oxylabs sticky sessions come in two flavours. A plain {@code sessid} session
	 * silently rebinds to a DIFFERENT exit IP once the assigned peer drops or after
	 * 60 seconds without a request -- and browser think-time routinely exceeds 60
	 * seconds, so a job can change geography mid-run with no error anywhere.
	 * {@code sessid_oneip} instead pins the session to one exit node and fails the
	 * request with HTTP 502 when that node goes away.
	 *
	 * For experiment validity a detectable 502 is strictly better than an
	 * undetected IP swap, so this defaults to true.
	 */
	@Value("${oxylabs.session.oneip:true}")
	private boolean oxylabsSessionOneIp = true;

	private final IProxy innerProxy;
	private final String sessionId;

	SessionDecorator(IProxy decorated, String prefix) {
		this.innerProxy = decorated;

		// Oxylabs documents the sessid charset as "0-9, and A-Z", so the hex is
		// upper-cased. The hyphens must go either way: the whole username is a
		// hyphen-delimited token stream and a hyphen inside the value would corrupt it.
		sessionId = prefix + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
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
				.replace(Utils.SessionTokenPlaceholder, this.getSessionTokenName()) //
				.replace(Utils.SessionPlaceholder, this.getSessionId()) //
				.replace(Utils.PasswordPlaceholder, this.getPassword());
	}

	/**
	 * @return the Oxylabs session token name to use. Only Oxylabs usernames carry
	 *         the placeholder, so this is a no-op for every other provider.
	 */
	String getSessionTokenName() {
		return oxylabsSessionOneIp ? "sessid_oneip" : "sessid";
	}

	@Override
	public String getUsername() {

		String tmp = innerProxy.getUsername();

		if (!StringUtils.isNullOrEmpty(oxylabsUserOverwrite) && innerProxy instanceof OxylabsProxy) {
			tmp = oxylabsUserOverwrite;
		}

		return runUsernameReplacement(tmp);
	}

	@Override
	public String getPassword() {
		if (!StringUtils.isNullOrEmpty(oxylabsPasswordOverwrite) && innerProxy instanceof OxylabsProxy) {
			return oxylabsPasswordOverwrite;
		}

		return innerProxy.getPassword();
	}

}
