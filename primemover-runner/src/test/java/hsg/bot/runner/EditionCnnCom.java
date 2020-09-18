package hsg.bot.runner;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import primemover.model.BasicAgent;
import primemover.model.AgentType;
import primemover.model.Crawler;
import primemover.model.GeoSurfProxy;
import primemover.model.IAgent;
import primemover.model.Joblist;
import primemover.model.jobs.VisitJob;
import primemover.model.jobs.WaitJob;
import primemover.runner.ITaskOperator;
import primemover.runner.TaskOperatorFactory;

@SpringBootTest
class EditionCnnCom {
	
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
	void openEditionCnnComAndWaitNoProxy() {
		// Define jobs for google search
		Joblist jobs = new Joblist();
		
		// open page
		VisitJob visitJob = new VisitJob(1l, "visit edition.cnn.com", "visit edition.cnn.com", "https://edition.cnn.com/");
		jobs.appendJob(visitJob);
	
		// wait for search result
		WaitJob waitJob = new WaitJob(1l, "wait", "wait", 10);
		jobs.appendJob(waitJob);
		
		operator.runJobList(jobs);
	}
	
	@Test
	void openEditionCnnComAndWaitWithProxy() {
		// Set Proxy
		GeoSurfProxy proxy = new GeoSurfProxy();
		proxy.setUsername("000000+US-CA-SAN_FRANCISCO+000000-683540");
		proxy.setPassword("CHANGEME-password");
		proxy.setUrl("us-30m.geosurf.io");
		proxy.setPort(8000);
		crawler.setProxy(proxy);
				
		// Define jobs for google search
		Joblist jobs = new Joblist();
		
		// open page
		VisitJob visitJob = new VisitJob(1l, "visit edition.cnn.com", "visit edition.cnn.com", "https://edition.cnn.com/");
		jobs.appendJob(visitJob);
	
		// wait for search result
		WaitJob waitJob = new WaitJob(1l, "wait", "wait", 10);
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
