package primemover.util.consent;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

/**
 * Manual check against a live site, for when a consent dialog's markup may
 * have changed. Opt-in and network-dependent:
 *
 * <pre>
 * mvn test -pl primemover-runner -Dtest=ConsentHandlerLiveTest -Dconsent.liveUrl=https://www.youtube.com/watch?v=... \
 *     -Dconsent.liveChoice=reject -Dconsent.chrome=/usr/bin/google-chrome
 * </pre>
 *
 * Only meaningful from an IP that is served the dialog (EU/CH for YouTube).
 * Asserts that a dialog was found, answered, and gone afterwards.
 */
@EnabledIfSystemProperty(named = "consent.liveUrl", matches = "https?://.+")
class ConsentHandlerLiveTest {

	@Test
	void liveDialogIsAnswered() throws Exception {
		ChromeOptions options = new ChromeOptions();
		String binary = System.getProperty("consent.chrome");
		if (binary != null && !binary.isBlank()) {
			options.setBinary(binary);
		}
		Path scratch = Files.createTempDirectory("consent-live-profile");
		options.addArguments("--headless=new", "--no-sandbox", "--disable-gpu", "--window-size=1366,768",
				"--user-data-dir=" + scratch.toAbsolutePath());
		WebDriver driver = new ChromeDriver(options);
		try {
			driver.get(System.getProperty("consent.liveUrl"));
			Thread.sleep(4000);
			ConsentChoice choice = ConsentPolicy.runnerDefault(System.getProperty("consent.liveChoice", "accept"));

			boolean before = ConsentHandler.isPresent(driver);
			ConsentResult result = ConsentHandler.handle(driver, choice);
			System.out.println("LIVE consent: present-before=" + before + " result=" + result + " url-after="
					+ driver.getCurrentUrl());

			assertThat(before).as("a consent dialog was shown for this IP").isTrue();
			assertThat(result.clicked()).isTrue();
			assertThat(result.stillPresent()).as("dialog gone after the click").isFalse();
		} finally {
			driver.quit();
		}
	}
}
