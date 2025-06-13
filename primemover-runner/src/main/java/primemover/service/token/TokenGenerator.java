package primemover.service.token;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.RestTemplate;
import primemover.api.model.TokenRequest;
import primemover.api.model.TokenResult;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

public class TokenGenerator {
	private static Logger logger = LoggerFactory.getLogger(TokenGenerator.class);

	@Value("${backend.requestTimeoutSeconds:60}")
	private Long requestTimeoutSeconds;

	@Value("${backend.connectionTimeoutSeconds:10}")
	private Long connectionTimeoutSeconds;

	@Value("#{'${backend.endpoint.api}' +'login'}")
	private String endpointPathReports;

	@Value("${backend.username}")
	private String username;
	@Value("${backend.password}")
	private String password;

	private RestTemplate restTemplate;

	private boolean isInit = false;

	private AtomicReference<String> token = new AtomicReference<>(); // thread save

	public TokenGenerator() {
	}

	private void init() {
		if (isInit) {
			return;
		}

		logger.debug("setting up TokenRestTemplate - Defaults");
		// custom template to allow request-interception with this generator!
		restTemplate = new RestTemplateBuilder()
				.connectTimeout(Duration.ofSeconds(connectionTimeoutSeconds))
				.readTimeout(Duration.ofSeconds(requestTimeoutSeconds)).build();

		isInit = true; // avoid recursion!

		// initial token
		refreshToken();
	}

	@Scheduled(fixedRate = 300000, initialDelay = 1000)
	private void refreshToken() {
		init();

		TokenRequest req = new TokenRequest(username, password);
		TokenResult res = restTemplate.postForObject(endpointPathReports, req, TokenResult.class);
		String strToken = res.getAccess_token();
		token.set(strToken);
		logger.trace("token refreshed to {}", strToken);
	}

	public String getToken() {
		init();
		return token.get();
	}
}
