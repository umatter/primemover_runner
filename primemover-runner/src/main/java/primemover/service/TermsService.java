package primemover.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Retryable;
import org.springframework.web.client.RestTemplate;

import primemover.api.converter.PrimemoverApiConverter;
import primemover.api.model.TermResult;
import primemover.model.Term;

public class TermsService {

	@Value("#{'${backend.endpoint.runner}' +'agents'}")
	private String endpointPath;

	@Qualifier("ServiceRestTemplate")
	@Autowired
	private RestTemplate restTemplate;

	private static Logger logger = LoggerFactory.getLogger(TermsService.class);

	public TermsService() {
	}

	@Retryable
	public List<Term> getTerms() {
		logger.debug("loading jobs from {}", endpointPath);
		TermResult apiRes = restTemplate.getForObject(endpointPath, TermResult.class);
		List<Term> res = PrimemoverApiConverter.convertFromApi(apiRes);

		return res;
	}
}
