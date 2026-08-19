package primemover.runner.job.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import primemover.model.ipcheck.OxylabsIpCheck;
import primemover.model.jobs.AssertProxyByOxylabsJob;
import primemover.runner.context.IJobAwareRunnerContext;
import primemover.runner.job.IJobHandler;

import java.util.List;
import java.util.Locale;

/**
 * Verifies the exit IP's geolocation against {@code ip.oxylabs.io/location}.
 *
 * <p>
 * The endpoint aggregates four geolocation databases that routinely disagree, so
 * country and city are judged differently:
 *
 * <ul>
 * <li><b>country</b> -- strict. Every provider that reports a country must agree
 * with the expectation, and at least one must report one. A country mismatch
 * means the crawler is in the wrong place and its data is not usable.</li>
 * <li><b>city</b> -- best-effort. It passes when <em>any</em> provider agrees.
 * Oxylabs targets from MaxMind, which frequently returns an empty city for an IP
 * other providers do place, so demanding unanimity would fail runs whose
 * targeting actually worked. When no provider reports any city at all the check
 * is logged and skipped rather than failed -- absence of evidence is not
 * evidence of the wrong city.</li>
 * </ul>
 */
public class AssertProxyByOxylabsJobHandler implements IJobHandler {

	private static final Logger logger = LoggerFactory.getLogger(AssertProxyByOxylabsJobHandler.class);

	private static final String CHECK_URL = "https://ip.oxylabs.io/location";

	private final ObjectMapper mapper = new ObjectMapper();

	@Override
	public void handle(IJobAwareRunnerContext<?> context) {
		logger.debug("running job");

		WebDriver driver = context.getDriver();
		AssertProxyByOxylabsJob typedJob = (AssertProxyByOxylabsJob) context.getJob();

		driver.get(CHECK_URL);

		// Chrome renders application/json inside a <pre> tag.
		String json = driver.findElement(By.tagName("pre")).getText();

		OxylabsIpCheck check;
		try {
			check = mapper.readValue(json, OxylabsIpCheck.class);
		} catch (JsonProcessingException e) {
			throw new AssertionError("Could not parse the response of " + CHECK_URL + ": " + json, e);
		}

		if (StringUtils.hasText(typedJob.getIp())) {
			assertIp(typedJob.getIp(), check);
		}
		if (StringUtils.hasText(typedJob.getCountry())) {
			assertCountry(typedJob.getCountry(), check);
		}
		if (StringUtils.hasText(typedJob.getCity())) {
			assertCity(typedJob.getCity(), check);
		}
	}

	private static void assertIp(String expected, OxylabsIpCheck check) {
		if (!expected.equals(check.getIp())) {
			throw new AssertionError("exit IP is " + check.getIp() + ", expected " + expected);
		}
	}

	private static void assertCountry(String expected, OxylabsIpCheck check) {
		String want = expected.trim().toUpperCase(Locale.ROOT);
		List<String> reported = check.reportedCountries();

		if (reported.isEmpty()) {
			throw new AssertionError("no geolocation provider reported a country for exit IP " + check.getIp()
					+ ", so the expected country " + want + " could not be verified");
		}
		for (String actual : reported) {
			if (!actual.equals(want)) {
				throw new AssertionError("exit IP " + check.getIp() + " is in " + reported + ", expected " + want);
			}
		}
		logger.debug("exit IP {} confirmed in {} by {} provider(s)", check.getIp(), want, reported.size());
	}

	private static void assertCity(String expected, OxylabsIpCheck check) {
		String want = OxylabsIpCheck.normaliseCity(expected);
		List<String> reported = check.reportedCities();

		if (reported.isEmpty()) {
			// Oxylabs targets from MaxMind, which often reports no city at all. Failing
			// here would reject runs that were in fact targeted correctly.
			logger.warn("no geolocation provider reported a city for exit IP {}; skipping the city check"
					+ " against '{}'", check.getIp(), want);
			return;
		}
		if (!reported.contains(want)) {
			throw new AssertionError("exit IP " + check.getIp() + " is in city " + reported + ", expected " + want);
		}
		logger.debug("exit IP {} confirmed in city {} by {}", check.getIp(), want, check.providersReporting(want));
	}
}
