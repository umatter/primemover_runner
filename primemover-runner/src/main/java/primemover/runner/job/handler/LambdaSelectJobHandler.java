package primemover.runner.job.handler;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.By;
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
import software.amazon.awssdk.services.lambda.model.InvokeResponse;

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

	private ObjectMapper mapper = new ObjectMapper();

	public LambdaSelectJobHandler() {
		super();
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

			// prepare input JSON
			LambdaSelectJobParam param = new LambdaSelectJobParam(typedJob, context.getCrawler(),
					driver.getPageSource());

			data.setInputJson(mapper.writeValueAsString(param));

			InvokeResponse result = this.caller.invoke(typedJob.getLinkValue(), data.getInputJson());
			data.setOutputJson(mapper.writeValueAsString(result));

			if (!StringUtils.isEmpty(result.functionError())) {
				// Enhanced error logging with more details
				logger.error("Lambda function '{}' failed with error type: {}", 
							typedJob.getLinkValue(), result.functionError());
				logger.error("Lambda status code: {}", result.statusCode());
				
				// Try to parse error details from payload
				String errorPayload = result.payload().asUtf8String();
				logger.error("Lambda error response payload: {}", errorPayload);
				
				// Try to extract structured error information
				try {
					// Attempt to parse as JSON to get structured error info
					Object errorObj = mapper.readValue(errorPayload, Object.class);
					logger.error("Parsed Lambda error details: {}", mapper.writeValueAsString(errorObj));
				} catch (Exception parseEx) {
					logger.debug("Could not parse Lambda error payload as JSON: {}", parseEx.getMessage());
				}
				
				throw new RuntimeException("Lambda Error (functionError): " + result.functionError() + 
										 " - Status: " + result.statusCode() + 
										 " - Details: " + errorPayload);
			}

			if (!(result.statusCode() >= 200 && result.statusCode() < 300)) {
				logger.error("AWS responded with status code {}", result.statusCode());
				throw new RuntimeException("Lambda Error (status code): " + result.statusCode());
			}

			LambdaSelectJobResult parsedResult = mapper.readValue(result.payload().asUtf8String(),
					LambdaSelectJobResult.class);

			// Validate XPath
			XPath xpath = factory.newXPath();
			XPathExpression expr = xpath.compile(parsedResult.getXpath());

			// select xpath
			WebElement element = driver.findElement(By.xpath(parsedResult.getXpath())); // NoSuchElementException
			// Wait for Element
			WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(2));
			wait.until(ExpectedConditions.elementToBeClickable(element));
			element.click();

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
