package hsg.bot.runner;

import java.util.HashMap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import primemover.anticaptcha.CaptchaSolvingType;
import primemover.model.Configuration;
import primemover.model.Crawler;
import primemover.model.DecisionType;
import primemover.model.DialogHandleType;
import primemover.model.FirefoxAgent;
import primemover.model.IAgent;
import primemover.model.Joblist;
import primemover.model.MediaOutlet;
import primemover.model.SelectionConfiguration;
import primemover.model.SelectorType;
import primemover.model.TypingModeType;
import primemover.model.jobs.Attribute;
import primemover.model.jobs.ClickJob;
import primemover.model.jobs.EnterTextFieldJob;
import primemover.model.jobs.HandleAlertJob;
import primemover.model.jobs.SelectorBaseJob;
import primemover.model.jobs.SingleSelectJob;
import primemover.model.jobs.SwitchToJob;
import primemover.model.jobs.VisitJob;
import primemover.model.jobs.WaitJob;
import primemover.model.proxy.GeoSurfProxy;
import primemover.model.proxy.HttpProxy;
import primemover.runner.ITaskOperator;
import primemover.runner.TaskOperatorFactory;

@SpringBootTest
class RunGoogleSearchOnFirefoxTest {

	@Autowired
	private TaskOperatorFactory operatorFactory;

	private ITaskOperator operator;
	private IAgent agent;
	Crawler crawler;

