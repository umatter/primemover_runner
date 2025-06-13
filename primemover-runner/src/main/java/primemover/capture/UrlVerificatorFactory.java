package primemover.capture;

import org.springframework.beans.factory.annotation.Value;

import java.net.Proxy;
import java.util.List;

/**
 * Helper factory to create UrlVerificators
 * 
 * @author dominik
 *
 */
public class UrlVerificatorFactory {

	@Value("${recorder.connectTimeoutSeconds:45}")
	private Integer connectTimeoutSeconds;

	@Value("#{'${proxy.connectivityVerificationUrls:https://primemover.example/,https://app.multiloginapp.com/whatismyip}'.split(',')}") // zero
	private List<String> connectivityVerificationUrls;

	UrlVerificator getInstance(Proxy proxy) {
		return new UrlVerificator(proxy, connectivityVerificationUrls, connectTimeoutSeconds * 1000);
	}
}
