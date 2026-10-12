package primemover.anticaptcha.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import primemover.model.proxy.HttpProxy;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Anti-Captcha takes a proxy only as a public IP address -- "no host names or IP
 * addresses from local networks" -- so the bot's upstream has to be resolved, and
 * an upstream its workers could never reach has to be refused up front rather
 * than paid for and failed.
 */
class TaskProxyTest {

	/** A resolver with no DNS: answers from a fixed table. */
	private static HostResolver table(String host, String... ips) {
		return name -> {
			if (!name.equals(host)) {
				throw new UnknownHostException(name);
			}
			InetAddress[] out = new InetAddress[ips.length];
			for (int i = 0; i < ips.length; i++) {
				out[i] = InetAddress.getByName(ips[i]); // literals: no lookup
			}
			return out;
		};
	}

	@Test
	@DisplayName("a gateway host name is resolved to its public IPv4 address")
	void resolvesGatewayHostName() {
		TaskProxy proxy = TaskProxy.resolve(new HttpProxy("gate.multilogin.com", 8080, "user-sess-1", "secret"),
				table("gate.multilogin.com", "2001:db8::1", "185.91.116.40"));

		assertThat(proxy.getType()).isEqualTo("http");
		assertThat(proxy.getAddress()).isEqualTo("185.91.116.40");
		assertThat(proxy.getPort()).isEqualTo(8080);
		assertThat(proxy.getLogin()).isEqualTo("user-sess-1");
		assertThat(proxy.getPassword()).isEqualTo("secret");
	}

	@Test
	@DisplayName("an IP address is used as it is")
	void ipLiteralIsKept() {
		TaskProxy proxy = TaskProxy.resolve(new HttpProxy("103.195.116.28", 5432, "u", "p"),
				table("103.195.116.28", "103.195.116.28"));

		assertThat(proxy.getAddress()).isEqualTo("103.195.116.28");
	}

	@Test
	@DisplayName("loopback, private, link-local and tailnet addresses are refused")
	void localAddressesAreRefused() {
		for (String ip : new String[] { "127.0.0.1", "10.0.0.5", "172.19.0.1", "192.168.1.2", "169.254.1.1",
				"100.66.121.42" }) {
			assertThatThrownBy(() -> TaskProxy.resolve(new HttpProxy("slot", 8888, "u", "p"), table("slot", ip)))
					.as(ip).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("public");
		}
	}

	@Test
	@DisplayName("no host, no port, or an unresolvable host is refused with the reason")
	void missingOrUnresolvableIsRefused() {
		assertThatThrownBy(() -> TaskProxy.resolve(new HttpProxy(null, 8080, "u", "p"), table("x", "1.1.1.1")))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no host");
		assertThatThrownBy(() -> TaskProxy.resolve(new HttpProxy("gate", null, "u", "p"), table("gate", "1.1.1.1")))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no port");
		assertThatThrownBy(() -> TaskProxy.resolve(new HttpProxy("nowhere", 8080, "u", "p"), table("x", "1.1.1.1")))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("nowhere");
	}

	@Test
	@DisplayName("the password never appears in toString, which is what gets logged")
	void toStringHidesThePassword() {
		TaskProxy proxy = TaskProxy.resolve(new HttpProxy("185.91.116.40", 8080, "u", "s3cret-pw"),
				table("185.91.116.40", "185.91.116.40"));

		assertThat(proxy.toString()).contains("185.91.116.40:8080").doesNotContain("s3cret-pw");
	}
}
