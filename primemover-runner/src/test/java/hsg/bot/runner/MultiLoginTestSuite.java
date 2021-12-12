package hsg.bot.runner;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import primemover.model.Crawler;
import primemover.model.IAgent;
import primemover.model.Joblist;
import primemover.model.MultiLoginAgent;
import primemover.model.jobs.AssertProxyByGeosurfJob;
import primemover.model.jobs.VisitJob;
import primemover.model.jobs.WaitJob;
import primemover.model.HttpProxy;
import primemover.runner.ITaskOperator;
import primemover.runner.TaskOperatorFactory;

@SpringBootTest
class MultiLoginTestSuite {
	
	@Autowired
	private TaskOperatorFactory serviceFactory;
	
	private ITaskOperator service;
	private IAgent agent;
	private Crawler crawler;
	private HttpProxy proxy;
	
	
	@BeforeEach
	public void setupNewYork() {
		//System.setProperty("https.protocols", "SSLv3,TLSv1,TLSv1.1,TLSv1.2,TLSv1.3");
		//String prot = System.getProperty("https.protocols");
		
		// Defined proxy for US and city of New York for test!
		proxy = new HttpProxy("us-30m.geosurf.io", 8000, "b8a2d8a4-ea69-4d0d-923e-2a83421cd1e5", "CHANGEME-password");	
		service = serviceFactory.getItem();
		agent = new MultiLoginAgent(1l, "Multilogin-Test", "Multilogin-Test", "", "c499422b-e88d-475d-8d66-468a1ef9c842", "");
		crawler = new Crawler();
		crawler.setAgent(agent);
		crawler.setProxy(proxy);
		
		service.setup(crawler);
	}

	
	@Test
	void checkLocationUS() {	
		// Define jobs for google search
		Joblist jobs = new Joblist();
		
		/* kept as template on how to grab correct verification values from the page */
		
		// open page
		 
		VisitJob visitJob = new VisitJob(1l, "visit geosurf", "visit geosurf", "https://geo.geosurf.io");
		jobs.appendJob(visitJob);
		
		// quick wait for search to finish
		WaitJob waitJob = new WaitJob(1l, "wait", "wait", 10);
		jobs.appendJob(waitJob);
		
		// Verify city and country
		AssertProxyByGeosurfJob assertProxyJob = new AssertProxyByGeosurfJob(1l, "Assert US / New York", "Assert US / New York", null, "US", "New York");
		jobs.appendJob(assertProxyJob);
		
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
