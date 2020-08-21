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
import primemover.model.SimpleProxy;
import primemover.model.SingleSelectUrlJob;
import primemover.model.VisitJob;
import primemover.model.WaitJob;
import primemover.runner.ITaskOperator;
import primemover.runner.TaskOperatorFactory;
import primemover.service.ICrawlerService;
import primemover.service.MockCrawlerService;

@SpringBootTest
class ConversionApiTestSuite {
	@Autowired
	private TaskOperatorFactory serviceFactory;
	
	private MockCrawlerService crawlerService;
		
	private ITaskOperator taskService;
	
	@BeforeEach
	public void setup() {
		crawlerService = new MockCrawlerService();
	}

	@Test
	void runEnd2EndFromJsonFile() {
		// set input file
		String filePath = "C:/Users/dburkolter/git/primemover_runner/primemover-runner/src/test/resources/api-json/MockEnd2End01.json";
		crawlerService.setMockFile(filePath);
				
		// get the jobList for each Crawler
		List<Pair<Crawler, Joblist>> taskList = crawlerService.getCurrentTasks();
		
		for (Pair<Crawler, Joblist> task : taskList) {
			ITaskOperator service = serviceFactory.getItem();
			service.setup(task.getLeft());
			service.runJobList(task.getValue());
			service.tearDown();
		}
	}
	
	@AfterEach
    public void tearDown() {
        
    }

}
