package primemover.anticaptcha.service;

import primemover.model.proxy.IProxy;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * The proxy an Anti-Captcha worker solves through, so that the token is minted
 * from the bot's own exit address rather than the worker's.
 *
 * <p>
 * Anti-Captcha takes the proxy only as an IP address -- "no host names or IP
 * addresses from local networks" -- so {@link #resolve} turns the session's
 * upstream (a gateway host name, usually) into its public address, preferring
 * IPv4, and refuses one the workers could never reach: loopback, private,
 * link-local, and the 100.64/10 range the tailnet lives in. A sticky session is
 * keyed by the username, not by which gateway address answers, so resolving the
 * gateway keeps the bot's exit. The password is never part of
 * {@link #toString()}.
 */
public final class TaskProxy {

	private final String address;
	private final int port;
	private final String login;
	private final String password;

	private TaskProxy(String address, int port, String login, String password) {
		this.address = address;
		this.port = port;
		this.login = login;
		this.password = password;
	}

	/**
	 * @throws IllegalArgumentException with the reason, when the proxy has no host
	 *                                  or port, cannot be resolved, or resolves to
	 *                                  no public address
	 */
	public static TaskProxy resolve(IProxy proxy, HostResolver resolver) {
		String host = proxy.getUrl();
		if (host == null || host.isBlank()) {
			throw new IllegalArgumentException("the bot's proxy has no host");
		}
		Integer port = proxy.getPort();
		if (port == null || port <= 0) {
			throw new IllegalArgumentException("the bot's proxy " + host + " has no port");
		}

		InetAddress[] addresses;
		try {
			addresses = resolver.resolve(host.strip());
		} catch (UnknownHostException e) {
			throw new IllegalArgumentException("the bot's proxy host " + host + " cannot be resolved");
		}

		InetAddress chosen = null;
		for (InetAddress candidate : addresses) {
			if (isPublic(candidate) && (chosen == null || (candidate instanceof Inet4Address
					&& !(chosen instanceof Inet4Address)))) {
				chosen = candidate;
			}
		}
		if (chosen == null) {
			throw new IllegalArgumentException(
					"the bot's proxy host " + host + " resolves to no public address Anti-Captcha could reach");
		}

		return new TaskProxy(chosen.getHostAddress(), port, proxy.getUsername(), proxy.getPassword());
	}

	private static boolean isPublic(InetAddress address) {
		if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()
				|| address.isAnyLocalAddress() || address.isMulticastAddress()) {
			return false;
		}
		byte[] bytes = address.getAddress();
		if (bytes.length == 4) {
			// 100.64.0.0/10: carrier-grade NAT, and the tailnet
			return !((bytes[0] & 0xFF) == 100 && (bytes[1] & 0xC0) == 64);
		}
		// fc00::/7: IPv6 unique local
		return (bytes[0] & 0xFE) != 0xFC;
	}

	/** Anti-Captcha's {@code proxyType}; every upstream we use speaks plain HTTP CONNECT. */
	public String getType() {
		return "http";
	}

	public String getAddress() {
		return address;
	}

	public int getPort() {
		return port;
	}

	public String getLogin() {
		return login;
	}

	public String getPassword() {
		return password;
	}

	@Override
	public String toString() {
		return "TaskProxy[" + address + ":" + port + "]";
	}
}
