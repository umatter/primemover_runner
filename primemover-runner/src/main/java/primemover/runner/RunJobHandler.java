package primemover.runner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import primemover.model.IJob;
import primemover.model.geosurf.IpInfo;
import primemover.model.jobs.AssertElementExistsJob;
import primemover.model.jobs.AssertPageLoadedJob;
import primemover.model.jobs.AssertProxyByGeosurfJob;
import primemover.model.jobs.AssertScrolledMaxJob;
import primemover.model.jobs.EnterTextFieldJob;
import primemover.model.jobs.ScrollByDurationJob;
import primemover.model.jobs.ScrollByLengthJob;
import primemover.model.jobs.SingleSelectUrlJob;
import primemover.model.jobs.TryClickJob;
import primemover.model.jobs.VisitJob;
import primemover.model.jobs.WaitJob;
import primemover.util.Page;
import primemover.util.Scrolling;
import primemover.util.Selecting;
import primemover.util.Waiter;

public class RunJobHandler {

	private static Logger logger = LoggerFactory.getLogger(RunJobHandler.class);
	
	
	
	// Interface for our IJob-Handlers
	private interface Handler {
		void handle(WebDriver driver, IJob job);
	}

	// Handler registration
	@SuppressWarnings("rawtypes")
	private static final Map<Class, Handler> dispatch = new HashMap<Class, Handler>();

	// Populate handlers
	static {
		logger.trace("populating handlers");
		logger.trace("creating VisitJob-Handler");
		dispatch.put(VisitJob.class, new Handler() {
			public void handle(WebDriver driver, IJob job) {
				VisitJob typedJob = (VisitJob) job;
				driver.get(typedJob.getUrl());
				primemover.util.Waiter.waitUntilJSReady(driver, 120);
			}
		});

		logger.trace("creating WaitJob-Handler");
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


		logger.trace("creating ScrollByLengthJob-Handler");
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
		

		logger.trace("creating ScrollByDurationJob-Handler");
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

		logger.trace("creating EnterTextFieldJob-Handler");
		dispatch.put(EnterTextFieldJob.class, new Handler() {
			public void handle(WebDriver driver, IJob job) {
				EnterTextFieldJob typedJob = (EnterTextFieldJob) job;

				WebElement element = Selecting.getElementByWebdriver(driver, typedJob.getType(),
						typedJob.getSelector());

				element.sendKeys(typedJob.getValue());
				element.sendKeys(Keys.RETURN);
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					// nothing to do
				}

				primemover.util.Waiter.waitUntilJSReady(driver, 120);
			}
		});

		logger.trace("creating SingleSelectUrlJob-Handler");
		dispatch.put(SingleSelectUrlJob.class, new Handler() {
			public void handle(WebDriver driver, IJob job) {
				SingleSelectUrlJob typedJob = (SingleSelectUrlJob) job;

				List<WebElement> elements = Selecting.getElementsByWebdriver(driver, typedJob.getType(),
						typedJob.getSelector());
				WebElement selectedElement = Selecting.chooseElement(elements, typedJob.getDecisionType());

				if (selectedElement != null) {
					selectedElement.click();
				}

				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					// nothing to do
				}

				primemover.util.Waiter.waitUntilJSReady(driver, 120);
			}
		});
		
		logger.trace("creating TryClickJob-Handler");
		dispatch.put(TryClickJob.class, new Handler() {
			public void handle(WebDriver driver, IJob job) {
				TryClickJob typedJob = (TryClickJob) job;

				List<WebElement> elements = Selecting.getElementsByWebdriver(driver, typedJob.getType(),
						typedJob.getSelector());
				
				if (elements.size() == 1) {
					WebElement selectedElement = elements.get(0);
					
					selectedElement.click();
				}
				
				try {
					Thread.sleep(500);
				} catch (InterruptedException e) {
					// nothing to do
				}
			}
		});
		
		logger.trace("creating AssertPageLoadedJob-Handler");
		dispatch.put(AssertPageLoadedJob.class, new Handler() {
			public void handle(WebDriver driver, IJob job) {
				// AssertPageLoadedJob typedJob = (AssertPageLoadedJob) job;

				Boolean isReady = Page.isPageReady(driver);
				assertTrue("page is not ready", isReady);
			}
		});
		
		logger.trace("creating AssertScrolledMaxJob-Handler");
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
		
		logger.trace("creating AssertElementExistsJob-Handler");
		dispatch.put(AssertElementExistsJob.class, new Handler() {
			public void handle(WebDriver driver, IJob job) {
				AssertElementExistsJob typedJob = (AssertElementExistsJob) job;
				
				List<WebElement> elements = Selecting.getElementsByWebdriver(driver, typedJob.getType(),
						typedJob.getSelector());
				
				assertTrue("element(s) not found", elements.size() > 0);
			}
		});
		
		logger.trace("creating AssertProxyByGeosurf-Handler");
		dispatch.put(AssertProxyByGeosurfJob.class, new Handler() {
			
			private ObjectMapper mapper = new ObjectMapper();
			
			public void handle(WebDriver driver, IJob job) {
				AssertProxyByGeosurfJob typedJob = (AssertProxyByGeosurfJob) job;
				
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
