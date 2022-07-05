package primemover.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestTemplate;

import primemover.api.model.OutletResult;
import primemover.converter.PrimemoverApiConverter;
import primemover.model.Outlet;

public class OutletService {
	
	@Value("#{'${backend.endpoint}' +'outlets'}")
	private String endpointPath;
	
	@Qualifier("ServiceRestTemplate")
	@Autowired
	private RestTemplate restTemplate;

	private static Logger logger = LoggerFactory.getLogger(OutletService.class);

	public OutletService() {
	}

	public List<Outlet> getOutlets() {
		logger.debug("loading outlets from {}", endpointPath);
		OutletResult apiRes = restTemplate.getForObject(endpointPath, OutletResult.class);		
		List<Outlet> res = PrimemoverApiConverter.convertFromApi(apiRes);
		
		return res;
	}
}
