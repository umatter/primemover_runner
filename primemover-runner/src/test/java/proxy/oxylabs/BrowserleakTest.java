package proxy.oxylabs;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.ZonedDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import lombok.extern.slf4j.Slf4j;
import primemover.model.ChromeAgent;
import primemover.model.Crawler;
import primemover.model.IAgent;
import primemover.model.Joblist;
import primemover.model.MultiLoginAgent;
import primemover.model.jobs.VisitJob;
import primemover.model.jobs.WaitJob;
import primemover.model.proxy.IProxy;
import primemover.model.proxy.OxylabsProxy;
import primemover.runner.ITaskOperator;
import primemover.runner.TaskExecutor;
import primemover.runner.TaskOperator;
import primemover.runner.TaskOperatorFactory;

@Slf4j
@SpringBootTest
@ActiveProfiles({ "dev", "test" })
@ExtendWith(MockitoExtension.class)
@ContextConfiguration(classes = TestConfig.class)
public class BrowserleakTest {

	@Autowired
	private TaskOperatorFactory operatorFactory;
	@Autowired
	private TaskExecutor taskExecutor;
	private ITaskOperator operator;
	private IAgent agent;
	private Crawler crawler;

	private void setup(IProxy proxy, boolean useMultiLoginAgent) {

		if (useMultiLoginAgent) {
			agent = new MultiLoginAgent(1l, "Multilogin", "Multilogin", "", null, null);
		} else {
			agent = new ChromeAgent(1l, "Firefox", "Firefox", "");
		}

		crawler = new Crawler();
		crawler.setId(1L);
		crawler.setAgent(agent);

		crawler.setProxy(proxy);

		operator = operatorFactory.getInstance(crawler);

		if (operator instanceof TaskOperator) {
			((TaskOperator) operator).setSkipIpRecording(true);
		}

	}

	Joblist createJobList() {
		Joblist jobs = new Joblist();
		jobs.setId(1L);
		jobs.setStartTime(ZonedDateTime.now());

		// IP Test
		VisitJob visitJob = new VisitJob(1l, "browserleak.com IP Test", "visit browserleak.com/ip",
				"https://browserleaks.com/ip", "https://browserleaks.com/ip");
		jobs.appendJob(visitJob);
		WaitJob waitJob = new WaitJob(1l, "wait", "wait", null, 10);
		jobs.appendJob(waitJob);

		// Ad-blocker and Proxy-Detection test
		visitJob = new VisitJob(2l, "browserleak.com detector", "visit browserleak.com/proxy",
				"https://browserleaks.com/proxy", "https://browserleaks.com/proxy");
		jobs.appendJob(visitJob);
		waitJob = new WaitJob(3l, "wait", "wait", null, 10);
		jobs.appendJob(waitJob);

		return jobs;
	}

	void runStateProxyWithHeaders(boolean useMultiLoginAgent) throws InterruptedException, ExecutionException {

		OxylabsProxy targetProxy = new OxylabsProxy("pr.oxylabs.io", 7777, "primemover_runner", "Primemover12345",
				"cc-US-city-chicago");

		setup(targetProxy, useMultiLoginAgent);

		Joblist jobs = createJobList();

		CompletableFuture<Boolean> success = taskExecutor.run(crawler, jobs);

		assertTrue(success.get(), "joblist didn't finish successfully");
	}

	@Test
	void runStateProxyWithHeadersNonMultiLogin() throws InterruptedException, ExecutionException {
		runStateProxyWithHeaders(false);
	}

	@Test
	void runStateProxyWithHeadersMultiLogin() throws InterruptedException, ExecutionException {
		runStateProxyWithHeaders(true);
	}
}
