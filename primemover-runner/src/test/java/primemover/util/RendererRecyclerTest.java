package primemover.util;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;

/**
 * The renderer bounce exists to stop a 113-job YouTube queue dying of
 * "Reached heap limit" two thirds of the way through. Its one hard requirement
 * is that it must never turn a working session into a broken one, so most of
 * these tests are about what it declines to do.
 */
class RendererRecyclerTest {

	@Test
	@DisplayName("bounces cross-origin and returns to the original URL")
	void recyclesAndReturns() {
		WebDriver driver = mock(WebDriver.class);
		when(driver.getCurrentUrl()).thenReturn("https://www.youtube.com/watch?v=abc");

		assertTrue(RendererRecycler.recycle(driver, 4179L));

		InOrder order = inOrder(driver);
		// Away first, then back: the order is the whole point -- it is what makes
		// Chrome destroy the youtube.com renderer holding the leaked heap.
		order.verify(driver).get(RendererRecycler.neutralUrl());
		order.verify(driver).get("https://www.youtube.com/watch?v=abc");
	}

	@Test
	@DisplayName("the neutral target is cross-origin, not about:blank")
	void neutralTargetForcesAProcessSwap() {
		// about:blank can inherit the current SiteInstance, which would leave the
		// leaking renderer alive and make the whole exercise a no-op.
		assertNotEquals("about:blank", RendererRecycler.neutralUrl());
		assertTrue(RendererRecycler.neutralUrl().startsWith("chrome://"));
	}

	@Test
	@DisplayName("does nothing when the page is not http(s)")
	void skipsNonHttpPages() {
		WebDriver driver = mock(WebDriver.class);
		when(driver.getCurrentUrl()).thenReturn("chrome-extension://abcdef/background.html");

		assertFalse(RendererRecycler.recycle(driver, 1L));
		verify(driver, never()).get(anyString());
	}

	@Test
	@DisplayName("a driver that cannot report a URL is left alone")
	void toleratesADriverThatThrows() {
		WebDriver driver = mock(WebDriver.class);
		when(driver.getCurrentUrl()).thenThrow(new WebDriverException("tab crashed"));

		assertFalse(RendererRecycler.recycle(driver, 2L));
		verify(driver, never()).get(anyString());
	}

	@Test
	@DisplayName("a failed bounce is swallowed, never propagated into the job")
	void aFailedBounceDoesNotFailTheJob() {
		WebDriver driver = mock(WebDriver.class);
		when(driver.getCurrentUrl()).thenReturn("https://www.youtube.com/");
		doThrow(new WebDriverException("navigation failed")).when(driver).get(RendererRecycler.neutralUrl());

		// Worst case is that we keep the old renderer and its heap, which is
		// exactly where we already were -- not a reason to fail the job.
		assertDoesNotThrow(() -> assertFalse(RendererRecycler.recycle(driver, 3L)));
	}

	@Test
	@DisplayName("a null driver is not an error")
	void nullDriverIsSafe() {
		assertFalse(RendererRecycler.recycle(null, 5L));
		assertFalse(RendererRecycler.onRecyclablePage(null));
	}

	@Test
	@DisplayName("onRecyclablePage only accepts real web pages")
	void recognisesRecyclablePages() {
		WebDriver http = mock(WebDriver.class);
		when(http.getCurrentUrl()).thenReturn("https://www.youtube.com/");
		assertTrue(RendererRecycler.onRecyclablePage(http));

		WebDriver blank = mock(WebDriver.class);
		when(blank.getCurrentUrl()).thenReturn("about:blank");
		assertFalse(RendererRecycler.onRecyclablePage(blank));

		WebDriver dead = mock(WebDriver.class);
		when(dead.getCurrentUrl()).thenThrow(new WebDriverException("tab crashed"));
		assertFalse(RendererRecycler.onRecyclablePage(dead));
	}
}
