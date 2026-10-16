package primemover.util;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;

/**
 * A result click off YouTube stays in the session's tab, and the job can tell
 * whether it left the page. Bing marks every result link target="_blank"; before
 * this, the select job's click left the results page in place (experiment 201:
 * 15 of 15 sampled clicks), so the clicked page was never captured.
 */
class SameTabClickTest {

	private static final Duration FAST_POLL = Duration.ofMillis(5);
	private static final Duration FAST_SETTLE = Duration.ofMillis(20);

	@Test
	@DisplayName("targets that open another browsing context are recognised")
	void newContextTargets() {
		assertTrue(SameTabClick.opensNewBrowsingContext("_blank"));
		assertTrue(SameTabClick.opensNewBrowsingContext(" _BLANK "));
		assertTrue(SameTabClick.opensNewBrowsingContext("results")); // a named window
		assertFalse(SameTabClick.opensNewBrowsingContext(null));
		assertFalse(SameTabClick.opensNewBrowsingContext(""));
		assertFalse(SameTabClick.opensNewBrowsingContext("_self"));
		assertFalse(SameTabClick.opensNewBrowsingContext("_top"));
		assertFalse(SameTabClick.opensNewBrowsingContext("_parent"));
	}

	@Test
	@DisplayName("the script removes the target and reports what it removed")
	void keepInTabReportsRemovedTarget() {
		JavascriptExecutor js = mock(JavascriptExecutor.class);
		WebElement link = mock(WebElement.class);
		when(js.executeScript(SameTabClick.KEEP_IN_TAB_JS, link)).thenReturn("_blank");
		assertEquals("_blank", SameTabClick.keepInTab(js, link));
	}

	@Test
	@DisplayName("a failing script never costs the click")
	void keepInTabNeverThrows() {
		JavascriptExecutor js = mock(JavascriptExecutor.class);
		WebElement link = mock(WebElement.class);
		when(js.executeScript(anyString(), any())).thenThrow(new WebDriverException("stale element"));
		assertNull(SameTabClick.keepInTab(js, link));
	}

	@Test
	@DisplayName("a changed URL counts once it has settled, through a redirect")
	void navigatesThroughRedirect() throws InterruptedException {
		WebDriver driver = mock(WebDriver.class);
		when(driver.getWindowHandles()).thenReturn(Set.of("serp"));
		when(driver.getCurrentUrl()).thenReturn("https://www.bing.com/search?q=x",
				"https://www.bing.com/ck/a?!&&p=abc", "https://www.foxnews.com/politics");
		SameTabClick.Outcome outcome = SameTabClick.awaitNavigation(driver, "https://www.bing.com/search?q=x",
				Set.of("serp"), "serp", Duration.ofSeconds(2), FAST_POLL, FAST_SETTLE);
		assertEquals(SameTabClick.Outcome.NAVIGATED, outcome);
		verify(driver, never()).close();
	}

	@Test
	@DisplayName("a click that leaves the page in place is reported as such")
	void staysOnDecisionPage() throws InterruptedException {
		WebDriver driver = mock(WebDriver.class);
		when(driver.getWindowHandles()).thenReturn(Set.of("serp"));
		when(driver.getCurrentUrl()).thenReturn("https://www.bing.com/search?q=x");
		SameTabClick.Outcome outcome = SameTabClick.awaitNavigation(driver, "https://www.bing.com/search?q=x",
				Set.of("serp"), "serp", Duration.ofMillis(100), FAST_POLL, FAST_SETTLE);
		assertEquals(SameTabClick.Outcome.STAYED, outcome);
	}

	@Test
	@DisplayName("a new tab is followed and the results tab closed, leaving one tab")
	void followsNewTab() throws InterruptedException {
		WebDriver driver = mock(WebDriver.class);
		WebDriver.TargetLocator locator = mock(WebDriver.TargetLocator.class);
		when(driver.switchTo()).thenReturn(locator);
		Set<String> after = new LinkedHashSet<>(Set.of("serp", "landing"));
		when(driver.getWindowHandles()).thenReturn(after);
		SameTabClick.Outcome outcome = SameTabClick.awaitNavigation(driver, "https://www.bing.com/search?q=x",
				Set.of("serp"), "serp", Duration.ofSeconds(2), FAST_POLL, FAST_SETTLE);
		assertEquals(SameTabClick.Outcome.FOLLOWED_NEW_TAB, outcome);
		var order = inOrder(locator, driver);
		order.verify(locator).window("serp");
		order.verify(driver).close();
		order.verify(locator).window("landing");
	}

