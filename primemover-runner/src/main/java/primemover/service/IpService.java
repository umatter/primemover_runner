package primemover.service;

import java.net.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import primemover.api.model.IpResult;
import primemover.converter.PrimemoverApiConverter;
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
	
	@Value("#{'${backend.endpoint}' +'ip'}")
	private String endpointPathReports;
	
	@Autowired
	TokenRequestInterceptor requestInterceptor;
	
	public IpService(Proxy proxy) {
		setProxy(proxy);
	}
	
	public IpService() {
	}
	
	public void setProxy(Proxy proxy) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setProxy(proxy);
		restTemplate = new RestTemplate(requestFactory);
		isInit.set(true);
	}
	
	
	
	public LocationInfo getLocationInfo() {
		if (!isInit.get()) {
			throw new IllegalStateException("Init proxy before using");
		}
		String url = endpointPathReports;
		
		logger.debug("grabbing location info from {}", url);
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
