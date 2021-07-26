package primemover.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

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

    public void init() {
        try {
        	old_page = driver.findElement(By.tagName("html"));
            logger.debug("initialized");
		} catch (NoSuchElementException e) {
			// nothing to do. Assume first page
			logger.debug("no initial html-tag found");
		}
    }

    public void waitForPageLoad() {
    	logger.debug("start waiting for page load");
        boolean done = false;
        final long startMillis = System.currentTimeMillis();
        long curMillis;

        while (!done) {
            WebElement new_page = null;

            try {
            	new_page = driver.findElement(By.tagName("html"));
            	
            	if (old_page == null) {
            		done = (new_page != null);
            	} else {
            		done = !(old_page.equals(new_page));
            	}
            	
			} catch (NoSuchElementException e) {
				// nothing to do. Assume still loading
			}

            if (done) {
            	break;
            }
            
            curMillis = System.currentTimeMillis();
            if ((curMillis - startMillis) > this.timeoutMillis) {
                logger.debug("timeout reached and page was done loading after timeout");
                throw new TimeoutException("timeout waiting for page load");
            }

            try {
                Thread.sleep(50);
            } catch (Exception e) {
                // Nothing to do
            }
        }

        logger.debug("page loaded");
    }
}
