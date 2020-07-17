package primemover.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;

import primemover.model.EnterTextField;
import primemover.model.IJob;
import primemover.model.ScrollJob;
import primemover.model.SingleSelectUrl;
import primemover.model.VisitJob;
import primemover.model.WaitJob;
import selenium.util.Scrolling;
import selenium.util.Selecting;
import selenium.util.Waiter;

public class RunJobHandler {

	// Interface for our IJob-Handlers
	private interface Handler {
		void handle(WebDriver driver, IJob job);
	}

	// Handler registration
	@SuppressWarnings("rawtypes")
	private static final Map<Class, Handler> dispatch = new HashMap<Class, Handler>();

	// Populate handlers
	static {
		dispatch.put(VisitJob.class, new Handler() {
			public void handle(WebDriver driver, IJob job) {
				VisitJob typedJob = (VisitJob) job;
				driver.get(typedJob.getUrl());
				selenium.util.Waiter.waitUntilJSReady(driver, 120);
			}
		});

		dispatch.put(WaitJob.class, new Handler() {
			public void handle(WebDriver driver, IJob job) {
				WaitJob typedJob = (WaitJob) job;

				try {
					Thread.sleep(typedJob.getSeconds() * 1000);
				} catch (InterruptedException e) {
					// nothing to do
				}
			}
		});

		dispatch.put(ScrollJob.class, new Handler() {
			public void handle(WebDriver driver, IJob job) {
				ScrollJob typedJob = (ScrollJob) job;

				switch (typedJob.getDirection()) {
				case DOWN:
					Scrolling.scrollDownByJavascript(driver, typedJob.getLength());
					Waiter.waitUntilJSReady(driver, 60);
					break;
				case UP:
					Scrolling.scrollUpByJavascript(driver, typedJob.getLength());
					Waiter.waitUntilJSReady(driver, 60);
					break;
				default:
					throw new UnsupportedOperationException("Not implemented yet");
				}

			}
		});

		dispatch.put(EnterTextField.class, new Handler() {
			public void handle(WebDriver driver, IJob job) {
				EnterTextField typedJob = (EnterTextField) job;

				WebElement element = Selecting.getElementByWebdriver(driver, typedJob.getType(),
						typedJob.getSelector());

				element.sendKeys(typedJob.getValue());
				element.sendKeys(Keys.RETURN);
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					// nothing to do
				}

				selenium.util.Waiter.waitUntilJSReady(driver, 120);
			}
		});

		dispatch.put(SingleSelectUrl.class, new Handler() {
			public void handle(WebDriver driver, IJob job) {
				SingleSelectUrl typedJob = (SingleSelectUrl) job;

				List<WebElement> elements = Selecting.getElementsByWebdriver(driver, typedJob.getType(),
						typedJob.getSelector());
				WebElement selectedElement = Selecting.chooseElement(elements, typedJob.getDecision());

				if (selectedElement != null) {
					selectedElement.click();
				}

				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					// nothing to do
				}

				selenium.util.Waiter.waitUntilJSReady(driver, 120);
			}
		});
	}

	// Call the job specific handler
	public static void handle(WebDriver driver, IJob job) {
		Handler h = dispatch.get(job.getClass());
		if (h == null) {
			throw new UnsupportedOperationException("This type is not yet supported");
		}
		h.handle(driver, job); // <<== Here is the magic
	}
}
