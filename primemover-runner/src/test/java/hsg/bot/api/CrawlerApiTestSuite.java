package hsg.bot.api;

import java.util.List;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import primemover.model.BasicAgent;
import primemover.model.AgentType;
import primemover.model.Crawler;
import primemover.model.DecisionType;
import primemover.model.EnterTextFieldJob;
import primemover.model.IAgent;
import primemover.model.Joblist;
import primemover.model.SelectorBaseJob;
import primemover.model.SelectorType;
import primemover.model.SingleSelectUrlJob;
import primemover.model.VisitJob;
import primemover.model.WaitJob;
import primemover.runner.ITaskOperator;
import primemover.runner.TaskOperatorFactory;
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
		// get the current Crawler
		List<Pair<Crawler, Joblist>> crawlers = service.getCurrentTasks();
	}
	
	@AfterEach
    public void tearDown() {
        
    }

}
