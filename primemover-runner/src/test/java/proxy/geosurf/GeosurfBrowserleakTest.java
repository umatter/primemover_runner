package proxy.geosurf;

import java.util.HashMap;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import primemover.model.ChromeAgent;
import primemover.model.Configuration;
import primemover.model.Crawler;
import primemover.model.IAgent;
import primemover.model.Joblist;
import primemover.model.MediaOutlet;
import primemover.model.MultiLoginAgent;
import primemover.model.SelectionConfiguration;
import primemover.model.jobs.VisitJob;
import primemover.model.jobs.WaitJob;
import primemover.model.proxy.GeoSurfProxy;
import primemover.model.proxy.HttpProxy;
import primemover.model.proxy.IProxy;
import primemover.runner.ITaskOperator;
import primemover.runner.TaskOperatorFactory;

@SpringBootTest
public class GeosurfBrowserleakTest {

	@Autowired
	private TaskOperatorFactory operatorFactory;

	private ITaskOperator operator;
	private IAgent agent;
	Crawler crawler;

	public void setup(IProxy proxy, boolean useMultiLoginAgent) {

		if (useMultiLoginAgent) {
			agent = new MultiLoginAgent(1l, "Multilogin", "Multilogin", "", "", "");
		} else {
			agent = new ChromeAgent(1l, "Chrome", "Chrome", "");
		}

		crawler = new Crawler();
		crawler.setAgent(agent);

		Configuration config = new Configuration();
		config.setId(1);
		config.setDescription("Test Configuration browserleaks");
		config.setName("browserleaks.com");

		SelectionConfiguration selConfig = new SelectionConfiguration();
		selConfig.setId(1);
		selConfig.setPi(-1);
		selConfig.setPsi(0);
		selConfig.setKappa(1);
		selConfig.setTau(1);
		selConfig.setBeta(0.62368980888047);
		selConfig.setAlpha(0);
		HashMap<String, MediaOutlet> outlets = new HashMap<>();
		selConfig.setOutlets(outlets);

		config.setSelectionConfiguration(selConfig);
		crawler.setConfig(config);

		crawler.setProxy(proxy);

		operator = operatorFactory.getInstance(crawler);
		operator.prepareExeuction(crawler);
	}

	Joblist createJobList() {
		Joblist jobs = new Joblist();

		// IP Test
		VisitJob visitJob = new VisitJob(1l, "browserleak.com IP Test", "visit browserleak.com/ip",
				"https://browserleaks.com/ip", null);
		jobs.appendJob(visitJob);
		WaitJob waitJob = new WaitJob(1l, "wait", "wait", null, 30);
		jobs.appendJob(waitJob);

		// Ad-blocker and Proxy-Detection test
		visitJob = new VisitJob(1l, "browserleak.com detector", "visit browserleak.com/proxy",
				"https://browserleaks.com/proxy", null);
		jobs.appendJob(visitJob);
		waitJob = new WaitJob(1l, "wait", "wait", null, 30);
		jobs.appendJob(waitJob);

		return jobs;
	}

	void runStateProxyWithHeaders(boolean useMultiLoginAgent) {
		GeoSurfProxy targetProxy = new GeoSurfProxy("state.geosurf.io", 8000, "000000", "CHANGEME-password",
				"US-NY-NEW_YORK");
		setup(targetProxy, useMultiLoginAgent);

		Joblist jobs = createJobList();
		operator.runJobList(jobs);

		operator.tearDown(true);
	}

	@Test
	void runStateProxyWithHeadersNonMultiLogin() {
		runStateProxyWithHeaders(false);
	}

	@Test
	void runStateProxyWithHeadersMultiLogin() {
		runStateProxyWithHeaders(true);
	}

	void runStateProxyWithUsernameLocator(boolean useMultiLoginAgent) {
		HttpProxy targetProxy = new HttpProxy("us-30m.geosurf.io", 8000, "000000+US-NY-NEW_YORK+000000-589621",
				"CHANGEME-password");
		setup(targetProxy, useMultiLoginAgent);

		Joblist jobs = createJobList();
		operator.runJobList(jobs);

		try {
			Thread.sleep(600000);
		} catch (Exception ex) {

		}

		operator.tearDown(true);
	}

	@Test
	void runStateProxyWithUsernameLocatorNonMultiLogin() {
		runStateProxyWithUsernameLocator(false);
	}

	@Test
	void runStateProxyWithUsernameLocatorMultiLogin() {
		runStateProxyWithUsernameLocator(true);
	}
}
