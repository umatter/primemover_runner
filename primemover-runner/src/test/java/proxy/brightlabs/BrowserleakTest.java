package proxy.brightlabs;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import primemover.model.ChromeAgent;
import primemover.model.Crawler;
import primemover.model.IAgent;
import primemover.model.Joblist;
import primemover.model.MultiLoginAgent;
import primemover.model.jobs.VisitJob;
import primemover.model.jobs.WaitJob;
import primemover.model.proxy.BrightdataProxy;
import primemover.model.proxy.IProxy;
import primemover.runner.ITaskOperator;
import primemover.runner.TaskOperator;
import primemover.runner.TaskOperatorFactory;

@SpringBootTest
public class BrowserleakTest {

	@Autowired
	private TaskOperatorFactory operatorFactory;

	private ITaskOperator operator;
	private IAgent agent;
	Crawler crawler;

	public void setup(IProxy proxy, boolean useMultiLoginAgent) {

		if (useMultiLoginAgent) {
			agent = new MultiLoginAgent(1l, "Multilogin", "Multilogin", "", null, null);
		} else {
			agent = new ChromeAgent(1l, "Chrome", "Chrome", "");
		}

		crawler = new Crawler();
		crawler.setAgent(agent);
		crawler.setProxy(proxy);

		operator = operatorFactory.getInstance(crawler);

		if (operator instanceof TaskOperator) {
			((TaskOperator) operator).setSkipIpRecording(true);
		}

		operator.prepareExeuction(crawler);
	}

	Joblist createJobList() {
		Joblist jobs = new Joblist();

		// IP Test
		VisitJob visitJob = new VisitJob(1l, "browserleak.com IP Test", "visit browserleak.com/ip",
				"https://browserleaks.com/ip", "https://browserleaks.com/ip");
		jobs.appendJob(visitJob);
		WaitJob waitJob = new WaitJob(1l, "wait", "wait", null, 1000);
		jobs.appendJob(waitJob);

		// Ad-blocker and Proxy-Detection test
		visitJob = new VisitJob(1l, "browserleak.com detector", "visit browserleak.com/proxy",
				"https://browserleaks.com/proxy", "https://browserleaks.com/proxy");
		jobs.appendJob(visitJob);
		waitJob = new WaitJob(1l, "wait", "wait", null, 10);
		jobs.appendJob(waitJob);

		return jobs;
	}

	void runStateProxyWithHeaders(boolean useMultiLoginAgent) {
		BrightdataProxy targetProxy = new BrightdataProxy("brd.superproxy.io", 22225,
				"brd-customer-hl_6766cbdd-zone-runner", "1ri4pdap64u5", "country-us-city-chicago", null);

		setup(targetProxy, useMultiLoginAgent);

		Joblist jobs = createJobList();

		boolean success = operator.runJobList(jobs);
		operator.tearDown(true);

		assertTrue(success, "joblist didn't finish successfully");
	}

	@Test
	void runStateProxyWithHeadersNonMultiLogin() {
		runStateProxyWithHeaders(false);
	}

	@Test
	void runStateProxyWithHeadersMultiLogin() {
		runStateProxyWithHeaders(true);
	}
}
