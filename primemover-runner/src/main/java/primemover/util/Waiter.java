package primemover.util;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;

public class Waiter {

	//Wait Until JS Ready
	/**
	 * Waits for Javascript to finish loading
	 * 
	 * @param driver to run wait in
	 * @param timeout in seconds for max wait
	 */
    public static void waitUntilJSReady(WebDriver driver, Integer timeout) {
        //Wait for Javascript to load
        ExpectedCondition<Boolean> jsLoad = jsExec -> ((JavascriptExecutor) driver)
                .executeScript("return document.readyState").toString().equals("complete");

        //Get JS is Ready
        boolean jsReady = ((JavascriptExecutor) driver).executeScript("return document.readyState").toString().equals("complete");

        //Wait Javascript until it is Ready!
        if(!jsReady) {
            System.out.println("JS in NOT Ready!");
            //Wait for Javascript to load
            WebDriverWait wait = new WebDriverWait(driver, timeout);
            wait.until(jsLoad);
        } else {
            System.out.println("JS is Ready!");
        }
    }
}
