package hsg.bot.runner;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.ContextConfiguration;

import primemover.RunnerApplication;
import primemover.model.Agent;
import primemover.model.Crawler;
import primemover.model.DecisionType;
import primemover.model.EnterTextField;
import primemover.model.Joblist;
import primemover.model.SelectorBaseJob;
import primemover.model.SelectorType;
import primemover.model.SingleSelectUrl;
import primemover.model.VisitJob;
import primemover.model.WaitJob;
import primemover.model.Agent.AgentType;
import primemover.service.ITaskService;
import primemover.service.TaskServiceFactory;

@SpringBootTest
class GoogleTestSuite {
	
	@Autowired
	private TaskServiceFactory serviceFactory;
	
	private ITaskService service;
	private Agent agent;
	Crawler crawler;
	
	
	@BeforeEach
	public void setup() {
		service = serviceFactory.getItem();
		agent = new Agent(1l, AgentType.CHROME, "Chrome", "Chrome");
		crawler = new Crawler();
		crawler.setAgent(agent);
		

		service.setup(crawler);
	}

	@Test
	void runGoogleSearchSelectFirst() {
		// Define jobs for google search
		Joblist jobs = new Joblist();
		
		// open page
		VisitJob visitJob = new VisitJob(1l, "visit google", "visit google", "https://www.google.ch");
		jobs.appendJob(visitJob);
		
		// enter search term
		SelectorBaseJob enterJob = new EnterTextField(1l, "enter value to text field", "enter value to text field", SelectorType.XPATH, "//input[@name='q']", "Klimaerwärmung");
		jobs.appendJob(enterJob);
		
		// quick wait for search to finish
		WaitJob waitJob = new WaitJob(1l, "wait", "wait", 10);
		jobs.appendJob(waitJob);
			
		// select url
		SingleSelectUrl singleSelectUrlJob = new SingleSelectUrl(1l, "select first entry", "select first entry", SelectorType.XPATH, ".//div[@id='rso']/div/div/div/a", DecisionType.FIRST);
		jobs.appendJob(singleSelectUrlJob);
		
		// wait for search result
		waitJob = new WaitJob(1l, "wait", "wait", 10);
		jobs.appendJob(waitJob);
		
		service.runJobList(jobs);
	}
	
	@Test
	void runGoogleSearchSelectLast() {
		// Define jobs for google search
		Joblist jobs = new Joblist();
		
		// open page
		VisitJob visitJob = new VisitJob(1l, "visit google", "visit google", "https://www.google.ch");
		jobs.appendJob(visitJob);
		
		// enter search term
		SelectorBaseJob enterJob = new EnterTextField(1l, "enter value to text field", "enter value to text field", SelectorType.XPATH, "//input[@name='q']", "Klimaerwärmung");
		jobs.appendJob(enterJob);
		
		// quick wait for search to finish
		WaitJob waitJob = new WaitJob(1l, "wait", "wait", 10);
		jobs.appendJob(waitJob);
			
		// select url
		SingleSelectUrl singleSelectUrlJob = new SingleSelectUrl(1l, "select first entry", "select first entry", SelectorType.XPATH, ".//div[@id='rso']/div/div/div/a", DecisionType.LAST);
		jobs.appendJob(singleSelectUrlJob);
		
		// wait for search result
		waitJob = new WaitJob(1l, "wait", "wait", 10);
		jobs.appendJob(waitJob);
		
		service.runJobList(jobs);
	}
	
	
	@AfterEach
    public void tearDown() {
        service.tearDown();
        service = null;
        agent = null;
        crawler = null;
    }

}
