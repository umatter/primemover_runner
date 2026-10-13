package primemover.anticaptcha.service;

import java.net.InetAddress;
import java.net.UnknownHostException;

/** Host name to addresses; {@link InetAddress#getAllByName} in production, a table in tests. */
@FunctionalInterface
public interface HostResolver {

	HostResolver SYSTEM = InetAddress::getAllByName;

	InetAddress[] resolve(String host) throws UnknownHostException;
}
