package primemover.util;

import java.util.Date;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class Waiter {
	private static Logger logger = LoggerFactory.getLogger(Waiter.class);
	
	/**
	 * Waits for Javascript to finish loading
	 * 
	 * @param driver to run wait in
	 * @param timeout in seconds for max wait
	 */
    public static Boolean waitUntilJSReady(WebDriver driver, Integer timeout) {
    	return waitUntilJSReady(driver, timeout, 1);
    }
    
	//Wait Until JS Ready
	/**
	 * Waits for Javascript to finish loading
	 * 
	 * @param driver to run wait in
	 * @param timeout in seconds for max wait
	 */
    public static Boolean waitUntilJSReady(WebDriver driver, Integer timeout, Integer numRetries) {
        //Wait for Javascript to load
        ExpectedCondition<Boolean> jsLoad = jsExec -> ((JavascriptExecutor) driver)
                .executeScript("return document.readyState").toString().equals("complete");

        //Get JS is Ready
        boolean jsReady = ((JavascriptExecutor) driver).executeScript("return document.readyState").toString().equals("complete");

        //Wait Javascript until it is Ready!
        if(!jsReady) {
        	logger.trace("JS is not ready -> wait for {} second(s)", timeout);
        	
        	for (int n = 0; n < numRetries; n++) {
        		logger.debug("trial {} of {}", n + 1, numRetries);
                
        		//Wait for Javascript to load
                try {
                	WebDriverWait wait = new WebDriverWait(driver, timeout);
                    wait.until(jsLoad);
                    logger.debug("successfully waited for js-ready");
                    
                    return true;
                } catch (TimeoutException ex){
                	logger.warn("timeout waiting for js-ready", ex);
                }
        	}
        	return false;
        	
        } else {
            logger.debug("JS is Ready");
            return true;
        }
    }

	

	public static void waitUntilObjectGone(WebDriver driver, Integer timeoutMillis, WebElement element) {
		boolean done = false;
		logger.trace("Waiting for element to vanish {} for {} millis", element, timeoutMillis);

		final long startMillis = System.currentTimeMillis();
		long curMillis;

		while (!done) {
			try {
				element.findElements(By.id("doesn't matter"));
			} catch (StaleElementReferenceException e) {
				done = true;
			}

			curMillis = System.currentTimeMillis();

			if ((curMillis - startMillis) > timeoutMillis) {
				logger.trace("element still here after timeout of {} millis {}", timeoutMillis, element);
				throw new TimeoutException("wait for object to vanish timed out " + element.toString());
			}

			try {
                Thread.sleep(50);
            } catch (Exception e) {
                // Nothing to do
            }
		}

		logger.trace("Element gone {}", element);
	}
}
