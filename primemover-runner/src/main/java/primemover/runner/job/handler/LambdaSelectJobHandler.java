package primemover.runner.job.handler;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import primemover.lambda.LambdaCaller;
import primemover.lambda.LambdaCallerFactory;
import primemover.model.jobs.LambdaSelectJob;
import primemover.runner.context.IJobAwareRunnerContext;
import primemover.runner.job.IJobHandler;
import primemover.service.IStorageService;
import primemover.service.datawriter.DataWriterCachingDecorator;
import primemover.service.datawriter.IDataWriter;
import primemover.service.datawriter.lambda.LambdaInvocationData;
import primemover.service.datawriter.lambda.LambdaInvocationWriter;
import primemover.lambda.LambdaInvokeResult;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class LambdaSelectJobHandler implements IJobHandler {
	private static Logger logger = LoggerFactory.getLogger(LambdaSelectJobHandler.class);

	@Autowired(required = false)
	private List<IStorageService> storages = new ArrayList<IStorageService>();

	private XPathFactory factory = XPathFactory.newInstance();

	@Autowired
	private LambdaCallerFactory callerFac;

	private LambdaCaller caller = null;

	private ObjectMapper mapper;

	public LambdaSelectJobHandler() {
		super();
		// Build mapper that allows NaN/Infinity values (Lambda may return these in numeric fields)
		mapper = JsonMapper.builder()
				.enable(JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS)
				.build();
		mapper.setVisibility(PropertyAccessor.FIELD, Visibility.ANY); // amazon invocation result needs higher
																		// visibility...
	}

	private void init() {
		logger.debug("initializing job");

		if (caller != null) {
			return;
		}

		if (callerFac == null) {
			throw new IllegalStateException("no LambdaCallerFactory available");
		}

		caller = callerFac.getInstance();

	}

	@Override
	public void handle(IJobAwareRunnerContext<?> context) {
		logger.debug("running job");

		init();

		if (caller == null) {
			throw new IllegalStateException("no LambdaCaller created");
		}
		// grab some data and type it
		WebDriver driver = context.getDriver();
		LambdaSelectJob typedJob = (LambdaSelectJob) context.getJob();

		// prepare recorder
		LambdaInvocationData data = new LambdaInvocationData();
		data.setJob(context.getJob());

		Exception ex = null;

		try {

			// --- Layer C: DOM Stabilization ---
			// Wait for YouTube recommendation elements to be present and stable before capturing page source
			try {
				new WebDriverWait(driver, Duration.ofSeconds(10))
					.until(ExpectedConditions.presenceOfElementLocated(
						By.cssSelector("#related yt-lockup-view-model, #secondary yt-lockup-view-model")));
				Thread.sleep(1000); // extra settle time after elements appear
				logger.debug("Recommendation container stabilized");
			} catch (Exception stabilizeEx) {
				logger.warn("Recommendation container not found during stabilization wait, proceeding anyway");
			}

			// prepare input JSON
			LambdaSelectJobParam param = new LambdaSelectJobParam(typedJob, context.getCrawler(),
					driver.getPageSource(), context.getCrawler().getRawConfiguration());

			data.setInputJson(mapper.writeValueAsString(param));

			LambdaInvokeResult result = this.caller.invoke(typedJob.getLinkValue(), data.getInputJson());
			data.setOutputJson(mapper.writeValueAsString(result));

			if (!StringUtils.isEmpty(result.getFunctionError())) {
				// Enhanced error logging with more details
				logger.error("Lambda function '{}' failed with error type: {}",
							typedJob.getLinkValue(), result.getFunctionError());
				logger.error("Lambda status code: {}", result.getStatusCode());

				// Try to parse error details from payload
				String errorPayload = result.getPayload();
				logger.error("Lambda error response payload: {}", errorPayload);

				// Try to extract structured error information
				try {
					// Attempt to parse as JSON to get structured error info
					Object errorObj = mapper.readValue(errorPayload, Object.class);
					logger.error("Parsed Lambda error details: {}", mapper.writeValueAsString(errorObj));
				} catch (Exception parseEx) {
					logger.debug("Could not parse Lambda error payload as JSON: {}", parseEx.getMessage());
				}

				throw new RuntimeException("Lambda Error (functionError): " + result.getFunctionError() +
										 " - Status: " + result.getStatusCode() +
										 " - Details: " + errorPayload);
			}

			if (!(result.getStatusCode() >= 200 && result.getStatusCode() < 300)) {
				logger.error("AWS responded with status code {}", result.getStatusCode());
				throw new RuntimeException("Lambda Error (status code): " + result.getStatusCode());
			}

			// Log the full Lambda response payload BEFORE attempting deserialization
			String lambdaResponsePayload = result.getPayload();
			logger.info("Full Lambda response payload: {}", lambdaResponsePayload);

			LambdaSelectJobResult parsedResult = mapper.readValue(lambdaResponsePayload,
					LambdaSelectJobResult.class);

			// Validate XPath
			XPath xpath = factory.newXPath();
			XPathExpression expr = xpath.compile(parsedResult.getXpath());

			// --- Layer A: Retry with re-invocation ---
			// Try up to 3 times to find and click the element, re-invoking lambda on each retry
			int maxAttempts = 3;
			boolean clicked = false;
			NoSuchElementException lastNSEE = null;

			for (int attempt = 1; attempt <= maxAttempts; attempt++) {
				try {
					WebElement element = driver.findElement(By.xpath(parsedResult.getXpath()));

					// Scroll element into view with offset to avoid sticky headers
					JavascriptExecutor js = (JavascriptExecutor) driver;
					js.executeScript("arguments[0].scrollIntoView({block: 'center', behavior: 'instant'});", element);

					// Small pause to allow scroll to complete and any overlays to settle
					Thread.sleep(300);

					// Wait for element to be clickable
					WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5));
					wait.until(ExpectedConditions.elementToBeClickable(element));

					// Use JavaScript click to bypass overlapping elements like sticky headers
					js.executeScript("arguments[0].click();", element);

					clicked = true;
					if (attempt > 1) {
						logger.info("Element found and clicked on attempt {}/{}", attempt, maxAttempts);
					}
					break;
				} catch (NoSuchElementException nsee) {
					lastNSEE = nsee;
					if (attempt < maxAttempts) {
						logger.warn("Element not found on attempt {}/{}, re-capturing page and re-invoking lambda",
								attempt, maxAttempts);
						Thread.sleep(2000); // wait for DOM to settle

						// Re-capture page source and re-invoke lambda
						param = new LambdaSelectJobParam(typedJob, context.getCrawler(),
								driver.getPageSource(), context.getCrawler().getRawConfiguration());
						data.setInputJson(mapper.writeValueAsString(param));
						result = this.caller.invoke(typedJob.getLinkValue(), data.getInputJson());
						data.setOutputJson(mapper.writeValueAsString(result));

						// Re-parse result
						lambdaResponsePayload = result.getPayload();
						logger.info("Retry Lambda response payload: {}", lambdaResponsePayload);
						parsedResult = mapper.readValue(lambdaResponsePayload, LambdaSelectJobResult.class);

						// Validate new XPath
						expr = xpath.compile(parsedResult.getXpath());
					}
				}
			}

			// --- Layer B: Direct URL navigation fallback ---
			if (!clicked) {
				if (parsedResult.getSelected() != null && parsedResult.getSelected().getId() != null) {
					String videoId = parsedResult.getSelected().getId();
					String videoUrl = "https://www.youtube.com/watch?v=" + videoId;
					logger.warn("Element not found after {} attempts, falling back to direct navigation: {}",
							maxAttempts, videoUrl);
					driver.get(videoUrl);
					// Wait for page load
					Thread.sleep(3000);
				} else {
					logger.error("All {} click attempts failed and no selected video ID available for fallback",
							maxAttempts);
					throw lastNSEE;
				}
			}

		} catch (Exception e) {
			logger.warn("error executing lambda", e);
			ex = e;
		}

		// write result
		LambdaInvocationWriter writer = new LambdaInvocationWriter(data);
		IDataWriter cachedWriter = new DataWriterCachingDecorator(writer);
		for (IStorageService storage : storages) {
			storage.writeData(typedJob, cachedWriter);
		}

		if (ex != null) {
			throw new RuntimeException("error executing lambda job", ex);
		}
	}

}
