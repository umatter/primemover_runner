package primemover.service;

import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.retry.annotation.Retryable;
import org.springframework.retry.support.RetrySynchronizationManager;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import primemover.api.converter.PrimemoverApiConverter;
import primemover.api.model.IpResult;
import primemover.capture.BasicAuthorization;
import primemover.model.LocationInfo;
import primemover.service.token.TokenRequestInterceptor;

/**
 * Service to grab the current IP via a proxy
 * 
 * @author dburkolter
 *
 */
public class IpService {

	private static Logger logger = LoggerFactory.getLogger(IpService.class);
	private RestTemplate restTemplate;
	private AtomicBoolean isInit = new AtomicBoolean(false);

	@Value("#{'${backend.endpoint.api}' +'ip'}")
	private String endpointPathReports;

	@Autowired
	TokenRequestInterceptor requestInterceptor;

	public IpService(Proxy proxy, Optional<BasicAuthorization> basicAuth) {
		setProxy(proxy, basicAuth);
	}

	public IpService() {
	}

	public void setProxy(Proxy proxy, Optional<BasicAuthorization> basicAuth) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setProxy(proxy);
		RestTemplateBuilder restTemplateBuilder = new RestTemplateBuilder().requestFactory(() -> requestFactory);

		if (basicAuth.isPresent()) {
			restTemplateBuilder.defaultHeader(HttpHeaders.PROXY_AUTHORIZATION,
					"Basic " + Base64.getEncoder()
							.encodeToString((basicAuth.get().getUsername() + ':' + basicAuth.get().getPassword())
									.getBytes(StandardCharsets.UTF_8)));
		}

		this.restTemplate = restTemplateBuilder.build();

		isInit.set(true);
	}

	@Retryable(noRetryFor = IllegalStateException.class)
	public LocationInfo getLocationInfo() {
		if (!isInit.get()) {
			throw new IllegalStateException("Init proxy before using");
		}
		String url = endpointPathReports;

		logger.debug("grabbing location info from {}. Retry {}", url,
				RetrySynchronizationManager.getContext().getRetryCount());
		try {
			IpResult apiRes = restTemplate.getForObject(url, IpResult.class);
			LocationInfo res = PrimemoverApiConverter.convertFromApi(apiRes);

			logger.debug("location info retrieved {}", res.toString());

			return res;
		} catch (RestClientException ex) {
			logger.warn("Error getting location info", ex);
		}

		return null;
	}

}