	@BeforeEach
	public void setup() {
		agent = new FirefoxAgent(1l, "firefox", "Firefox", "");
		crawler = new Crawler();
		crawler.setAgent(agent);

		// Defined proxy for US and city of New York for test!
		HttpProxy proxy = new GeoSurfProxy("state.geosurf.io", 8000, "000000", "CHANGEME-password", "US-NY-NEW_YORK");
		crawler.setProxy(proxy);

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

	void visitGoogleInclAgreementJobs(Joblist jobs, long jobIdStart) {
		// open page and aggree terms
		VisitJob visitJob = new VisitJob(jobIdStart, "visit google", "visit google", "https://www.google.com", null);
		jobs.appendJob(visitJob);

		jobIdStart++;
		SwitchToJob trySwitchToJob = new SwitchToJob(jobIdStart, "switching to agreement frame",
				"google accept aggreement", null, SelectorType.XPATH, "//iframe");
		trySwitchToJob.setIsFailsave(true);
		jobs.appendJob(trySwitchToJob);

		jobIdStart++;
		ClickJob tryClickJob = new ClickJob(jobIdStart, "click accept aggreement", "google accept aggreement", null,
				SelectorType.XPATH, "//*[@id=\"L2AGLb\"]/div", false);
		tryClickJob.setIsFailsave(true);
		jobs.appendJob(tryClickJob);

		jobIdStart++;
		WaitJob waitJob = new WaitJob(jobIdStart, "wait", "wait", null, 2);
		jobs.appendJob(waitJob);
	}

	void visitGoogleInclAgreementJobs(Joblist jobs) {
		visitGoogleInclAgreementJobs(jobs, 1l);
	}

	@Test
	void runGoogleSettingsChange() {
		// Define jobs for google search
		Joblist jobs = new Joblist();

		visitGoogleInclAgreementJobs(jobs);

		// quick wait for search to finish
		WaitJob waitJob = new WaitJob(5l, "wait", "wait", null, 1);
		jobs.appendJob(waitJob);

		// go to settings
		ClickJob tryClickJob = new ClickJob(6l, "click settings buttons", "google settings", null, SelectorType.XPATH,
				"//*[@id=\"Mses6b\"]", false);
		tryClickJob.setIsFailsave(true);
		jobs.appendJob(tryClickJob);

		// set to 40 results
		tryClickJob = new ClickJob(7l, "click settings buttons", "google settings", null, SelectorType.XPATH,
				"//*[@id=\"dEjpnf\"]/li[1]", false);
		tryClickJob.setIsFailsave(true);
		jobs.appendJob(tryClickJob);
		tryClickJob = new ClickJob(8l, "click settings buttons", "google settings", null, SelectorType.XPATH,
				"//*[@id=\"result_slider\"]/ol/li[3]", false);
		tryClickJob.setIsFailsave(true);
		jobs.appendJob(tryClickJob);
		tryClickJob = new ClickJob(9l, "click settings buttons", "google settings", null, SelectorType.XPATH,
				"//*[@id=\"result_slider\"]/ol/li[3]", false);
		tryClickJob.setIsFailsave(true);
		jobs.appendJob(tryClickJob);
		tryClickJob = new ClickJob(10l, "click settings buttons", "google settings", null, SelectorType.XPATH,
				"//*[@id=\"result_slider\"]/ol/li[3]", false);
		tryClickJob.setIsFailsave(true);
		jobs.appendJob(tryClickJob);

		// quick wait
		waitJob = new WaitJob(11l, "wait", "wait", null, 1);
		jobs.appendJob(waitJob);

		// save
		tryClickJob = new ClickJob(12l, "click settings buttons", "google settings", null, SelectorType.XPATH,
				"//*[@id=\"form-buttons\"]/div[1]", false);
		tryClickJob.setIsFailsave(true);
		jobs.appendJob(tryClickJob);

		// quick wait
		waitJob = new WaitJob(13l, "wait", "wait", null, 5);
		jobs.appendJob(waitJob);

		HandleAlertJob handleAlertJob = new HandleAlertJob(14l, "accept save settings", "save settings", null,
				DialogHandleType.ACCEPT);
		jobs.appendJob(handleAlertJob);

		// quick wait
		waitJob = new WaitJob(15l, "wait", "wait", null, 1);
		waitJob.setCaptchaSolving(CaptchaSolvingType.AFTER);
		jobs.appendJob(waitJob);

		waitJob = new WaitJob(16l, "wait", "wait", null, 50);
		jobs.appendJob(waitJob);

		// run
		operator.runJobList(jobs);
	}

	@Test
	void runGoogleSearchSelectFirst() {
		// Define jobs for google search
		Joblist jobs = new Joblist();

		visitGoogleInclAgreementJobs(jobs);

		// enter search term
		SelectorBaseJob enterJob = new EnterTextFieldJob(4l, "enter value to text field", "enter value to text field",
				null, SelectorType.XPATH, "//input[@name='q']", "Klimaerwärmung", TypingModeType.DIRECT, true);
		jobs.appendJob(enterJob);

		// quick wait for search to finish
		WaitJob waitJob = new WaitJob(5l, "wait", "wait", null, 10);
		jobs.appendJob(waitJob);

		// select url
		SingleSelectJob singleSelectUrlJob = new SingleSelectJob(6l, "select first entry", "select first entry", null,
				SelectorType.XPATH, ".//div[@class=\"yuRUbf\"]/a", DecisionType.FIRST, Attribute.HREF);
		jobs.appendJob(singleSelectUrlJob);

		// wait for search result
		waitJob = new WaitJob(7l, "wait", "wait", null, 10);
		jobs.appendJob(waitJob);

		operator.runJobList(jobs);
	}

	@Test
	void runGoogleSearchSelectLast() {
		// Define jobs for google search
		Joblist jobs = new Joblist();

		visitGoogleInclAgreementJobs(jobs);

		// enter search term
		SelectorBaseJob enterJob = new EnterTextFieldJob(5l, "enter value to text field", "enter value to text field",
				null, SelectorType.XPATH, "//input[@name='q']", "Klimaerwärmung", TypingModeType.DIRECT, true);
		jobs.appendJob(enterJob);

		// quick wait for search to finish
		WaitJob waitJob = new WaitJob(6l, "wait", "wait", null, 10);
		jobs.appendJob(waitJob);

		// select url
		SingleSelectJob singleSelectUrlJob = new SingleSelectJob(7l, "select first entry", "select first entry", null,
				SelectorType.XPATH, ".//div[@class=\"yuRUbf\"]/a", DecisionType.LAST, Attribute.HREF);
		jobs.appendJob(singleSelectUrlJob);

		// wait for search result
		waitJob = new WaitJob(8l, "wait", "wait", null, 10);
		jobs.appendJob(waitJob);

		operator.runJobList(jobs);
	}

	@Test
	void runGoogleSearchCalculated() {
		// Define jobs for google search
		Joblist jobs = new Joblist();

		visitGoogleInclAgreementJobs(jobs);

		// enter search term
		SelectorBaseJob enterJob = new EnterTextFieldJob(4l, "enter value to text field", "enter value to text field",
				null, SelectorType.XPATH, "//input[@name='q']", "news.yahoo.com", TypingModeType.DIRECT, true);
		jobs.appendJob(enterJob);

		// quick wait for search to finish
		WaitJob waitJob = new WaitJob(5l, "wait", "wait", null, 10);
		jobs.appendJob(waitJob);

		// select url
		// SingleSelectUrlJob singleSelectUrlJob = new SingleSelectUrlJob(1l, "select
		// first entry", "select first entry", SelectorType.CSS, "#rso > div > div >
		// div.yuRUbf > a", DecisionType.CALCULATED);
		// SingleSelectUrlJob singleSelectUrlJob = new SingleSelectUrlJob(1l, "select
		// first entry", "select first entry", SelectorType.XPATH,
		// ".//div[@id='rso']/div/div/div/a | .//div[@id='rso']/div/div/div/div/a",
		// DecisionType.CALCULATED);
		SingleSelectJob singleSelectUrlJob = new SingleSelectJob(6l, "select calculated entry",
				"select calculated entry", null, SelectorType.XPATH, ".//div[@class=\"yuRUbf\"]/a",
				DecisionType.CALCULATED, Attribute.HREF);
		jobs.appendJob(singleSelectUrlJob);

		// wait for search result
		waitJob = new WaitJob(7l, "wait", "wait", null, 10);
		jobs.appendJob(waitJob);

		operator.runJobList(jobs);
	}

	@Test
	void runGoogleSearchLongSearchTerm() {
		// Define jobs for google search
		Joblist jobs = new Joblist();

		visitGoogleInclAgreementJobs(jobs);

		// enter search term
		SelectorBaseJob enterJob = new EnterTextFieldJob(4l, "enter value to text field", "enter value to text field",
				null, SelectorType.XPATH, "//input[@name='q']", "Aufmerksamkeitsdefizit-Hyperaktivitätsstörung",
				TypingModeType.SIMULATED_NOTYPOS, true);
		jobs.appendJob(enterJob);

		// quick wait for search to finish
		WaitJob waitJob = new WaitJob(5l, "wait", "wait", null, 10);
		jobs.appendJob(waitJob);

		// select url
		SingleSelectJob singleSelectUrlJob = new SingleSelectJob(6l, "select first entry", "select first entry", null,
				SelectorType.XPATH, ".//div[@class=\"yuRUbf\"]/a", DecisionType.LAST, Attribute.HREF);
		jobs.appendJob(singleSelectUrlJob);

		// wait for search result
		waitJob = new WaitJob(7l, "wait", "wait", null, 10);
		jobs.appendJob(waitJob);

		operator.runJobList(jobs);
	}

	@Test
	void runGoogleSearchPhrase() {
		// Define jobs for google search
		Joblist jobs = new Joblist();

		visitGoogleInclAgreementJobs(jobs);

		// enter search term
		SelectorBaseJob enterJob = new EnterTextFieldJob(4l, "enter value to text field", "enter value to text field",
				null, SelectorType.XPATH, "//input[@name='q']", "Klimaerwärmung ist real",
				TypingModeType.SIMULATED_NOTYPOS, true);
		jobs.appendJob(enterJob);

		// quick wait for search to finish
		WaitJob waitJob = new WaitJob(5l, "wait", "wait", null, 10);
		jobs.appendJob(waitJob);

		// select url
		SingleSelectJob singleSelectUrlJob = new SingleSelectJob(6l, "select first entry", "select first entry", null,
				SelectorType.XPATH, ".//div[@class=\"yuRUbf\"]/a", DecisionType.LAST, Attribute.HREF);
		jobs.appendJob(singleSelectUrlJob);

		// wait for search result
		waitJob = new WaitJob(7l, "wait", "wait", null, 10);
		jobs.appendJob(waitJob);

		operator.runJobList(jobs);
	}

	@AfterEach
	public void tearDown() {
		operator.tearDown(true);
		operator = null;
		agent = null;
		crawler = null;
	}
}
