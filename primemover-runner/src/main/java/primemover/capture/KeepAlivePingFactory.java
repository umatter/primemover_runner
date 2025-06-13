package primemover.capture;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.net.Proxy;

public class KeepAlivePingFactory {

	@Autowired
	private UrlVerificatorFactory urlVerificatorFac;

	@Value("${proxy.pingIntervalSeconds:45}")
	private int intervalSeconds;

	KeepAlivePing getInstance(Proxy proxy) {
		return new KeepAlivePing(urlVerificatorFac.getInstance(proxy), intervalSeconds);
	}
}
