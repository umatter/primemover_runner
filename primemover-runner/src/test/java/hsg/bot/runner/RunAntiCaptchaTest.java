package hsg.bot.runner;

import java.util.HashMap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import primemover.anticaptcha.CaptchaSolvingType;
import primemover.model.ChromeAgent;
import primemover.model.Configuration;
import primemover.model.Crawler;
import primemover.model.FirefoxAgent;
import primemover.model.IAgent;
import primemover.model.Joblist;
import primemover.model.MediaOutlet;
import primemover.model.MultiLoginAgent;
import primemover.model.SelectionConfiguration;
import primemover.model.HtmlSelectorType;
import primemover.model.jobs.ClickJob;
import primemover.model.jobs.VisitJob;
import primemover.model.jobs.WaitJob;
import primemover.runner.ITaskOperator;
import primemover.runner.TaskOperatorFactory;

@SpringBootTest
class RunAntiCaptchaTest {

	@Autowired
	private TaskOperatorFactory operatorFactory;

	private ITaskOperator operator;
	private IAgent agent;
	Crawler crawler;

	public void setup() {
		crawler = new Crawler();
		crawler.setAgent(agent);

		Configuration config = new Configuration();
		config.setId(1);
		config.setDescription("Test Configuration for climate change");
		config.setName("Test Config");

		SelectionConfiguration selConfig = new SelectionConfiguration();
		selConfig.setId(1);
		selConfig.setPi(-1);
		selConfig.setPsi(0);
		selConfig.setKappa(1);
		selConfig.setTau(1);
		selConfig.setBeta(0.62368980888047);
		selConfig.setAlpha(0);
		HashMap<String, MediaOutlet> outlets = new HashMap<>();
		/*
		 * outlets.put("hillreporter.com", "https://hillreporter.com");
		 * outlets.put("nbcnews.com", "https://www.nbcnews.com"); outlets.put("pbs.org",
		 * "https://pbs.org"); outlets.put("opednews.com", "https://www.opednews.com");
		 * outlets.put("doinmytoons.blogspot.com", "https://doinmytoons.blogspot.com");
		 * outlets.put("news.yahoo.com", "https://news.yahoo.com");
		 * outlets.put("cbsnews.com", "https://www.cbsnews.com");
		 * outlets.put("esquire.com", "https://www.esquire.com"); outlets.put("amp.gs",
		 * "https://amplifr.com"); outlets.put("theroot.com",
		 * "https://www.theroot.com");
		 */
		selConfig.setOutlets(outlets);

		config.setSelectionConfiguration(selConfig);
		crawler.setConfig(config);

		operator = operatorFactory.getInstance(crawler);
	}

	private void runJobList() {
		// Define jobs for google search
		Joblist jobs = new Joblist();

		// open page and aggree terms
		VisitJob visitJob = new VisitJob(1l, "visit recaptcha test", "visit recaptcha test", "",
				"https://www.google.com/recaptcha/api2/demo");
		visitJob.setCaptchaSolving(CaptchaSolvingType.ALWAYS);
		jobs.appendJob(visitJob);

		// temp wait
		WaitJob waitJob = new WaitJob(2l, "wait", "wait", null, 10);
		jobs.appendJob(waitJob);

		ClickJob tryClickJob = new ClickJob(3l, "click accept aggreement", "google accept aggreement", null,
				HtmlSelectorType.XPATH, "//*[@id=\"recaptcha-demo-submit\"]", false);
		tryClickJob.setIsFailsave(true);
		jobs.appendJob(tryClickJob);

		// quick wait for search to finish
		waitJob = new WaitJob(4l, "wait", "wait", null, 10);
		jobs.appendJob(waitJob);

		operator.runJobList(jobs);
	}

	@Test
	void openRecaptchaV2WithChrome() {
		agent = new ChromeAgent(1l, "Chrome", "Chrome", "");
		setup();

		runJobList();
	}

	@Test
	void openRecaptchaV2WithFirefox() {
		agent = new FirefoxAgent(1l, "firefox", "firefox", "");
		setup();

		runJobList();
	}

	@Test
	void openRecaptchaV2WithMultilogin() {
		agent = new MultiLoginAgent(1l, "Multilogin", "Multilogin", "", "", "");
		setup();

		runJobList();
	}

	@AfterEach
	public void tearDown() {
		operator.tearDown(true);
		operator = null;
		agent = null;
		crawler = null;
	}

}
