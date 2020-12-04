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
import primemover.model.jobs.VisitJob;
import primemover.model.jobs.WaitJob;
import primemover.runner.ITaskOperator;
import primemover.runner.TaskOperatorFactory;

@SpringBootTest
class MulitLoginConnectivity {
	
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
	void visitMultiLoginApi() {
		// Define jobs for google search
		Joblist jobs = new Joblist();
		
		// open page
		VisitJob visitJob = new VisitJob(1l, "visit MulitLogin API", "visit MulitLogin API", "https://app.multiloginapp.com");
		jobs.appendJob(visitJob);
	
		// quick wait for search to finish
		WaitJob waitJob = new WaitJob(1l, "wait", "wait", 10);
		jobs.appendJob(waitJob);
		
		service.runJobList(jobs);
	}
	
	
	@AfterEach
    public void tearDown() {
        service.tearDown(true);
        service = null;
        agent = null;
        crawler = null;
    }

}
