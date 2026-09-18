package primemover.runner;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.DesiredCapabilities;
import primemover.model.AgentType;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two decisions that let a developer with no Multilogin account drive a bot
 * through plain Chrome: whether Chrome runs headless, and what happens to a
 * MULTILOGIN crawler on a runner where the MLX beans do not exist.
 *
 * Both are pure functions of a flag, which is why they are extracted -- asserting
 * them through {@code prepareExeuction} would need a browser, a display and a
 * Spring context.
 */
class TaskOperatorChromeDevTest {

	private static List<String> argumentsOf(ChromeOptions options) {
		@SuppressWarnings("unchecked")
		Map<String, Object> googOptions = (Map<String, Object>) options.asMap().get(ChromeOptions.CAPABILITY);

		@SuppressWarnings("unchecked")
		List<String> args = (List<String>) googOptions.get("args");

		return args;
	}

	private static DesiredCapabilities productionCapabilities() {
		DesiredCapabilities capabilities = new DesiredCapabilities();
		capabilities.setAcceptInsecureCerts(true);

		return capabilities;
	}

	@Test
	@DisplayName("runner.chromeHeadless=true adds --headless=new")
	void headlessFlagAddsHeadlessNew() {
		List<String> args = argumentsOf(TaskOperator.buildChromeOptions(productionCapabilities(), true));

		assertThat(args)
				.as("the modern headless mode, not the legacy --headless")
				.contains("--headless=new");
		assertThat(args)
				.as("plus what a box with no display needs")
				.contains("--disable-gpu", "--no-sandbox", "--window-size=1920,1080");
	}

	@Test
	@DisplayName("the default leaves the production option set untouched")
	void defaultIsHeadfulAndUnchanged() {
		List<String> args = argumentsOf(TaskOperator.buildChromeOptions(productionCapabilities(), false));

		assertThat(args)
				.as("headed Chrome must be byte-for-byte what it was before the flag existed")
				.containsExactly("--start-maximized", "--disable-blink-features",
						"--disable-blink-features=AutomationControlled");
	}

	@Test
	@DisplayName("a MULTILOGIN crawler is refused, with the reason, when Multilogin is disabled")
	void multiloginCrawlerIsRefusedWhenDisabled() {
		assertThat(TaskOperator.startupRefusalReason(AgentType.MULTILOGIN, false))
				.isEqualTo("Multilogin is disabled on this runner (multilogin.enabled=false); "
						+ "use a chrome agent or enable it");
	}

	@Test
	@DisplayName("nothing else is refused")
	void everythingElseStarts() {
		assertThat(TaskOperator.startupRefusalReason(AgentType.MULTILOGIN, true))
				.as("an enabled runner must still start Multilogin crawlers")
				.isNull();
		assertThat(TaskOperator.startupRefusalReason(AgentType.CHROME, false))
				.as("disabling Multilogin must cost a chrome crawler nothing -- that is the point")
				.isNull();
		assertThat(TaskOperator.startupRefusalReason(AgentType.FIREFOX, false))
				.isNull();
	}
}
