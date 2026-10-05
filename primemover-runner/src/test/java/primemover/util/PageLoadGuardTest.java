package primemover.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * A page that never finishes loading must fail the visit in about a minute and
 * leave the browser answering, not cost the session what it cost on experiment
 * 189: ~27 minutes per job, and every later job in the session too.
 */
class PageLoadGuardTest {

	/** A driver that is also a JavascriptExecutor, as the real Remote one is. */
	private static WebDriver driver() {
		return mock(WebDriver.class, withSettings().extraInterfaces(JavascriptExecutor.class)
				.defaultAnswer(RETURNS_DEEP_STUBS));
	}

	@Test
	@DisplayName("the configured bound becomes the driver's page-load timeout")
	void appliesTheBound() {
		WebDriver driver = driver();

		assertThat(PageLoadGuard.apply(driver, 60)).isTrue();

		verify(driver.manage().timeouts()).pageLoadTimeout(Duration.ofSeconds(60));
	}

	@Test
	@DisplayName("zero leaves Selenium's default in force")
	void zeroDisablesTheBound() {
		WebDriver driver = driver();

		assertThat(PageLoadGuard.apply(driver, 0)).isFalse();

		verify(driver, never()).manage();
	}

	@Test
	@DisplayName("a driver that refuses the timeout is reported, not fatal")
	void refusedTimeoutIsNotFatal() {
		WebDriver driver = driver();
		when(driver.manage()).thenThrow(new IllegalStateException("no session"));

		assertThat(PageLoadGuard.apply(driver, 60)).isFalse();
	}

	@Test
	@DisplayName("a timed-out navigation stops the load, then fails the job")
	void timeoutStopsTheLoadAndFails() {
		WebDriver driver = driver();
		WebDriver.Navigation navigation = driver.navigate();
		doThrow(new TimeoutException("page load")).when(navigation).to("https://www.yelp.com/");

		assertThatThrownBy(() -> PageLoadGuard.navigate(driver, "https://www.yelp.com/", 65643L))
				.isInstanceOf(TimeoutException.class).hasMessageContaining("Page load timed out")
				.hasMessageContaining("browser recovered");

		verify((JavascriptExecutor) driver).executeScript("window.stop();");
	}

	@Test
	@DisplayName("a timeout on a browser that does not recover is a BrowserUnresponsiveException")
	void deadBrowserIsDistinguished() {
		WebDriver driver = driver();
		WebDriver.Navigation navigation = driver.navigate();
		doThrow(new TimeoutException("page load")).when(navigation).to("https://www.yelp.com/");
		when(((JavascriptExecutor) driver).executeScript(anyString()))
				.thenThrow(new org.openqa.selenium.WebDriverException("gone"));
		doThrow(new org.openqa.selenium.WebDriverException("gone")).when(navigation).to("about:blank");

		assertThatThrownBy(() -> PageLoadGuard.navigate(driver, "https://www.yelp.com/", 66251L))
				.isInstanceOf(BrowserUnresponsiveException.class).hasMessageContaining("browser unresponsive");
	}

	@Test
	@DisplayName("a recovered browser is a plain TimeoutException, so the session carries on")
	void recoveredBrowserIsNotUnresponsive() {
		WebDriver driver = driver();
		WebDriver.Navigation navigation = driver.navigate();
		doThrow(new TimeoutException("page load")).when(navigation).to("https://www.yelp.com/");

		assertThatThrownBy(() -> PageLoadGuard.navigate(driver, "https://www.yelp.com/", 1L))
				.isInstanceOf(TimeoutException.class).isNotInstanceOf(BrowserUnresponsiveException.class);
	}

	@Test
	@DisplayName("the cause chain is searched, since handlers wrap what they catch")
	void causeChainIsSearched() {
		Exception wrapped = new RuntimeException("error executing job",
				new BrowserUnresponsiveException("x", null));

		assertThat(BrowserUnresponsiveException.isCausedBy(wrapped)).isTrue();
		assertThat(BrowserUnresponsiveException.isCausedBy(new TimeoutException("slow"))).isFalse();
		assertThat(BrowserUnresponsiveException.isCausedBy(null)).isFalse();
	}

	@Test
	@DisplayName("a tab that cannot run window.stop() is sent to about:blank")
	void wedgedTabFallsBackToBlank() {
		WebDriver driver = driver();
		when(((JavascriptExecutor) driver).executeScript("window.stop();"))
				.thenThrow(new org.openqa.selenium.WebDriverException("script timeout"));

		assertThat(PageLoadGuard.recoverAfterTimeout(driver, 1L)).isTrue();

		verify(driver.navigate()).to("about:blank");
	}

	@Test
	@DisplayName("a browser that answers neither is reported unresponsive")
	void deadBrowserIsReported() {
		WebDriver driver = driver();
		WebDriver.Navigation navigation = driver.navigate();
		when(((JavascriptExecutor) driver).executeScript(anyString()))
				.thenThrow(new org.openqa.selenium.WebDriverException("gone"));
		doThrow(new org.openqa.selenium.WebDriverException("gone")).when(navigation).to("about:blank");

		assertThat(PageLoadGuard.recoverAfterTimeout(driver, 1L)).isFalse();
	}

	@Test
	@DisplayName("a navigation that succeeds is left alone")
	void successfulNavigationIsUntouched() {
		WebDriver driver = driver();

		PageLoadGuard.navigate(driver, "https://example.com/", 1L);

		verify(driver.navigate()).to("https://example.com/");
		verify((JavascriptExecutor) driver, never()).executeScript(anyString());
	}
}
