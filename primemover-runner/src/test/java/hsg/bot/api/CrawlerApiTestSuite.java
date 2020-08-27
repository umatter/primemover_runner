package hsg.bot.api;

import java.util.List;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import primemover.model.Crawler;
import primemover.model.Joblist;
import primemover.service.ICrawlerService;

@SpringBootTest
class CrawlerApiTestSuite {
	
	@Autowired
	private ICrawlerService service;
	
	@BeforeEach
	public void setup() {
		
	}

	@Test
	void runGoogleSearchSelectFirst() {
		// get the current Crawlers
		List<Pair<Crawler, Joblist>> crawlers = service.getCurrentTasks();
		
	}
	
	@AfterEach
    public void tearDown() {
        
    }

}
