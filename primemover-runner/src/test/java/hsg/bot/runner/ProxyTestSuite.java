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
import primemover.model.SimpleProxy;
import primemover.runner.ITaskOperator;
import primemover.runner.TaskOperatorFactory;
import primemover.model.AssertProxyByGeosurf;

@SpringBootTest
class ProxyTestSuite {
	
	@Autowired
	private TaskOperatorFactory serviceFactory;
	
	private ITaskOperator service;
	private IAgent agent;
	private Crawler crawler;
	private SimpleProxy proxy;
	
	
	@BeforeEach
	public void setupNewYork() {
	
		// Defined proxy for US and city of New York for test!
		proxy = new SimpleProxy("us-30m.geosurf.io", 8000, "000000+US-NY-NEW_YORK+000000-296201", "CHANGEME-password");	
		service = serviceFactory.getItem();
		agent = new BasicAgent(1l, AgentType.CHROME, "Chrome", "Chrome"); // Must run on Chrome as plain JSON is available. Firefox will display a table.
		crawler = new Crawler();
		crawler.setAgent(agent);
		crawler.setProxy(proxy);
		
		service.setup(crawler);
	}

	
	@Test
	void checkNewYorkProxy() {
		// Define jobs for google search
		Joblist jobs = new Joblist();
		
		/* kept as template on how to grab correct verification values from the page */
		// open page
		/*VisitJob visitJob = new VisitJob(1l, "visit geosurf", "visit geosurf", "https://geo.geosurf.io");
		jobs.appendJob(visitJob);
		
		// quick wait for search to finish
		WaitJob waitJob = new WaitJob(1l, "wait", "wait", 45);
		jobs.appendJob(waitJob);
		*/
		
		// Verify city and country
		AssertProxyByGeosurf assertProxyJob = new AssertProxyByGeosurf(1l, "Assert New York", "Assert New York", null, "US", "New York");
		jobs.appendJob(assertProxyJob);
		
		service.runJobList(jobs);
	}
	
	
	@AfterEach
    public void tearDown() {
        service.tearDown();
        service = null;
        agent = null;
        crawler = null;
        proxy = null;
    }

}
