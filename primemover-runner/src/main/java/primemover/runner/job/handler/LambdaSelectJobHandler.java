package primemover.runner.job.handler;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.ObjectMapper;

import primemover.lambda.LambdaCaller;
import primemover.lambda.LambdaCallerFactory;
import primemover.model.jobs.LambdaSelectJob;
import primemover.runner.context.IJobAwareRunnerContext;
import primemover.runner.job.IJobHandler;
import primemover.service.IStorageService;
import primemover.service.datawriter.DataWriterCachingDecorator;
import primemover.service.datawriter.IDataWriter;
import primemover.service.datawriter.LambdaResultWriter;

public class LambdaSelectJobHandler implements IJobHandler {
	private static Logger logger = LoggerFactory.getLogger(LambdaSelectJobHandler.class);

	@Autowired(required = false)
	List<IStorageService> storages = new ArrayList<IStorageService>();

	@Autowired
	private LambdaCallerFactory callerFac;

	private LambdaCaller caller = null;

	private ObjectMapper mapper = new ObjectMapper();

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

		try {

			WebDriver driver = context.getDriver();
			LambdaSelectJob typedJob = (LambdaSelectJob) context.getJob();

			// prepare input JSON
			LambdaSelectJobParam param = new LambdaSelectJobParam(typedJob, context.getCrawler(),
					driver.getPageSource());
			String result = this.caller.invoke(typedJob.getLinkValue(), mapper.writeValueAsString(param));

			LambdaSelectJobResult parsedResult = mapper.readValue(result, LambdaSelectJobResult.class);

			// write result
			LambdaResultWriter writer = new LambdaResultWriter(typedJob, result);
			IDataWriter cachedWriter = new DataWriterCachingDecorator(writer);
			for (IStorageService storage : storages) {
				storage.writeData(typedJob, cachedWriter);
			}

			// select xpath
			WebElement element = driver.findElement(By.xpath(parsedResult.getXpath()));
			// Wait for Element
			WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5));
			wait.until(ExpectedConditions.elementToBeClickable(element));
			element.click();

		} catch (Exception e) {
			logger.warn("error executing lambda", e);
			throw new RuntimeException("error executing lamgda", e);

		}
	}

}
