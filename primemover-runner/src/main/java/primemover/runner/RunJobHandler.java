package primemover.runner;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.openqa.selenium.By;
import org.openqa.selenium.ElementNotInteractableException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import primemover.captcha.CaptchaJobHelper;
import primemover.captcha.CaptchaJobHelper.JobPosition;
import primemover.captcha.CaptchaSolver;
import primemover.config.StartupMode;
import primemover.model.Crawler;
import primemover.model.IJob;
import primemover.model.SelectorType;
import primemover.model.TypingModeType;
import primemover.model.geosurf.IpInfo;
import primemover.model.jobs.AssertElementExistsJob;
import primemover.model.jobs.AssertPageLoadedJob;
import primemover.model.jobs.AssertProxyByGeosurfJob;
import primemover.model.jobs.AssertProxyByPrimemoverApiJob;
import primemover.model.jobs.AssertScrolledMaxJob;
import primemover.model.jobs.AssertTextInFieldJob;
import primemover.model.jobs.ClickJob;
import primemover.model.jobs.EnterTextFieldJob;
import primemover.model.jobs.ScrollByDurationJob;
import primemover.model.jobs.ScrollByLengthJob;
import primemover.model.jobs.SingleSelectUrlJob;
import primemover.model.jobs.SwitchToJob;
import primemover.model.jobs.TryClickJob;
import primemover.model.jobs.TrySwitchToJob;
import primemover.model.jobs.VisitJob;
import primemover.model.jobs.WaitJob;
import primemover.util.Page;
import primemover.util.Scrolling;
import primemover.util.SelectingHelper;
import primemover.util.Typing;
import primemover.util.Waiter;

public class RunJobHandler {

	private static Logger logger = LoggerFactory.getLogger(RunJobHandler.class);
	
	@Autowired
	private SelectingHelper selectingHelper;
	
	@Autowired
	private CaptchaSolver captchaSolver;
	
	@Value("${global.mode}")
	private StartupMode mode;
			
	// Interface for our IJob-Handlers
	private interface Handler {
		void handle(OperatorContext context, IJob job);
	}

	// Handler registration
	@SuppressWarnings("rawtypes")
	private final Map<Class, Handler> dispatch = new HashMap<Class, Handler>();

