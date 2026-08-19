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

	/**
	 * Host-level Oxylabs account, used whenever a proxy record leaves the
	 * credentials blank. Oxylabs geo-targeting comes from the crawler's agent, not
	 * from the proxy record, so every crawler in an experiment would otherwise
	 * carry a byte-identical copy of the same account -- in the database and in the
	 * experiment spec. Keeping it here means one place to set it and one place to
	 * rotate it, and no credential in git.
	 */
	@Value("${oxylabs.customer:}")
	private String oxylabsCustomer;
	@Value("${oxylabs.password:}")
	private String oxylabsPassword;
	@Value("${oxylabs.hostname:pr.oxylabs.io}")
	private String oxylabsHostname;
	@Value("${oxylabs.port:7777}")
	private Integer oxylabsPort;

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
		String url = innerProxy.getUrl();
		if (isOxylabs() && StringUtils.isNullOrEmpty(url)) {
			return oxylabsHostname;
		}
		return url;
	}

	@Override
	public Integer getPort() {
		Integer port = innerProxy.getPort();
		if (isOxylabs() && (port == null || port <= 0)) {
			return oxylabsPort;
		}
		return port;
	}

	private boolean isOxylabs() {
		return innerProxy instanceof OxylabsProxy;
	}

	private String runUsernameReplacement(String input) {
		if (input != null && input.contains(Utils.CustomerPlaceholder)) {
			if (StringUtils.isNullOrEmpty(oxylabsCustomer)) {
				throw new IllegalStateException(
						"This proxy has no username and no host-level Oxylabs account is configured."
								+ " Set oxylabs.customer (and oxylabs.password) in the runner configuration,"
								+ " or put the credentials on the proxy record.");
			}
			input = input.replace(Utils.CustomerPlaceholder, oxylabsCustomer);
		}

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

		if (!StringUtils.isNullOrEmpty(oxylabsUserOverwrite) && isOxylabs()) {
			tmp = oxylabsUserOverwrite;
		}

		return runUsernameReplacement(tmp);
	}

	@Override
	public String getPassword() {
		if (!StringUtils.isNullOrEmpty(oxylabsPasswordOverwrite) && isOxylabs()) {
			return oxylabsPasswordOverwrite;
		}

		String password = innerProxy.getPassword();
		if (isOxylabs() && StringUtils.isNullOrEmpty(password)) {
			if (StringUtils.isNullOrEmpty(oxylabsPassword)) {
				throw new IllegalStateException(
						"This proxy has no password and no host-level Oxylabs password is configured."
								+ " Set oxylabs.password in the runner configuration, or put the credentials"
								+ " on the proxy record.");
			}
			return oxylabsPassword;
		}
		return password;
	}

}
