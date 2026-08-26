package primemover.util.consent;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

/**
 * Drives the real consent script in headless Chrome against fixture pages whose
 * markup is copied from live sites (the YouTube consent bump as served to an EU
 * IP on 2026-08-26, OneTrust, a generic French banner).
 *
 * <p>Needs a Chrome binary, so it is opt-in:
 * {@code mvn test -Dconsent.browserTest=true -Dtest=ConsentHandlerBrowserTest}
 * (set {@code -Dconsent.chrome=/path/to/chrome} if it is not on the PATH).
 * Selenium Manager fetches a matching chromedriver on first run.
 */
@EnabledIfSystemProperty(named = "consent.browserTest", matches = "true")
class ConsentHandlerBrowserTest {

	private static WebDriver driver;
	private static Path scratch;

	@BeforeAll
	static void startChrome() throws Exception {
		ChromeOptions options = new ChromeOptions();
		String binary = System.getProperty("consent.chrome");
		if (binary != null && !binary.isBlank()) {
			options.setBinary(binary);
		}
		scratch = Files.createTempDirectory("consent-test-profile");
		options.addArguments("--headless=new", "--no-sandbox", "--disable-gpu", "--window-size=1366,768",
				"--user-data-dir=" + scratch.toAbsolutePath());
		driver = new ChromeDriver(options);
	}

	@AfterAll
	static void stopChrome() {
		if (driver != null) {
			driver.quit();
		}
	}

	@BeforeEach
	void unbind() {
		ConsentPolicy.unbind();
	}

	private static void open(String fixture) {
		URL url = ConsentHandlerBrowserTest.class.getResource("/consent/" + fixture);
		assertThat(url).as("fixture " + fixture).isNotNull();
		driver.get(url.toString());
	}

	private static Object clicked() {
		return ((JavascriptExecutor) driver).executeScript("return window.__clicked || null;");
	}

	@Test
	@DisplayName("YouTube consent bump: accept clicks 'Accept all', not the player's Close button")
	void youtubeAccept() {
		open("youtube-bump-en.html");
		assertThat(ConsentHandler.isPresent(driver)).isTrue();

		ConsentResult r = ConsentHandler.handle(driver, ConsentChoice.ACCEPT);

		assertThat(r.present()).isTrue();
		assertThat(r.clicked()).isTrue();
		assertThat(r.cmp()).isEqualTo("youtube");
		assertThat(r.label()).isEqualTo("accept all");
		assertThat(r.stillPresent()).isFalse();
		assertThat(clicked()).isEqualTo("accept");
		assertThat(ConsentHandler.isPresent(driver)).isFalse();
	}

	@Test
	void youtubeReject() {
		open("youtube-bump-en.html");
		ConsentResult r = ConsentHandler.handle(driver, ConsentChoice.REJECT);
		assertThat(r.clicked()).isTrue();
		assertThat(r.label()).isEqualTo("reject all");
		assertThat(clicked()).isEqualTo("reject");
	}

	@Test
	@DisplayName("YouTube consent bump in German is answered by wording, not by position")
	void youtubeGerman() {
		open("youtube-bump-de.html");
		ConsentResult r = ConsentHandler.handle(driver, ConsentChoice.REJECT);
		assertThat(r.cmp()).isEqualTo("youtube");
		assertThat(r.label()).isEqualTo("alle ablehnen");
		assertThat(clicked()).isEqualTo("reject");

		open("youtube-bump-de.html");
		r = ConsentHandler.handle(driver, ConsentChoice.ACCEPT);
		assertThat(r.label()).isEqualTo("alle akzeptieren");
		assertThat(clicked()).isEqualTo("accept");
	}

	@Test
	@DisplayName("ignore leaves the dialog in place and reports it")
	void youtubeIgnore() {
		open("youtube-bump-en.html");
		ConsentResult r = ConsentHandler.handle(driver, ConsentChoice.IGNORE);
		assertThat(r.present()).isTrue();
		assertThat(r.clicked()).isFalse();
		assertThat(r.action()).isEqualTo("ignore");
		assertThat(clicked()).isNull();
		assertThat(ConsentHandler.isPresent(driver)).isTrue();
	}

	@Test
	void onetrustUsesItsOwnButtonIds() {
		open("onetrust.html");
		ConsentResult r = ConsentHandler.handle(driver, ConsentChoice.REJECT);
		assertThat(r.cmp()).isEqualTo("onetrust");
		assertThat(clicked()).isEqualTo("reject");

		open("onetrust.html");
		r = ConsentHandler.handle(driver, ConsentChoice.ACCEPT);
		assertThat(clicked()).isEqualTo("accept");
	}

	@Test
	@DisplayName("generic banner, French: 'Continuer sans accepter' is a reject, 'Personnaliser' is neither")
	void genericFrench() {
		open("generic-fr.html");
		ConsentResult r = ConsentHandler.handle(driver, ConsentChoice.REJECT);
		assertThat(r.cmp()).isEqualTo("generic");
		assertThat(clicked()).isEqualTo("reject");

		open("generic-fr.html");
		r = ConsentHandler.handle(driver, ConsentChoice.ACCEPT);
		assertThat(r.label()).isEqualTo("tout accepter");
		assertThat(clicked()).isEqualTo("accept");
	}

	@Test
	@DisplayName("a page without a dialog is left alone (footer cookie link, hidden button, player Close)")
	void noDialog() {
		open("no-dialog.html");
		assertThat(ConsentHandler.isPresent(driver)).isFalse();
		ConsentResult r = ConsentHandler.handle(driver, ConsentChoice.ACCEPT);
		assertThat(r).isEqualTo(ConsentResult.NOT_PRESENT);
		assertThat(clicked()).isNull();
	}

	@Test
	@DisplayName("the thread-bound policy is what handle(driver) applies")
	void usesBoundPolicy() {
		ConsentPolicy.bind(ConsentChoice.REJECT);
		open("youtube-bump-en.html");
		ConsentHandler.handle(driver);
		assertThat(clicked()).isEqualTo("reject");
	}
}
