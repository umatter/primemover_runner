package hsg.bot.runner;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import primemover.model.BasicAgent;
import primemover.model.Configuration;
import primemover.model.AgentType;
import primemover.model.Crawler;
import primemover.model.DecisionType;
import primemover.model.IAgent;
import primemover.model.Joblist;
import primemover.model.SelectionConfiguration;
import primemover.model.SelectorType;
import primemover.model.TypingModeType;
import primemover.model.jobs.EnterTextFieldJob;
import primemover.model.jobs.SelectorBaseJob;
import primemover.model.jobs.SingleSelectUrlJob;
import primemover.model.jobs.VisitJob;
import primemover.model.jobs.WaitJob;
import primemover.runner.ITaskOperator;
import primemover.runner.TaskOperator;
import primemover.runner.TaskOperatorFactory;

@SpringBootTest
class RunGoogleSearchTest {
	
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
		
		Configuration config = new Configuration();
		config.setId(1);
		config.setDescription("Test Configuration for climate change");
		config.setName("Test Config");
		
		SelectionConfiguration selConfig = new SelectionConfiguration();
		selConfig.setId(1);
		selConfig.setPi(-1);
		selConfig.setPsi(0);
		selConfig.setKappa(1);
		selConfig.setTau(1);
		selConfig.setBeta(0.62368980888047);
		selConfig.setAlpha(0);
		Map<String, String> outlets = new HashMap<>();
		outlets.put("hillreporter.com", "https://hillreporter.com");
		outlets.put("nbcnews.com", "https://www.nbcnews.com");
		outlets.put("pbs.org", "https://pbs.org");
		outlets.put("opednews.com", "https://www.opednews.com");
		outlets.put("doinmytoons.blogspot.com", "https://doinmytoons.blogspot.com");
		outlets.put("news.yahoo.com", "https://news.yahoo.com");
		outlets.put("cbsnews.com", "https://www.cbsnews.com");
		outlets.put("esquire.com", "https://www.esquire.com");
		outlets.put("amp.gs", "https://amplifr.com");
		outlets.put("theroot.com", "https://www.theroot.com");
		selConfig.setOutlets(outlets);
		
		config.setSelectionConfiguration(selConfig);
		crawler.setConfig(config);
		
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
	
	@Test
	void runGoogleSearchCalculated() {
		// Define jobs for google search
		Joblist jobs = new Joblist();
		
		// open page
		VisitJob visitJob = new VisitJob(1l, "visit google", "visit google", "https://www.google.com");
		jobs.appendJob(visitJob);
		
		// enter search term
		SelectorBaseJob enterJob = new EnterTextFieldJob(1l, "enter value to text field", "enter value to text field", SelectorType.XPATH, "//input[@name='q']", "news.yahoo.com", TypingModeType.DIRECT, true);
		jobs.appendJob(enterJob);
		
		// quick wait for search to finish
		WaitJob waitJob = new WaitJob(1l, "wait", "wait", 10);
		jobs.appendJob(waitJob);
			
		// select url
		//SingleSelectUrlJob singleSelectUrlJob = new SingleSelectUrlJob(1l, "select first entry", "select first entry", SelectorType.CSS, "#rso > div > div > div.yuRUbf > a", DecisionType.CALCULATED);
		//SingleSelectUrlJob singleSelectUrlJob = new SingleSelectUrlJob(1l, "select first entry", "select first entry", SelectorType.XPATH, ".//div[@id='rso']/div/div/div/a | .//div[@id='rso']/div/div/div/div/a", DecisionType.CALCULATED);
		SingleSelectUrlJob singleSelectUrlJob = new SingleSelectUrlJob(1l, "select first entry", "select first entry", SelectorType.XPATH, ".//*[@class=\"yuRUbf\"]/a", DecisionType.CALCULATED);
		jobs.appendJob(singleSelectUrlJob);
		
		// wait for search result
		waitJob = new WaitJob(1l, "wait", "wait", 10);
		jobs.appendJob(waitJob);
		
		operator.runJobList(jobs);
	}
	
	@Test
	void runGoogleSearchLongSearchTerm() {
		// Define jobs for google search
		Joblist jobs = new Joblist();
		
		// open page
		VisitJob visitJob = new VisitJob(1l, "visit google", "visit google", "https://www.google.ch");
		jobs.appendJob(visitJob);
		
		// enter search term
		SelectorBaseJob enterJob = new EnterTextFieldJob(1l, "enter value to text field", "enter value to text field", SelectorType.XPATH, "//input[@name='q']", "Aufmerksamkeitsdefizit-Hyperaktivitätsstörung", TypingModeType.SIMULATED_NOTYPOS, true);
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
	
	@Test
	void runGoogleSearchPhrase() {
		// Define jobs for google search
		Joblist jobs = new Joblist();
		
		// open page
		VisitJob visitJob = new VisitJob(1l, "visit google", "visit google", "https://www.google.ch");
		jobs.appendJob(visitJob);
		
		// enter search term
		SelectorBaseJob enterJob = new EnterTextFieldJob(1l, "enter value to text field", "enter value to text field", SelectorType.XPATH, "//input[@name='q']", "Klimaerwärmung ist real", TypingModeType.SIMULATED_NOTYPOS, true);
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
        operator.tearDown(true);
        operator = null;
        agent = null;
        crawler = null;
    }

}