	// Populate handlers
	public RunJobHandler() {
		logger.trace("populating handlers");
		logger.trace("creating VisitJob-Handler");
		dispatch.put(VisitJob.class, new Handler() {
			public void handle(OperatorContext context, IJob job) {
				WebDriver driver = context.getDriver();
				VisitJob typedJob = (VisitJob) job;
				
				// captcha before
				CaptchaJobHelper.solveCaptcha(context, typedJob, JobPosition.BEFORE, captchaSolver);
				
				logger.debug("navigate to {} for job {}", typedJob.getUrl(), typedJob.getId());
				driver.navigate().to(typedJob.getUrl());
				
				// wait with retry until loaded
				primemover.util.Waiter.waitUntilJSReady(context.getDriver(), 10, 12); // check every 10 seconds for 2 Minutes
				
				// captcha after
				CaptchaJobHelper.solveCaptcha(context, typedJob, JobPosition.AFTER, captchaSolver);
			}
		});

		logger.trace("creating WaitJob-Handler");
		dispatch.put(WaitJob.class, new Handler() {
			public void handle(OperatorContext context, IJob job) {
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
			public void handle(OperatorContext context, IJob job) {
				WebDriver driver = context.getDriver();
				
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
			public void handle(OperatorContext context, IJob job) {
				WebDriver driver = context.getDriver();
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
			public void handle(OperatorContext context, IJob job) {
				WebDriver driver = context.getDriver();
				EnterTextFieldJob typedJob = (EnterTextFieldJob) job;

				boolean done = false;
				int retriesCount = 0;
				
				logger.debug("typing text ({}) to field ({} : {})", typedJob.getValue(), typedJob.getType(), typedJob.getSelector());
				
				while (!done && retriesCount < 5) {
					try {
						retriesCount++;
						logger.debug("typing text. Trial {} of {}", retriesCount, 5);
						SelectorType selectorType = typedJob.getType();
						String selector = typedJob.getSelector();
						
						// captcha before
						CaptchaJobHelper.solveCaptcha(context, typedJob, JobPosition.BEFORE, captchaSolver);
						
						// provide WebElement dynamic for below functions. Google search will stale the element with the search suggestions
						Supplier<WebElement> elementSup = () -> selectingHelper.getElementByWebdriver(driver, selectorType,
								selector);
						
						TypingModeType typingMode = typedJob.getTypingMode();
						
						// typing text
						switch (typingMode) {
						case DIRECT:
							Typing.typeTextDirect(elementSup, typedJob.getValue(), typedJob.getAppendReturn());
							break;
						case SIMULATED_NOTYPOS:
							Typing.typeTextSimulated(elementSup, typedJob.getValue(), typedJob.getAppendReturn(), false, false);
							break;
						case SIMULATED_KEEPINGTYPOS:
							Typing.typeTextSimulated(elementSup, typedJob.getValue(), typedJob.getAppendReturn(), true, true);
							break;
						case SIMULATED_FIXINGTYPOS:
							Typing.typeTextSimulated(elementSup, typedJob.getValue(), typedJob.getAppendReturn(), true, false);
							break;
						default:
							throw new UnsupportedOperationException("Not implemented yet (" + typingMode + ")");
						}
						logger.debug("done typing text");
						
						// captcha after
						CaptchaJobHelper.solveCaptcha(context, typedJob, JobPosition.AFTER, captchaSolver);
						
						done = true;
					} catch (StaleElementReferenceException e) { 
						done = false; // retry
						logger.debug("retrying typing text in {} seconds", 3);
						
						// just wait
						try {
							Thread.sleep(3000);
						} catch (InterruptedException e1) {
							// nothing to do
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
		});

		logger.trace("creating SingleSelectUrlJob-Handler");
		dispatch.put(SingleSelectUrlJob.class, new Handler() {
			public void handle(OperatorContext context, IJob job) {
				WebDriver driver = context.getDriver();
				Crawler crawler = context.getCrawler();
				SingleSelectUrlJob typedJob = (SingleSelectUrlJob) job;

				List<WebElement> elements = selectingHelper.getElementsByWebdriver(driver, typedJob.getType(),
						typedJob.getSelector());
				
				if (elements == null || elements.isEmpty()) {
					logger.warn("no element found");
					
					throw new IllegalStateException("no elements found");
				}
				
				WebElement selectedElement = selectingHelper.chooseElement(crawler, job, elements, typedJob.getDecisionType());

				if (selectedElement != null) {
					try {
						logger.debug("clicking element {}", selectedElement);
						Actions actions = new Actions(driver);
						actions.moveToElement(selectedElement);
						actions.perform();
						
						selectedElement.click();
					} catch (ElementNotInteractableException e) {
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
		});
		
		logger.trace("creating TryClickJob-Handler");
		dispatch.put(TryClickJob.class, new Handler() {
			public void handle(OperatorContext context, IJob job) {
				WebDriver driver = context.getDriver();
				TryClickJob typedJob = (TryClickJob) job;

				List<WebElement> elements = selectingHelper.getElementsByWebdriver(driver, typedJob.getType(),
						typedJob.getSelector());
				
				if (elements.size() == 1) {

					logger.debug("element found in TryClickJob {}", job.getId());
					WebElement selectedElement = elements.get(0);
					
					try {
						Actions actions = new Actions(driver);
						actions.moveToElement(selectedElement);
						actions.click(selectedElement);
						actions.perform();
					} catch (Exception e) {
						logger.error("Fatal", e);
					}
				} else {
					logger.debug("no element found in TryClickJob {}", job.getId());
				}
				
				try {
					Thread.sleep(500);
				} catch (InterruptedException e) {
					// nothing to do
				}
			}
		});
		
		logger.trace("creating TrySwitchToJob-Handler");
		dispatch.put(TrySwitchToJob.class, new Handler() {
			public void handle(OperatorContext context, IJob job) {
				WebDriver driver = context.getDriver();
				TrySwitchToJob typedJob = (TrySwitchToJob) job;

				List<WebElement> elements = selectingHelper.getElementsByWebdriver(driver, typedJob.getType(),
						typedJob.getSelector());
				
				if (elements.size() == 1) {

					logger.debug("element found in TryClickJob {}", job.getId());
					WebElement selectedElement = elements.get(0);
					
					try {

						driver.switchTo().frame(selectedElement);
					} catch (Exception e) {
						logger.warn("Error in job {}", job.getId(), e);
					}
				} else {
					logger.debug("no element found in TryClickJob {}", job.getId());
				}
				
				try {
					Thread.sleep(500);
				} catch (InterruptedException e) {
					// nothing to do
				}
			}
		});
		
		logger.trace("creating SwitchToJob-Handler");
		dispatch.put(SwitchToJob.class, new Handler() {
			public void handle(OperatorContext context, IJob job) {
				WebDriver driver = context.getDriver();
				SwitchToJob typedJob = (SwitchToJob) job;

				List<WebElement> elements = selectingHelper.getElementsByWebdriver(driver, typedJob.getType(),
						typedJob.getSelector());
				
				if (elements.size() == 1) {
					logger.debug("Switching to element for job {}", typedJob.getId());
					WebElement selectedElement = elements.get(0);
					driver.switchTo().frame(selectedElement);
				} else {
					logger.warn("Found more than one element for job {}", typedJob.getId());
					throw new IllegalStateException("More than one element found for job " + typedJob.getId());
				}
				
				try {
					Thread.sleep(500);
				} catch (InterruptedException e) {
					// nothing to do
				}
			}
		});
		
		logger.trace("creating TryClickJob-Handler");
		dispatch.put(ClickJob.class, new Handler() {
			public void handle(OperatorContext context, IJob job) {
				WebDriver driver = context.getDriver();
				ClickJob typedJob = (ClickJob) job;

				List<WebElement> elements = selectingHelper.getElementsByWebdriver(driver, typedJob.getType(),
						typedJob.getSelector());
				
				if (elements.size() == 1) {
					logger.debug("Clicking on element for job {}", typedJob.getId());
					WebElement selectedElement = elements.get(0);
					
					selectedElement.click();
				} else {
					logger.warn("Found more than one element for job {}", typedJob.getId());
					throw new IllegalStateException("More than one element found for job " + typedJob.getId());
				}
				
				try {
					Thread.sleep(500);
				} catch (InterruptedException e) {
					// nothing to do
				}
			}
		});
		
		logger.trace("creating AssertTextInFieldJob-Handler");
		dispatch.put(AssertTextInFieldJob.class, new Handler() {
			public void handle(OperatorContext context, IJob job) {
				WebDriver driver = context.getDriver();
				AssertTextInFieldJob typedJob = (AssertTextInFieldJob) job;

				WebElement element = selectingHelper.getElementByWebdriver(driver, typedJob.getType(),
						typedJob.getSelector());
				
				String textFound = element.getAttribute("innerText");
				assertEquals(textFound, typedJob.getValue());	
			}
		});
		
		// add assert-jobs only for develop and integration
		if (!(mode == StartupMode.DEVELOP || mode == StartupMode.INTEGRATION)) {
			return;
		}
		
		logger.trace("creating AssertPageLoadedJob-Handler");
		dispatch.put(AssertPageLoadedJob.class, new Handler() {
			public void handle(OperatorContext context, IJob job) {
				WebDriver driver = context.getDriver();
				// AssertPageLoadedJob typedJob = (AssertPageLoadedJob) job;

				Boolean isReady = Page.isPageReady(driver);
				assertTrue(isReady, "page is not ready");
			}
		});
		
		logger.trace("creating AssertScrolledMaxJob-Handler");
		dispatch.put(AssertScrolledMaxJob.class, new Handler() {
			public void handle(OperatorContext context, IJob job) {
				WebDriver driver = context.getDriver();
				AssertScrolledMaxJob typedJob = (AssertScrolledMaxJob) job;
				Boolean res;
				
				switch (typedJob.getDirection()) {
				case DOWN:
					res = Scrolling.isScrolledToBootom(driver);
					assertTrue(res, "page is not scrolled to bottom");
					break;
				case UP:
					res = Scrolling.isScrolledToTop(driver);
					assertTrue(res, "page is not scrolled to Top");
					break;
				default:
					throw new UnsupportedOperationException("Not implemented yet");
				}
			}
		});
		
		logger.trace("creating AssertElementExistsJob-Handler");
		dispatch.put(AssertElementExistsJob.class, new Handler() {
			public void handle(OperatorContext context, IJob job) {
				WebDriver driver = context.getDriver();
				AssertElementExistsJob typedJob = (AssertElementExistsJob) job;
				
				List<WebElement> elements = selectingHelper.getElementsByWebdriver(driver, typedJob.getType(),
						typedJob.getSelector());
				
				assertTrue(elements.size() > 0, "element(s) not found");
			}
		});
		
		logger.trace("creating AssertProxyByGeosurf-Handler");
		dispatch.put(AssertProxyByGeosurfJob.class, new Handler() {
			
			private ObjectMapper mapper = new ObjectMapper();
			
			public void handle(OperatorContext context, IJob job) {
				WebDriver driver = context.getDriver();
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
		
		logger.trace("creating AssertProxyByPrimemoverApi-Handler");
		dispatch.put(AssertProxyByPrimemoverApiJob.class, new Handler() {
			
			private ObjectMapper mapper = new ObjectMapper();
			
			public void handle(OperatorContext context, IJob job) {
				WebDriver driver = context.getDriver();
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
	public void handle(OperatorContext context, IJob job) {
		Handler h = dispatch.get(job.getClass());
		if (h == null) {
			throw new UnsupportedOperationException("This type is not yet supported");
		}
		h.handle(context, job); // <<== Here is the magic
	}
}
