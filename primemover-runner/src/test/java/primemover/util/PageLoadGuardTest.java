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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.LockSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

	/** The session's page-load timeout in these tests. */
	private static final Duration PAGE_LOAD = Duration.ofSeconds(1);

	/** margin, quick, new-tab page load, fallback: scaled down from the real ones. */
	private static final PageLoadGuard.Bounds FAST = new PageLoadGuard.Bounds(Duration.ofMillis(300),
			Duration.ofMillis(300), Duration.ofMillis(200), Duration.ofSeconds(1));

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
		when(driver.manage().timeouts().getPageLoadTimeout()).thenReturn(PAGE_LOAD);
		return driver;
	}

	@Test
	@DisplayName("a frozen tab is left for a new one that answers, and the frozen one is closed")
	void frozenTabIsReplacedByANewTab() {
		WebDriver driver = frozenTab(null);
		when(driver.getWindowHandle()).thenReturn("frozen", "fresh");

		assertThat(PageLoadGuard.recover(driver, 1L, FAST))
				.isEqualTo(PageLoadGuard.Recovery.NEW_TAB);

		WebDriver.Timeouts timeouts = driver.manage().timeouts();
		InOrder order = inOrder(driver, driver.switchTo(), timeouts);
		order.verify(timeouts).pageLoadTimeout(FAST.newTabPageLoad());
		order.verify(driver.switchTo()).newWindow(WindowType.TAB);
		order.verify(timeouts).pageLoadTimeout(PAGE_LOAD);
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

		// the short timeout NewWindow ran under does not outlive it
		WebDriver.Timeouts timeouts = driver.manage().timeouts();
		InOrder order = inOrder(timeouts, driver.switchTo());
		order.verify(timeouts).pageLoadTimeout(PageLoadGuard.BOUNDS.newTabPageLoad());
		order.verify(driver.switchTo()).newWindow(WindowType.TAB);
		order.verify(timeouts).pageLoadTimeout(PAGE_LOAD);
	}

	@Test
	@DisplayName("a page-load timeout that cannot be set back after the new tab leaves the browser unused")
	void unrestoredTimeoutIsNoRecovery() {
		WebDriver driver = frozenTab(null);
		when(driver.getWindowHandle()).thenReturn("frozen", "fresh");
		when(driver.manage().timeouts().pageLoadTimeout(PAGE_LOAD)).thenThrow(new WebDriverException("no answer"));

		assertThat(PageLoadGuard.recover(driver, 1L, FAST)).isEqualTo(PageLoadGuard.Recovery.UNRESPONSIVE);
	}

	@Test
	@DisplayName("without the driver's page-load timeout no new tab is tried, since its own could not be set back")
	void unknownTimeoutMeansNoNewTab() {
		WebDriver driver = frozenTab(null);
		when(driver.manage().timeouts().getPageLoadTimeout()).thenThrow(new WebDriverException("unsupported"));

		assertThat(PageLoadGuard.recover(driver, 1L, FAST)).isEqualTo(PageLoadGuard.Recovery.UNRESPONSIVE);

		verify(driver.switchTo(), never()).newWindow(WindowType.TAB);
		verify(driver.manage().timeouts(), never()).pageLoadTimeout(any(Duration.class));
	}

	@Test
	@DisplayName("a new tab that opens but does not answer is no recovery")
	void silentNewTabIsNoRecovery() {
		WebDriver driver = frozenTab(null);
		when(((JavascriptExecutor) driver).executeScript("return 1;"))
				.thenThrow(new WebDriverException("no answer"));

		assertThat(PageLoadGuard.recover(driver, 1L, FAST))
				.isEqualTo(PageLoadGuard.Recovery.UNRESPONSIVE);
	}

	@Test
	@DisplayName("a frozen tab that cannot be closed still leaves the browser on the new tab")
	void unclosableFrozenTabIsLeftOpen() {
		WebDriver driver = frozenTab(null);
		when(driver.getWindowHandle()).thenReturn("frozen", "fresh");
		doThrow(new WebDriverException("no answer")).when(driver).close();

		assertThat(PageLoadGuard.recover(driver, 1L, FAST))
				.isEqualTo(PageLoadGuard.Recovery.NEW_TAB);

		verify(driver.switchTo()).window("fresh");
	}

	@Test
	@DisplayName("a command the driver does not answer is the last one sent, and is given up on after its bound")
	void unansweredCommandIsTheLastOne() {
		WebDriver driver = driver();
		when(driver.manage().timeouts().getPageLoadTimeout()).thenReturn(Duration.ofMillis(200));
		WebDriver.Navigation navigation = driver.navigate();
		doAnswer(invocation -> {
			Thread.sleep(30_000);
			return null;
		}).when((JavascriptExecutor) driver).executeScript(anyString());

		long started = System.nanoTime();
		PageLoadGuard.Recovery recovery = PageLoadGuard.recover(driver, 1L, FAST);
		Duration took = Duration.ofNanos(System.nanoTime() - started);

		assertThat(recovery).isEqualTo(PageLoadGuard.Recovery.UNRESPONSIVE);
		// page-load timeout plus margin, nowhere near the 30 s the call holds
		assertThat(took).isLessThan(Duration.ofSeconds(3));
		// anything sent now would only queue behind the held command
		verify(navigation, never()).to("about:blank");
		verify(driver.switchTo(), never()).newWindow(WindowType.TAB);
	}

	/**
	 * ChromeDriver's handling of one session, as measured on 2026-10-07 with
	 * ChromeDriver 144 and Chrome 154: commands run one at a time; a command to
	 * the tab waits for its renderer at most the session's page-load timeout and
	 * then fails with the renderer timeout; giving up on a command client-side
	 * does not withdraw it. Session commands (handles, timeouts) are answered at
	 * once. NewWindow waits on the current tab like any other tab command.
	 */
	private static final class ChromeDriverSession {
		private final Object session = new Object();
		private final long rendererFreeAt;
		private volatile Duration pageLoad;
		final List<String> executed = Collections.synchronizedList(new ArrayList<>());

		/** @param rendererBusyFor how long the renderer stays busy; null for a frozen one */
		ChromeDriverSession(Duration pageLoad, Duration rendererBusyFor) {
			this.pageLoad = pageLoad;
			this.rendererFreeAt = rendererBusyFor == null ? Long.MAX_VALUE : System.nanoTime() + rendererBusyFor.toNanos();
		}

		Object tab(String command, Object result) {
			synchronized (session) {
				long giveUp = System.nanoTime() + pageLoad.toNanos();
				if (rendererFreeAt <= giveUp) {
					sleepUntil(rendererFreeAt);
					executed.add(command);
					return result;
				}
				sleepUntil(giveUp);
				executed.add(command + " failed");
				throw new TimeoutException("timeout: Timed out receiving message from renderer: "
						+ pageLoad.toMillis() / 1000.0);
			}
		}

		Object session(String command, Object result) {
			synchronized (session) {
				executed.add(command);
				return result;
			}
		}

		/** Ignores interrupts: a command ChromeDriver is running is not withdrawn. */
		private static void sleepUntil(long nanos) {
			for (long left = nanos - System.nanoTime(); left > 0; left = nanos - System.nanoTime()) {
				LockSupport.parkNanos(left);
			}
		}

		WebDriver driver() {
			WebDriver driver = PageLoadGuardTest.driver();
			WebDriver.Timeouts timeouts = driver.manage().timeouts();
			when(timeouts.getPageLoadTimeout()).thenAnswer(invocation -> session("getTimeouts", pageLoad));
			when(timeouts.pageLoadTimeout(any(Duration.class))).thenAnswer(invocation -> {
				Duration to = invocation.getArgument(0);
				session("setTimeouts " + to.toMillis(), null);
				pageLoad = to;
				return timeouts;
			});
			when(((JavascriptExecutor) driver).executeScript(anyString()))
					.thenAnswer(invocation -> tab("script " + invocation.getArgument(0), null));
			WebDriver.Navigation navigation = driver.navigate();
			doAnswer(invocation -> tab("navigate " + invocation.getArgument(0), null)).when(navigation)
					.to(anyString());
			when(driver.getWindowHandle()).thenAnswer(invocation -> session("getWindowHandle", "frozen"));
			when(driver.switchTo().newWindow(WindowType.TAB)).thenAnswer(invocation -> tab("newWindow", driver));
			return driver;
		}
	}

	@Test
	@DisplayName("a renderer that answers window.stop() late, inside the driver's own wait, is recovered without escalating")
	void lateWindowStopIsRecovered() {
		// scaled from the busy-renderer run: page load 60 s, window.stop() answered
		// 45 s after the timeout, which 10 s steps had reported unresponsive
		ChromeDriverSession chrome = new ChromeDriverSession(PAGE_LOAD, Duration.ofMillis(750));

		assertThat(PageLoadGuard.recover(chrome.driver(), 1L, FAST)).isEqualTo(PageLoadGuard.Recovery.STOPPED);

		assertThat(chrome.executed).containsExactly("getTimeouts", "script window.stop();");
	}

	@Test
	@DisplayName("a renderer busy into a second page-load timeout is recovered by about:blank, as before the new tab")
	void busyRendererIsRecoveredByBlank() {
		ChromeDriverSession chrome = new ChromeDriverSession(PAGE_LOAD, Duration.ofMillis(1500));

		assertThat(PageLoadGuard.recover(chrome.driver(), 1L, FAST)).isEqualTo(PageLoadGuard.Recovery.BLANK);

		assertThat(chrome.executed).containsExactly("getTimeouts", "script window.stop(); failed",
				"navigate about:blank");
	}

	@Test
	@DisplayName("a frozen renderer: each rung waits for the driver's answer, the new tab costs a short timeout, nothing is left queued")
	void frozenRendererIsGivenUpOnInOrder() {
		ChromeDriverSession chrome = new ChromeDriverSession(PAGE_LOAD, null);
		WebDriver driver = chrome.driver();

		long started = System.nanoTime();
		PageLoadGuard.Recovery recovery = PageLoadGuard.recover(driver, 1L, FAST);
		Duration took = Duration.ofNanos(System.nanoTime() - started);

		assertThat(recovery).isEqualTo(PageLoadGuard.Recovery.UNRESPONSIVE);
		// two page-load timeouts and the new tab's short one, not a third full one
		assertThat(took).isGreaterThanOrEqualTo(Duration.ofMillis(2200)).isLessThan(Duration.ofMillis(2900));
		List<String> sent = List.copyOf(chrome.executed);
		assertThat(sent).containsExactly("getTimeouts", "script window.stop(); failed", "navigate about:blank failed",
				"getWindowHandle", "setTimeouts 200", "newWindow failed", "setTimeouts 1000");

		// every command was answered before recover() returned
		LockSupport.parkNanos(Duration.ofMillis(300).toNanos());
		assertThat(chrome.executed).isEqualTo(sent);
	}

	@Test
	@DisplayName("a cut-off session (interrupted worker) stops recovering and is reported unresponsive")
	void interruptedWorkerStopsRecovering() {
		WebDriver driver = driver();
		Thread.currentThread().interrupt();
		try {
			assertThat(PageLoadGuard.recover(driver, 1L, FAST))
					.isEqualTo(PageLoadGuard.Recovery.UNRESPONSIVE);
			assertThat(Thread.currentThread().isInterrupted()).as("interrupt flag kept for the session loop")
					.isTrue();
		} finally {
			Thread.interrupted();
		}

		verify((JavascriptExecutor) driver, never()).executeScript(anyString());
		verify(driver.navigate(), never()).to("about:blank");
		verify(driver.switchTo(), never()).newWindow(WindowType.TAB);
	}
}
