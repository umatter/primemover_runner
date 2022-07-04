package primemover.runner.jobs.handler;

import primemover.model.Crawler;
import primemover.model.IJob;
import primemover.model.jobs.SingleSelectUrlJob;
import primemover.runner.context.ContextBase;
import primemover.runner.jobs.IJobHandler;
import primemover.util.SelectingHelper;

import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.ElementNotInteractableException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class SingleSelectUrlJobHandler implements IJobHandler {
	private static Logger logger = LoggerFactory.getLogger(SingleSelectUrlJobHandler.class);

	@Autowired
	private SelectingHelper selectingHelper;
	
    public void handle(ContextBase context, IJob job) {
        logger.debug("running job");

        WebDriver driver = context.getDriver();
		Crawler crawler = context.getCrawler();
		SingleSelectUrlJob typedJob = (SingleSelectUrlJob) job;

		List<WebElement> elements = SelectingHelper.getElementsByWebdriver(driver, typedJob.getType(),
				typedJob.getSelector());
		
		if (elements == null || elements.isEmpty()) {
			logger.warn("no element found");
			
			throw new IllegalStateException("no elements found");
		}
		
		WebElement selectedElement = selectingHelper.chooseElement(crawler, job, elements, typedJob.getDecisionType());

		if (selectedElement != null) {
			try {
				logger.debug("clicking element {}", selectedElement);
				
				// Wait for Element
				WebDriverWait wait = new WebDriverWait(driver, 5);
				wait.until(ExpectedConditions.elementToBeClickable(selectedElement));
				selectedElement.click();
				
			} catch (ElementNotInteractableException | TimeoutException e) {
				// Fallback hack for google-elements TODO: optimize with recursive try and error
				//List<WebElement> subSelements = selectedElement.findElements(By.xpath("./child::*"));
				List<WebElement> subSelements = selectedElement.findElements(By.tagName("div"));
				
				if (subSelements.size() > 0) {
					WebElement clickItem = subSelements.get(0);
					logger.debug("clicking fallback felement {}", clickItem);
				
					Actions actions = new Actions(driver);
					actions.moveToElement(clickItem);
					actions.perform();							
					
					clickItem.click();
				} else {
					throw new IllegalStateException("no clickable elements found", e);
				}	
			}					
		}

		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			// nothing to do
		}

		primemover.util.Waiter.waitUntilJSReady(driver, 120);
    }
}
