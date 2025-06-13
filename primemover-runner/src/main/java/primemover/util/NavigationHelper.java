package primemover.util;

import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.FluentWait;
import org.openqa.selenium.support.ui.Wait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.TreeSet;
import java.util.function.Function;

public class NavigationHelper {
	private static Logger logger = LoggerFactory.getLogger(NavigationHelper.class);

	private WebDriver driver;
	WebElement old_page = null;
	long timeoutMillis;

	public NavigationHelper(WebDriver driver, long timeoutMillis) {
		this.driver = driver;
		this.timeoutMillis = timeoutMillis;

		init();
	}

	private WebElement checkAndGetHtmlElement(long timeoutMillis) {
		Wait<WebDriver> wait = new FluentWait<WebDriver>(driver).withTimeout(Duration.ofMillis(timeoutMillis))
				.pollingEvery(Duration.ofMillis(100)).ignoring(NoSuchElementException.class);

		try {
			WebElement elem = wait.until(new Function<WebDriver, WebElement>() {
				@Override
				public WebElement apply(WebDriver driver) {
					return driver.findElement(By.tagName("html"));
				}
			});

			return elem;
		} catch (Exception e) {
			return null;
		}
	}

	public void init() {
		try {
			old_page = checkAndGetHtmlElement(100);
			logger.debug("initialized");
		} catch (Exception e) {
			// nothing to do. Assume first page
			logger.debug("no initial html-tag found");
			old_page = null;
		}
	}

	public void waitForPageLoad(String target) throws InterruptedException {
		logger.debug("start waiting for page load for {}", driver.getCurrentUrl());
		boolean done = false;
		long start_millis = System.currentTimeMillis();
		long cur_millis = start_millis;

		// WORKAROUND for extension failure of Multilogin
		if (driver.getCurrentUrl().startsWith("chrome-extension://")) {
			logger.warn("Extension {} still loading. Trying workaround! Start running in new tab for {}...",
					driver.getCurrentUrl(), target);

			JavascriptExecutor js = (JavascriptExecutor) driver;

			js.executeScript("window.open('" + target + "', '_blank');");
			Thread.sleep(500);

			TreeSet<String> sortedHandles = new TreeSet<String>(driver.getWindowHandles());

			driver.switchTo().window(sortedHandles.last());
			// driver.switchTo().newWindow(WindowType.TAB);
			// driver.navigate().to(target);
		}

		// initial wait to allow browser to navigate
		try {
			Thread.sleep(Math.min(this.timeoutMillis, 500));
		} catch (Exception e) {
		}

		// repeat if current page is still there and not the new one
		int attempt = 1;
		while ((cur_millis - start_millis) < timeoutMillis) {
			try {
				WebElement new_page = checkAndGetHtmlElement(this.timeoutMillis);

				if (new_page != null && old_page != null) {
					if (!old_page.equals(new_page)) {
						done = true;
						logger.debug("new page loaded");
						break;
					}
				}

				if (new_page != null && old_page == null) {
					done = true;
					logger.debug("new page loaded");
					break;
				}

				attempt++;

				if ((attempt % 2) == 1) {
					logger.warn("new page not yet loaded during navigation for page {}", driver.getCurrentUrl());
				}
				Thread.sleep(100); // pause before next round

				cur_millis = System.currentTimeMillis();

			} catch (Exception e) {
				logger.warn("exception waiting for page load", e);
			}
		}

		if (!done) {
			logger.warn("potential failure during navigation.");
		}
	}
}
