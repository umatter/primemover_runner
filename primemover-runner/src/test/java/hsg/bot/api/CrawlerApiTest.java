package hsg.bot.api;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import org.apache.commons.lang3.tuple.Pair;
import primemover.model.Crawler;
import primemover.model.Joblist;
import primemover.service.CrawlerService;

@SpringBootTest
class CrawlerApiTest {
	
	@Autowired
	private CrawlerService service;

	@Test
	void receiveAllCrawlersFromApi() {
		List<Pair<Crawler, Joblist>> crawlers = service.getCurrentTasks();
		
		assertNotNull("result is null", crawlers);
		assertTrue("Empty result received", crawlers.size() > 0);
	}

}