	@Test
	@DisplayName("a link announcing a popover is recognised, and a failing script says no")
	void opensPopover() {
		JavascriptExecutor js = mock(JavascriptExecutor.class);
		WebElement chip = mock(WebElement.class);
		WebElement plain = mock(WebElement.class);
		WebElement stale = mock(WebElement.class);
		when(js.executeScript(SameTabClick.OPENS_POPOVER_JS, chip)).thenReturn(Boolean.TRUE);
		when(js.executeScript(SameTabClick.OPENS_POPOVER_JS, plain)).thenReturn(Boolean.FALSE);
		when(js.executeScript(SameTabClick.OPENS_POPOVER_JS, stale)).thenThrow(new WebDriverException("stale"));
		assertTrue(SameTabClick.opensPopover(js, chip));
		assertFalse(SameTabClick.opensPopover(js, plain));
		assertFalse(SameTabClick.opensPopover(js, stale));
	}

	@Test
	@DisplayName("the link in the popover is returned once the popover shows it")
	void findsPopoverLink() throws InterruptedException {
		WebDriver driver = mock(WebDriver.class, withSettings().extraInterfaces(JavascriptExecutor.class));
		JavascriptExecutor js = (JavascriptExecutor) driver;
		WebElement chip = mock(WebElement.class);
		WebElement card = mock(WebElement.class);
		when(driver.getCurrentUrl()).thenReturn("https://www.bing.com/search?q=x");
		// not built yet, then failing mid-build, then there
		when(js.executeScript(SameTabClick.LINK_IN_POPOVER_JS, chip)).thenReturn(null)
				.thenThrow(new WebDriverException("detached")).thenReturn(card);
		assertSame(card, SameTabClick.awaitPopoverLink(driver, js, chip, "https://www.bing.com/search?q=x",
				Duration.ofSeconds(2), FAST_POLL));
	}

	@Test
	@DisplayName("no popover link is looked for once the page has left")
	void noPopoverLinkAfterNavigation() throws InterruptedException {
		WebDriver driver = mock(WebDriver.class, withSettings().extraInterfaces(JavascriptExecutor.class));
		JavascriptExecutor js = (JavascriptExecutor) driver;
		WebElement chip = mock(WebElement.class);
		when(driver.getCurrentUrl()).thenReturn("https://www.bing.com/ck/a?!&&p=abc");
		assertNull(SameTabClick.awaitPopoverLink(driver, js, chip, "https://www.bing.com/search?q=x",
				Duration.ofSeconds(2), FAST_POLL));
		verify(js, never()).executeScript(anyString(), any());
	}

	@Test
	@DisplayName("a popover that never shows the link gives up at the timeout")
	void popoverLinkTimesOut() throws InterruptedException {
		WebDriver driver = mock(WebDriver.class, withSettings().extraInterfaces(JavascriptExecutor.class));
		JavascriptExecutor js = (JavascriptExecutor) driver;
		WebElement chip = mock(WebElement.class);
		when(driver.getCurrentUrl()).thenReturn("https://www.bing.com/search?q=x");
		when(js.executeScript(SameTabClick.LINK_IN_POPOVER_JS, chip)).thenReturn(null);
		assertNull(SameTabClick.awaitPopoverLink(driver, js, chip, "https://www.bing.com/search?q=x",
				Duration.ofMillis(60), FAST_POLL));
	}

	@Test
	@DisplayName("a click that lands on the browser's error page reports its code")
	void browserErrorPageReportsCode() {
		JavascriptExecutor js = mock(JavascriptExecutor.class);
		when(js.executeScript(SameTabClick.BROWSER_ERROR_JS)).thenReturn("ERR_TUNNEL_CONNECTION_FAILED");
		assertEquals("ERR_TUNNEL_CONNECTION_FAILED", SameTabClick.browserErrorPage(js));
	}

	@Test
	@DisplayName("a real page, or a page that will not run the check, is not an error page")
	void browserErrorPageOtherwiseNull() {
		JavascriptExecutor real = mock(JavascriptExecutor.class);
		when(real.executeScript(SameTabClick.BROWSER_ERROR_JS)).thenReturn(null);
		assertNull(SameTabClick.browserErrorPage(real));
		JavascriptExecutor broken = mock(JavascriptExecutor.class);
		when(broken.executeScript(anyString())).thenThrow(new WebDriverException("no such window"));
		assertNull(SameTabClick.browserErrorPage(broken));
	}

	@Test
	@DisplayName("a page still loading at the timeout counts as left")
	void slowLandingCountsAsNavigated() throws InterruptedException {
		WebDriver driver = mock(WebDriver.class);
		when(driver.getWindowHandles()).thenReturn(Set.of("serp"));
		// Changes on every poll, so it never settles.
		final int[] n = { 0 };
		when(driver.getCurrentUrl()).thenAnswer(inv -> "https://www.example.com/p" + (n[0]++));
		SameTabClick.Outcome outcome = SameTabClick.awaitNavigation(driver, "https://www.bing.com/search?q=x",
				Set.of("serp"), "serp", Duration.ofMillis(100), FAST_POLL, Duration.ofSeconds(5));
		assertEquals(SameTabClick.Outcome.NAVIGATED, outcome);
	}
}
