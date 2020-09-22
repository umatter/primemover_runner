package hsg.bot.runner;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import primemover.model.BasicAgent;
import primemover.model.AgentType;
import primemover.model.Crawler;
import primemover.model.DecisionType;
import primemover.model.IAgent;
import primemover.model.Joblist;
import primemover.model.SelectorType;
import primemover.model.TypingModeType;
import primemover.model.jobs.EnterTextFieldJob;
import primemover.model.jobs.SelectorBaseJob;
import primemover.model.jobs.SingleSelectUrlJob;
import primemover.model.jobs.VisitJob;
import primemover.model.jobs.WaitJob;
import primemover.runner.ITaskOperator;
import primemover.runner.TaskOperatorFactory;

@SpringBootTest
class GoogleTestSuite {
	
	@Autowired
	private TaskOperatorFactory operatorFactory;
	
	private ITaskOperator operator;
	private IAgent agent;
	Crawler crawler;
	
	
	@BeforeEach
	public void setup() {
		operator = operatorFactory.getItem();
		agent = new BasicAgent(1l, AgentType.CHROME, "Chrome", "Chrome", "");
		crawler = new Crawler();
		crawler.setAgent(agent);
		

		operator.setup(crawler);
	}

	@Test
	void runGoogleSearchSelectFirst() {
		// Define jobs for google search
		Joblist jobs = new Joblist();
		
		// open page
		VisitJob visitJob = new VisitJob(1l, "visit google", "visit google", "https://www.google.com");
		jobs.appendJob(visitJob);
		
		// enter search term
		SelectorBaseJob enterJob = new EnterTextFieldJob(1l, "enter value to text field", "enter value to text field", SelectorType.XPATH, "//input[@name='q']", "Klimaerwärmung", TypingModeType.DIRECT, true);
		jobs.appendJob(enterJob);
		
		// quick wait for search to finish
		WaitJob waitJob = new WaitJob(1l, "wait", "wait", 10);
		jobs.appendJob(waitJob);
			
		// select url
		SingleSelectUrlJob singleSelectUrlJob = new SingleSelectUrlJob(1l, "select first entry", "select first entry", SelectorType.XPATH, ".//div[@id='rso']/div/div/div/a", DecisionType.FIRST);
		jobs.appendJob(singleSelectUrlJob);
		
		// wait for search result
		waitJob = new WaitJob(1l, "wait", "wait", 10);
		jobs.appendJob(waitJob);
		
		operator.runJobList(jobs);
	}
	
	@Test
	void runGoogleSearchSelectLast() {
		// Define jobs for google search
		Joblist jobs = new Joblist();
		
		// open page
		VisitJob visitJob = new VisitJob(1l, "visit google", "visit google", "https://www.google.ch");
		jobs.appendJob(visitJob);
		
		// enter search term
		SelectorBaseJob enterJob = new EnterTextFieldJob(1l, "enter value to text field", "enter value to text field", SelectorType.XPATH, "//input[@name='q']", "Klimaerwärmung", TypingModeType.DIRECT, true);
		jobs.appendJob(enterJob);
		
		// quick wait for search to finish
		WaitJob waitJob = new WaitJob(1l, "wait", "wait", 10);
		jobs.appendJob(waitJob);
			
		// select url
		SingleSelectUrlJob singleSelectUrlJob = new SingleSelectUrlJob(1l, "select first entry", "select first entry", SelectorType.XPATH, ".//div[@id='rso']/div/div/div/a", DecisionType.LAST);
		jobs.appendJob(singleSelectUrlJob);
		
		// wait for search result
		waitJob = new WaitJob(1l, "wait", "wait", 10);
		jobs.appendJob(waitJob);
		
		operator.runJobList(jobs);
	}
	
	
	@AfterEach
    public void tearDown() {
        operator.tearDown();
        operator = null;
        agent = null;
        crawler = null;
    }

}
