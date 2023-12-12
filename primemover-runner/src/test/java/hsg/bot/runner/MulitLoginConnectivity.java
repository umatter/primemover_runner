package hsg.bot.runner;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import primemover.model.ChromeAgent;
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
		agent = new ChromeAgent(1l, "Chrome", "Chrome", "");
		crawler = new Crawler();
		crawler.setAgent(agent);
		service = serviceFactory.getInstance(crawler);

	}

	@Test
	void visitMultiLoginApi() {
		// Define jobs for google search
		Joblist jobs = new Joblist();

		// open page
		VisitJob visitJob = new VisitJob(1l, "visit MulitLogin API", "visit MulitLogin API", null,
				"https://app.multiloginapp.com");
		jobs.appendJob(visitJob);

		// quick wait for search to finish
		WaitJob waitJob = new WaitJob(1l, "wait", "wait", null, 10);
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
