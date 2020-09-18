package hsg.bot.runner;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import primemover.model.BasicAgent;
import primemover.model.AgentType;
import primemover.model.Crawler;
import primemover.model.IAgent;
import primemover.model.Joblist;
import primemover.model.ScrollDirection;
import primemover.model.jobs.AssertPageLoadedJob;
import primemover.model.jobs.AssertScrolledMaxJob;
import primemover.model.jobs.ScrollByLengthJob;
import primemover.model.jobs.VisitJob;
import primemover.model.jobs.WaitJob;
import primemover.runner.ITaskOperator;
import primemover.runner.TaskOperatorFactory;

@SpringBootTest
class ScrollingTestSuite {
	
	@Autowired
	private TaskOperatorFactory serviceFactory;
	
	private ITaskOperator service;
	private IAgent agent;
	Crawler crawler;
	
	
	@BeforeEach
	public void setup() {
		service = serviceFactory.getItem();
		agent = new BasicAgent(1l, AgentType.CHROME, "Chrome", "Chrome", "");
		crawler = new Crawler();
		crawler.setAgent(agent);	

		service.setup(crawler);
	}

	@Test
	void openPageAndScrollDown() {
		// Define jobs for google search
		Joblist jobs = new Joblist();
		
		// open page
		VisitJob visitJob = new VisitJob(1l, "visit google", "visit google", "https://www.nzz.ch");
		jobs.appendJob(visitJob);
		
		// quick wait for search to finish
		WaitJob waitJob = new WaitJob(1l, "wait", "wait", 10);
		jobs.appendJob(waitJob);
		
		// Assert page loaded
		AssertPageLoadedJob pageLoadedJob = new AssertPageLoadedJob(1l, "assert page loaded", "assert page loaded");
		jobs.appendJob(pageLoadedJob);
		
		// scroll down
		ScrollByLengthJob scrollJob = new ScrollByLengthJob(1l, "scroll", "scroll 100% down", ScrollDirection.DOWN, 100);
		jobs.appendJob(scrollJob);
		
		// Assert page loaded
		AssertScrolledMaxJob assertScrolledDown = new AssertScrolledMaxJob(1l, "assert scrolled max down", "assert scrolled max down", ScrollDirection.DOWN);
		jobs.appendJob(assertScrolledDown);
				
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
