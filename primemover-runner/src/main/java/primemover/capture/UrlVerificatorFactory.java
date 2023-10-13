package primemover.capture;

import java.net.Proxy;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;

/**
 * Helper factory to create UrlVerificators
 * 
 * @author dominik
 *
 */
public class UrlVerificatorFactory {

	@Value("${recorder.connectTimeoutSeconds:45}")
	private Integer connectTimeoutSeconds;

	@Value("#{'${recorder.connectivityVerificationUrls:https://primemover.example/,https://app.multiloginapp.com/whatismyip}'.split(',')}") // zero
	private List<String> connectivityVerificationUrls;

	UrlVerificator getInstance(Proxy proxy) {
		return new UrlVerificator(proxy, connectivityVerificationUrls, connectTimeoutSeconds * 1000);
	}
}
