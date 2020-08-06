package primemover.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import primemover.model.AssertElementExistsJob;
import primemover.model.AssertPageLoadedJob;
import primemover.model.AssertProxyByGeosurf;
import primemover.model.AssertScrolledMaxJob;
import primemover.model.EnterTextField;
import primemover.model.IJob;
import primemover.model.ScrollByDurationJob;
import primemover.model.ScrollByLengthJob;
import primemover.model.SingleSelectUrl;
import primemover.model.VisitJob;
import primemover.model.WaitJob;
import primemover.model.geosurf.IpInfo;
import selenium.util.Page;
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

		dispatch.put(ScrollByLengthJob.class, new Handler() {
			public void handle(WebDriver driver, IJob job) {
				ScrollByLengthJob typedJob = (ScrollByLengthJob) job;

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
		
		dispatch.put(ScrollByDurationJob.class, new Handler() {
			public void handle(WebDriver driver, IJob job) {
				ScrollByDurationJob typedJob = (ScrollByDurationJob) job;

				switch (typedJob.getDirection()) {
				case DOWN:
					Scrolling.scrollDownForDurationByJavascript(driver, typedJob.getDuration());
					Waiter.waitUntilJSReady(driver, 60);
					break;
				case UP:
					Scrolling.scrollUpForDurationByJavascript(driver, typedJob.getDuration());
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
		
		dispatch.put(AssertPageLoadedJob.class, new Handler() {
			public void handle(WebDriver driver, IJob job) {
				// AssertPageLoadedJob typedJob = (AssertPageLoadedJob) job;

				Boolean isReady = Page.isPageReady(driver);
				assertTrue("page is not ready", isReady);
			}
		});
		
		dispatch.put(AssertScrolledMaxJob.class, new Handler() {
			public void handle(WebDriver driver, IJob job) {
				AssertScrolledMaxJob typedJob = (AssertScrolledMaxJob) job;
				Boolean res;
				
				switch (typedJob.getDirection()) {
				case DOWN:
					res = Scrolling.isScrolledToBootom(driver);
					assertTrue("page is not scrolled to bottom", res);
					break;
				case UP:
					res = Scrolling.isScrolledToTop(driver);
					assertTrue("page is not scrolled to Top", res);
					break;
				default:
					throw new UnsupportedOperationException("Not implemented yet");
				}
			}
		});
		
		
		dispatch.put(AssertElementExistsJob.class, new Handler() {
			public void handle(WebDriver driver, IJob job) {
				AssertElementExistsJob typedJob = (AssertElementExistsJob) job;
				
				List<WebElement> elements = Selecting.getElementsByWebdriver(driver, typedJob.getType(),
						typedJob.getSelector());
				
				assertTrue("element(s) not found", elements.size() > 0);
			}
		});
		
		dispatch.put(AssertProxyByGeosurf.class, new Handler() {
			
			private ObjectMapper mapper = new ObjectMapper();
			
			public void handle(WebDriver driver, IJob job) {
				AssertProxyByGeosurf typedJob = (AssertProxyByGeosurf) job;
				
				// get IP-Info from geosurf
				driver.get("https://geo.geosurf.io/");
								
				// JSON is displayed within a pre-Tag in Chrome...
				String json = driver.findElement(By.tagName("pre")).getText();
				
				// map object
				try {
					IpInfo ipInfo = mapper.readValue(json, IpInfo.class);
					
					// verify
					if (StringUtils.hasText(typedJob.getIp())) {
						assertEquals(typedJob.getIp(), ipInfo.getIp());
					}
					if (StringUtils.hasText(typedJob.getCountry())) {
						assertEquals(typedJob.getCountry(), ipInfo.getCountry());
					}
					if (StringUtils.hasText(typedJob.getCity())) {
						assertEquals(typedJob.getCity(), ipInfo.getCity());
					}
					
				} catch (JsonMappingException e) {
					throw new AssertionError("Error mapping json", e);
				} catch (JsonProcessingException e) {
					throw new AssertionError("Error processing json", e);
				}
				
				
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
