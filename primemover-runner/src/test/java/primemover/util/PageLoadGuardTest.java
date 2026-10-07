package primemover.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WindowType;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
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

	/**
	 * A tab frozen the way experiment 189's were on 2026-10-07: it answers neither
	 * window.stop() nor about:blank.
	 */
	private static WebDriver frozenTab(String url) {
		WebDriver driver = driver();
		WebDriver.Navigation navigation = driver.navigate();
		if (url != null) {
			doThrow(new TimeoutException("Timed out receiving message from renderer: 58.5")).when(navigation).to(url);
		}
		when(((JavascriptExecutor) driver).executeScript("window.stop();"))
				.thenThrow(new WebDriverException("Timed out receiving message from renderer: 58.5"));
		doThrow(new WebDriverException("Timed out receiving message from renderer: 58.5")).when(navigation)
				.to("about:blank");
		return driver;
	}

	@Test
	@DisplayName("a frozen tab is left for a new one that answers, and the frozen one is closed")
	void frozenTabIsReplacedByANewTab() {
		WebDriver driver = frozenTab(null);
		when(driver.getWindowHandle()).thenReturn("frozen", "fresh");

		assertThat(PageLoadGuard.recover(driver, 1L, Duration.ofSeconds(5)))
				.isEqualTo(PageLoadGuard.Recovery.NEW_TAB);

		InOrder order = inOrder(driver, driver.switchTo());
		order.verify(driver.switchTo()).newWindow(WindowType.TAB);
		order.verify(driver.switchTo()).window("frozen");
		order.verify(driver).close();
		order.verify(driver.switchTo()).window("fresh");
	}

	@Test
	@DisplayName("a job whose tab was replaced fails as recovered (new tab), not as unresponsive")
	void newTabRecoveryIsNamedInTheMessage() {
		WebDriver driver = frozenTab("https://www.rottentomatoes.com/");
		when(driver.getWindowHandle()).thenReturn("frozen", "fresh");

		assertThatThrownBy(() -> PageLoadGuard.navigate(driver, "https://www.rottentomatoes.com/", 70001L))
				.isInstanceOf(TimeoutException.class).isNotInstanceOf(BrowserUnresponsiveException.class)
				.hasMessageContaining("browser recovered (new tab)");
	}

	@Test
	@DisplayName("a browser that cannot open a tab either is unresponsive, as before")
	void noNewTabMeansUnresponsive() {
		WebDriver driver = frozenTab("https://www.rottentomatoes.com/");
		when(driver.switchTo().newWindow(WindowType.TAB))
				.thenThrow(new WebDriverException("Timed out receiving message from renderer: 58.5"));

		assertThatThrownBy(() -> PageLoadGuard.navigate(driver, "https://www.rottentomatoes.com/", 70002L))
				.isInstanceOf(BrowserUnresponsiveException.class).hasMessageContaining("browser unresponsive");
	}

	@Test
	@DisplayName("a new tab that opens but does not answer is no recovery")
	void silentNewTabIsNoRecovery() {
		WebDriver driver = frozenTab(null);
		when(((JavascriptExecutor) driver).executeScript("return 1;"))
				.thenThrow(new WebDriverException("no answer"));

		assertThat(PageLoadGuard.recover(driver, 1L, Duration.ofSeconds(5)))
				.isEqualTo(PageLoadGuard.Recovery.UNRESPONSIVE);
	}

	@Test
	@DisplayName("a frozen tab that cannot be closed still leaves the browser on the new tab")
	void unclosableFrozenTabIsLeftOpen() {
		WebDriver driver = frozenTab(null);
		when(driver.getWindowHandle()).thenReturn("frozen", "fresh");
		doThrow(new WebDriverException("no answer")).when(driver).close();

		assertThat(PageLoadGuard.recover(driver, 1L, Duration.ofSeconds(5)))
				.isEqualTo(PageLoadGuard.Recovery.NEW_TAB);

		verify(driver.switchTo()).window("fresh");
	}

	@Test
	@DisplayName("each step is bounded: a browser that holds every command is given up on in steps, not minutes")
	void hangingCallsAreBounded() {
		WebDriver driver = driver();
		WebDriver.Navigation navigation = driver.navigate();
		org.mockito.stubbing.Answer<Object> hang = invocation -> {
			Thread.sleep(30_000);
			return null;
		};
		doAnswer(hang).when((JavascriptExecutor) driver).executeScript(anyString());
		doAnswer(hang).when(navigation).to("about:blank");
		WebDriver.TargetLocator locator = driver.switchTo();
		doAnswer(hang).when(locator).newWindow(WindowType.TAB);

		long started = System.nanoTime();
		PageLoadGuard.Recovery recovery = PageLoadGuard.recover(driver, 1L, Duration.ofMillis(200));
		Duration took = Duration.ofNanos(System.nanoTime() - started);

		assertThat(recovery).isEqualTo(PageLoadGuard.Recovery.UNRESPONSIVE);
		// window.stop(), about:blank, the frozen tab's handle and the new tab:
		// four bounded steps, nowhere near one 30 s call
		assertThat(took).isLessThan(Duration.ofSeconds(5));
	}

	@Test
	@DisplayName("a cut-off session (interrupted worker) stops recovering and is reported unresponsive")
	void interruptedWorkerStopsRecovering() {
		WebDriver driver = driver();
		Thread.currentThread().interrupt();
		try {
			assertThat(PageLoadGuard.recover(driver, 1L, Duration.ofSeconds(5)))
					.isEqualTo(PageLoadGuard.Recovery.UNRESPONSIVE);
			assertThat(Thread.currentThread().isInterrupted()).as("interrupt flag kept for the session loop")
					.isTrue();
		} finally {
			Thread.interrupted();
		}

		verify(driver.navigate(), never()).to("about:blank");
		verify(driver.switchTo(), never()).newWindow(WindowType.TAB);
	}
}
