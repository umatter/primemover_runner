package hsg.bot.runner;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import primemover.model.Agent;
import primemover.model.Crawler;
import primemover.model.DecisionType;
import primemover.model.EnterTextField;
import primemover.model.Joblist;
import primemover.model.SelectorBaseJob;
import primemover.model.SelectorType;
import primemover.model.SimpleProxy;
import primemover.model.SingleSelectUrl;
import primemover.model.VisitJob;
import primemover.model.WaitJob;
import primemover.model.Agent.AgentType;
import primemover.model.AssertProxyByGeosurf;
import primemover.service.ITaskService;
import primemover.service.TaskServiceFactory;

@SpringBootTest
class ProxyTestSuite {
	
	@Autowired
	private TaskServiceFactory serviceFactory;
	
	private ITaskService service;
	private Agent agent;
	private Crawler crawler;
	private SimpleProxy proxy;
	
	
	@BeforeEach
	public void setup() {
		// Defined proxy for US and city of New York for test!
		proxy = new SimpleProxy("us-30m.geosurf.io", 8000, "000000+US-NY-NEW_YORK+000000-296201", "CHANGEME-password");	
		service = serviceFactory.getItem();
		agent = new Agent(1l, AgentType.CHROME, "Chrome", "Chrome"); // Must run on Chrome as plain JSON is available. Firefox will display a table.
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
		//VisitJob visitJob = new VisitJob(1l, "visit geosurf", "visit geosurf", "https://geo.geosurf.io");
		//jobs.appendJob(visitJob);
		
		// quick wait for search to finish
		//WaitJob waitJob = new WaitJob(1l, "wait", "wait", 10);
		//jobs.appendJob(waitJob);
		
		// Verify city and country
		AssertProxyByGeosurf assertProxyJob = new AssertProxyByGeosurf(1l, "wait", "wait", null, "US", "New York");
		jobs.appendJob(assertProxyJob);
		
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
