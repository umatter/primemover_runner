package primemover.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;


public class Waiter {
	private static Logger logger = LogManager.getLogger(Waiter.class);
	
	//Wait Until JS Ready
	/**
	 * Waits for Javascript to finish loading
	 * 
	 * @param driver to run wait in
	 * @param timeout in seconds for max wait
	 */
    public static Boolean waitUntilJSReady(WebDriver driver, Integer timeout) {
        //Wait for Javascript to load
        ExpectedCondition<Boolean> jsLoad = jsExec -> ((JavascriptExecutor) driver)
                .executeScript("return document.readyState").toString().equals("complete");

        //Get JS is Ready
        boolean jsReady = ((JavascriptExecutor) driver).executeScript("return document.readyState").toString().equals("complete");

        //Wait Javascript until it is Ready!
        if(!jsReady) {
            System.out.println("JS in NOT Ready!");
            //Wait for Javascript to load
            
            try {
            	WebDriverWait wait = new WebDriverWait(driver, timeout);
                wait.until(jsLoad);
                logger.debug("successfully waited for js-ready");
            } catch (TimeoutException ex){
            	logger.warn("timeout waiting for js-ready", ex);
            	return false;
            }
            
        } else {
            System.out.println("JS is Ready!");
        }
        
        return true;
    }
}
