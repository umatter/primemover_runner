package hsg.bot.runner;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import primemover.model.ChromeAgent;
import primemover.model.Crawler;
import primemover.model.IAgent;
import primemover.model.Joblist;
import primemover.model.SelectorType;
import primemover.model.TypingModeType;
import primemover.model.jobs.AssertTextInFieldJob;
import primemover.model.jobs.EnterTextFieldJob;
import primemover.model.jobs.SelectorBaseJob;
import primemover.model.jobs.VisitJob;
import primemover.model.jobs.WaitJob;
import primemover.runner.ITaskOperator;
import primemover.runner.TaskOperatorFactory;

@SpringBootTest
class Typing {

	@Autowired
	private TaskOperatorFactory operatorFactory;

	private ITaskOperator operator;
	private IAgent agent;
	Crawler crawler;

	@BeforeEach
	public void setup() {
		agent = new ChromeAgent(1l, "Chrome", "Chrome", "");
		crawler = new Crawler();
		crawler.setAgent(agent);
		operator = operatorFactory.getInstance(crawler);
	}

	@Test
	void openGoogleAndTypeInTextSimulatedWithoutTypos() {
		// Define jobs for google search
		Joblist jobs = new Joblist();

		// open page
		VisitJob visitJob = new VisitJob(1l, "visit google", "visit google", "https://www.google.ch", null);
		jobs.appendJob(visitJob);

		// enter search term
		SelectorBaseJob enterJob = new EnterTextFieldJob(1l, "enter value to text field", "enter value to text field",
				null, SelectorType.XPATH, "//input[@name='q']", "Klimaerwärmung", TypingModeType.SIMULATED_NOTYPOS,
				false);
		jobs.appendJob(enterJob);

		// verify search term
		AssertTextInFieldJob assertJob = new AssertTextInFieldJob(1l, "verify", "", SelectorType.XPATH,
				"//input[@name='q']", "Klimaerwärmung");
		jobs.appendJob(assertJob);

		// quick wait for search to finish

		operator.runJobList(jobs);
	}

	@Test
	void openGoogleAndTypeInTextSimulatedWithTypos() {
		// Define jobs for google search
		Joblist jobs = new Joblist();

		// open page
		VisitJob visitJob = new VisitJob(1l, "visit google", "visit google", "https://www.google.ch", null);
		jobs.appendJob(visitJob);

		// enter search term
		SelectorBaseJob enterJob = new EnterTextFieldJob(1l, "enter value to text field", "enter value to text field",
				null, SelectorType.XPATH, "//input[@name='q']", "Klimaerwärmung", TypingModeType.SIMULATED_KEEPINGTYPOS,
				false);
		jobs.appendJob(enterJob);

		jobs.appendJob(new WaitJob(1l, "Wait", "Wait", null, 10));

		// quick wait for search to finish

		operator.runJobList(jobs);
	}

	@Test
	void openGoogleAndTypeInTextSimulatedWithFixingTypos() {
		// Define jobs for google search
		Joblist jobs = new Joblist();

		// open page
		VisitJob visitJob = new VisitJob(1l, "visit google", "visit google", "https://www.google.ch", null);
		jobs.appendJob(visitJob);

		// enter search term
		SelectorBaseJob enterJob = new EnterTextFieldJob(1l, "enter value to text field", "enter value to text field",
				null, SelectorType.XPATH, "//input[@name='q']", "Klimaerwärmung", TypingModeType.SIMULATED_FIXINGTYPOS,
				false);
		jobs.appendJob(enterJob);

		jobs.appendJob(new WaitJob(1l, "Wait", "Wait", null, 10));

		// quick wait for search to finish

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
