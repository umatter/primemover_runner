package primemover.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.function.Function;

import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.FluentWait;
import org.openqa.selenium.support.ui.Wait;

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
    	Wait<WebDriver> wait = new FluentWait<WebDriver>(driver)
          	  .withTimeout(Duration.ofMillis(timeoutMillis))
          	  .pollingEvery(Duration.ofMillis(100))
          	  .ignoring(NoSuchElementException.class);
    	
    	
    	try {
        	WebElement elem = wait.until(new Function<WebDriver, WebElement>() {
        	  public WebElement apply(WebDriver driver) {
        	    return driver.findElement(By.tagName("html"));
        	  }
        	});
        	
        	return elem;
    	} catch(Exception e) {
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

    public void waitForPageLoad() {
    	logger.debug("start waiting for page load");
    	boolean done = false;
    	long start_millis = System.currentTimeMillis();
    	long cur_millis = start_millis;
    	
    	// initial wait to allow browser to navigate
        try {
            Thread.sleep(Math.min(this.timeoutMillis, 1000));
        } catch (Exception e) {
            // Nothing to do
        }
        
    	// repeat if current page is still there and not the new one
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
            	
            	logger.warn("new page not yet loaded during navigation. Repeating until timeout reached.");
            	Thread.sleep(50); // pause before next round
            	
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
