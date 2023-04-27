package primemover.runner.job.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import primemover.model.geosurf.IpInfo;
import primemover.model.jobs.AssertProxyByGeosurfJob;
import primemover.runner.context.IJobAwareRunnerContext;
import primemover.runner.job.IJobHandler;

public class AssertProxyByPrimemoverApiJobHandler implements IJobHandler {

	private static Logger logger = LoggerFactory.getLogger(AssertProxyByPrimemoverApiJobHandler.class);

	private ObjectMapper mapper = new ObjectMapper();

	// TODO effectively change to primemover API. Below is a quick hack to get
	// running due to timeconstraint.
	@Override
	public void handle(IJobAwareRunnerContext<?> context) {
		logger.debug("running job");

		WebDriver driver = context.getDriver();
		AssertProxyByGeosurfJob typedJob = (AssertProxyByGeosurfJob) context.getJob();

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
}
