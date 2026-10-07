package primemover.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.retry.RetryContext;
import org.springframework.retry.annotation.Retryable;
import org.springframework.retry.support.RetrySynchronizationManager;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import primemover.api.converter.PrimemoverApiConverter;
import primemover.api.model.IpResult;
import primemover.capture.BasicAuthorization;
import primemover.model.LocationInfo;
import primemover.model.ipcheck.OxylabsIpCheck;
import primemover.service.token.TokenRequestInterceptor;

import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service to grab the current IP via a proxy.
 *
 * <p>
 * The request is made <em>through the crawler's proxy</em>, so the responding
 * service observes the exit address rather than the runner's own. That only works
 * against a host the exit node can actually reach. The original endpoint is the
 * backend's own {@code /ip}, which is reachable from the runner but not from a
 * residential exit when the backend sits on a private network -- in that setup the
 * recording fails on every session. {@code ipcheck.source=oxylabs} (the default)
 * therefore uses {@code ip.oxylabs.io/location}, which is public, is the endpoint
 * Oxylabs mandates for exit checks, and reports four geolocation databases instead
 * of one. Set {@code ipcheck.source=backend} to restore the original behaviour.
 *
 * @author dburkolter
 */
public class IpService {

	private static Logger logger = LoggerFactory.getLogger(IpService.class);

	private static final String OXYLABS_SOURCE = "oxylabs";
	private RestTemplate restTemplate;
	private AtomicBoolean isInit = new AtomicBoolean(false);

	@Value("#{'${backend.endpoint.api}' +'ip'}")
	private String endpointPathReports;

	/** {@code oxylabs} (default) or {@code backend}; see the class javadoc. */
	@Value("${ipcheck.source:oxylabs}")
	private String ipCheckSource;

	@Value("${ipcheck.oxylabs.url:https://ip.oxylabs.io/location}")
	private String oxylabsCheckUrl;

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
		boolean viaOxylabs = OXYLABS_SOURCE.equalsIgnoreCase(ipCheckSource);
		String url = viaOxylabs ? oxylabsCheckUrl : endpointPathReports;

		logger.debug("grabbing location info from {}. Retry {}", url, retryCount());
		try {
			LocationInfo res = viaOxylabs
					? toLocationInfo(restTemplate.getForObject(url, OxylabsIpCheck.class))
					: PrimemoverApiConverter.convertFromApi(restTemplate.getForObject(url, IpResult.class));

			logger.debug("location info retrieved {}", res);

			return res;
		} catch (RestClientException ex) {
			logger.warn("Error getting location info from {}", url, ex);
		}

		return null;
	}

	/**
	 * The current retry attempt, or -1 when there is none.
	 *
	 * <p>
	 * {@link RetrySynchronizationManager#getContext()} returns null whenever this
	 * method is not running inside a retry, which is <em>always</em> here:
	 * {@code IpServiceFactory} builds the service with {@code new IpService()} and
	 * {@code autowireBean}, and {@code autowireBean} injects dependencies without
	 * creating the AOP proxy that {@link Retryable} needs, so the annotation above is
	 * inert. Dereferencing that null threw a NullPointerException out of this method
	 * -- past the catch below, which only covers {@link RestClientException} -- and
	 * {@code TaskOperator} swallowed it. The effect was that no session, on any
	 * crawler, ever recorded an exit address, while every job still reported success.
	 */
	private static int retryCount() {
		RetryContext context = RetrySynchronizationManager.getContext();
		return context == null ? -1 : context.getRetryCount();
	}

	/**
	 * Map an {@code ip.oxylabs.io/location} response onto {@link LocationInfo}.
	 *
	 * <p>
	 * The endpoint reports one block per geolocation database and they routinely
	 * disagree, while {@link LocationInfo} holds a single city. The first non-blank
	 * value wins and a disagreement is logged, which matches how
	 * {@code AssertProxyByOxylabsJobHandler} already treats this payload: a city any
	 * provider reports is good enough, because Oxylabs targets from MaxMind and
	 * MaxMind often returns no city at all. Fields the endpoint does not carry
	 * (region, zip, coordinates) are left null rather than guessed at.
	 */
	static LocationInfo toLocationInfo(OxylabsIpCheck check) {
		LocationInfo res = new LocationInfo();
		if (check == null) {
			return res;
		}

		res.setIp(check.getIp());

		List<String> countries = check.reportedCountries();
		List<String> cities = check.reportedCities();
		if (!countries.isEmpty()) {
			res.setCountryCode(countries.get(0));
		}
		if (!cities.isEmpty()) {
			res.setCity(cities.get(0));
		}
		if (countries.size() > 1 || cities.size() > 1) {
			logger.info("geolocation providers disagree on exit IP {}: countries {}, cities {}", check.getIp(),
					countries, cities);
		}

		return res;
	}

}
