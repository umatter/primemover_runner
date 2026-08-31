package primemover.util;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;

/**
 * The re-anchor exists because one bad landing used to cost a whole session:
 * soak3_us_04 on experiment 147 finished 86 of 284 jobs after a YouTube SPA
 * navigation left it on an empty shell, and nothing in the workflow ever
 * navigated again.
 *
 * <p>
 * Its hard requirements are the mirror image of that: recover when a reload
 * would help, never loop, and never let a rescued page pass as an ordinary one.
 */
class WatchPageAnchorTest {

	// Short waits: these tests are about control flow, not about timing.
	private static final Duration FIRST = Duration.ofMillis(120);
	private static final Duration SECOND = Duration.ofMillis(120);
	private static final Duration POLL = Duration.ofMillis(20);

	private static List<WebElement> tiles(int n) {
		return n == 0 ? Collections.emptyList() : Collections.nCopies(n, mock(WebElement.class));
	}

	private static WatchPageAnchor.Outcome run(WebDriver driver, boolean enabled) {
		return WatchPageAnchor.ensureRecommendations(driver, 12691L, enabled, FIRST, SECOND, POLL);
	}

	@Test
	@DisplayName("a page that already has recommendations is left completely alone")
	void presentPageIsUntouched() {
		WebDriver driver = mock(WebDriver.class);
		when(driver.findElements(any(By.class))).thenReturn(tiles(20));

		assertEquals(WatchPageAnchor.Outcome.PRESENT, run(driver, true));
		// The common case must cost nothing: no reload on a healthy page.
		verify(driver, never()).navigate();
	}

	@Test
	@DisplayName("an empty shell is reloaded once and reported RECOVERED")
	void emptyShellIsRecovered() {
		WebDriver driver = mock(WebDriver.class);
		WebDriver.Navigation navigation = mock(WebDriver.Navigation.class);
		when(driver.navigate()).thenReturn(navigation);

		// Empty until the reload, populated after it -- the production symptom,
		// tied to the reload itself rather than to a call count, so the test
		// cannot pass by accident if the wait's polling changes.
		AtomicBoolean reloaded = new AtomicBoolean(false);
		doAnswer(invocation -> {
			reloaded.set(true);
			return null;
		}).when(navigation).refresh();
		when(driver.findElements(any(By.class)))
				.thenAnswer(invocation -> reloaded.get() ? tiles(20) : tiles(0));

		assertEquals(WatchPageAnchor.Outcome.RECOVERED, run(driver, true));
		verify(navigation, times(1)).refresh();
	}

	@Test
	@DisplayName("a genuinely empty page is reloaded at most once, then given up on")
	void neverLoops() {
		WebDriver driver = mock(WebDriver.class);
		WebDriver.Navigation navigation = mock(WebDriver.Navigation.class);
		when(driver.navigate()).thenReturn(navigation);
		when(driver.findElements(any(By.class))).thenReturn(tiles(0));

		assertEquals(WatchPageAnchor.Outcome.ABSENT, run(driver, true));
		// Retrying forever against a members-only or removed video would burn
		// the session just as thoroughly as the bug this fixes.
		verify(navigation, times(1)).refresh();
	}

	@Test
	@DisplayName("recovery disabled restores the old behaviour: no reload at all")
	void disabledDoesNotReload() {
		WebDriver driver = mock(WebDriver.class);
		when(driver.findElements(any(By.class))).thenReturn(tiles(0));

		assertEquals(WatchPageAnchor.Outcome.ABSENT, run(driver, false));
		verify(driver, never()).navigate();
	}

	@Test
	@DisplayName("a driver that cannot reload does not abort the job")
	void reloadFailureIsNotFatal() {
		WebDriver driver = mock(WebDriver.class);
		WebDriver.Navigation navigation = mock(WebDriver.Navigation.class);
		when(driver.navigate()).thenReturn(navigation);
		when(driver.findElements(any(By.class))).thenReturn(tiles(0));
		doThrow(new WebDriverException("tab crashed")).when(navigation).refresh();

		// The decision that follows reports the missing recommendations properly;
		// throwing from here would replace a clear diagnosis with a stack trace.
		assertEquals(WatchPageAnchor.Outcome.ABSENT, run(driver, true));
	}

	@Test
	@DisplayName("a driver that throws while being queried is treated as empty, not fatal")
	void queryFailureIsNotFatal() {
		WebDriver driver = mock(WebDriver.class);
		WebDriver.Navigation navigation = mock(WebDriver.Navigation.class);
		when(driver.navigate()).thenReturn(navigation);
		when(driver.findElements(any(By.class))).thenThrow(new WebDriverException("no such session"));

		assertEquals(WatchPageAnchor.Outcome.ABSENT, run(driver, true));
	}

	@Test
	@DisplayName("the selector matches both containers YouTube renders into")
	void selectorCoversBothContainers() {
		// #related is the classic container; #secondary is what a number of the
		// captured pages actually used. Losing either silently halves detection.
		String selector = WatchPageAnchor.recommendationsSelector().toString();
		assertTrue(selector.contains("#related"), selector);
		assertTrue(selector.contains("#secondary"), selector);
		assertTrue(selector.contains("yt-lockup-view-model"), selector);
	}
}
