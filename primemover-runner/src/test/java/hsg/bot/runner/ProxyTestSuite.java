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
import primemover.model.jobs.AssertProxyByGeosurfJob;
import primemover.model.jobs.WaitJob;
import primemover.model.proxy.GeoSurfProxy;
import primemover.model.proxy.HttpProxy;
import primemover.runner.ITaskOperator;
import primemover.runner.TaskOperatorFactory;

@SpringBootTest
class ProxyTestSuite {
	
	@Autowired
	private TaskOperatorFactory serviceFactory;
	
	private ITaskOperator service;
	private IAgent agent;
	private Crawler crawler;
	private HttpProxy proxy;
	
	
	@BeforeEach
	public void setupNewYork() {
		
		service = serviceFactory.getItem();
		agent = new BasicAgent(1l, AgentType.CHROME, "Chrome", "Chrome", "US-NY-NEW_YORK"); // Must run on Chrome as plain JSON is available. Firefox will display a table.
		crawler = new Crawler();
		crawler.setAgent(agent);
	}

	
	@Test
	void checkStaticNewYorkProxy() {
		// Defined proxy for US and city of New York for test!
		proxy = new HttpProxy("us-30m.geosurf.io", 8000, "000000+US-NY-NEW_YORK+000000-296201", "CHANGEME-password");
		crawler.setProxy(proxy);
				
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
		AssertProxyByGeosurfJob assertProxyJob = new AssertProxyByGeosurfJob(1l, "Assert New York", "Assert New York", null, "US", "New York");
		jobs.appendJob(assertProxyJob);		
		jobs.appendJob(new WaitJob(2l, "Wait", "", null, 10));
		
		service.setup(crawler);
		service.runJobList(jobs);
	}
	
	@Test
	void checkDynamicNewYorkProxy() {
		// Defined proxy for US and city of New York for test!
		proxy = new GeoSurfProxy("state.geosurf.io", 8000, "000000", "CHANGEME-password", "US-NY-NEW_YORK");
		crawler.setProxy(proxy);
				
		// Define jobs for google search
		Joblist jobs = new Joblist();
		
		// Verify city and country
		AssertProxyByGeosurfJob assertProxyJob = new AssertProxyByGeosurfJob(1l, "Assert New York", "Assert New York", null, "US", "New York");
		jobs.appendJob(assertProxyJob);
		jobs.appendJob(new WaitJob(2l, "Wait", "", null, 10));
		
		service.setup(crawler);
		service.runJobList(jobs);
	}
	
	@Test
	void checkDynamicFremontProxy() {
		// Defined proxy for US and city of New York for test!
		proxy = new GeoSurfProxy("state.geosurf.io", 8000, "000000", "CHANGEME-password", "US-CA-FREMONT");
		crawler.setProxy(proxy);
				
		// Define jobs for google search
		Joblist jobs = new Joblist();
		
		// Verify city and country
		AssertProxyByGeosurfJob assertProxyJob = new AssertProxyByGeosurfJob(1l, "Assert New York", "Assert New York", null, "US", "Fremont");
		jobs.appendJob(assertProxyJob);
		jobs.appendJob(new WaitJob(2l, "Wait", "", null, 10));
		
		service.setup(crawler);
		service.runJobList(jobs);
	}
	
	
	@AfterEach
    public void tearDown() {
        service.tearDown(true);
        service = null;
        agent = null;
        crawler = null;
        proxy = null;
    }

}
