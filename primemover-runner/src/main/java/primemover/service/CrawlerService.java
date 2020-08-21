package primemover.service;

import java.util.List;

import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import primemover.converter.PrimemoverApiConverter;
import primemover.model.Crawler;
import primemover.model.Joblist;
import primemover.model.api.CrawlerResult;

@Service
public class CrawlerService implements ICrawlerService {

	public CrawlerService() {
		// nothing to do
	}

	@Autowired
	private RestTemplate restTemplate;
	
	/* (non-Javadoc)
	 * @see primemover.service.ICrawlerService#getCurrentTasks()
	 */
	@Override
	public List<Pair<Crawler, Joblist>> getCurrentTasks() {
		CrawlerResult apiRes = restTemplate.getForObject("https://siaw.primemover.example/api/v1/crawlers", CrawlerResult.class);	
		return PrimemoverApiConverter.convertFromApi(apiRes);
	}
}
