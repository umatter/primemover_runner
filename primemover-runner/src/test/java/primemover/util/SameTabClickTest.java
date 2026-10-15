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
