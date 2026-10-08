package primemover.runner.job.handler;

import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import primemover.model.jobs.ClearStorageJob;
import primemover.model.jobs.ClearStorageScope;
import primemover.runner.context.IJobAwareRunnerContext;
import primemover.runner.job.IJobHandler;
import primemover.util.BrowsingData;
import primemover.util.WebStorage;

/**
 * Clear browsing data.
 *
 * <p>
 * {@link ClearStorageScope#ALL} is the privacy-conscious user: all cookies and the cache,
 * browser-wide, plus every storage bucket of each named origin. It goes through CDP rather
 * than JavaScript because this job runs at the head of a session, before any page has
 * loaded -- a {@code window.localStorage} call there would address {@code about:blank} and
 * clear nothing at all, which is the trap this scope exists to avoid.
 *
 * <p>
 * {@link ClearStorageScope#NONE} is a deliberate no-op, so an experiment's control arm can
 * run the same workflow, with the same job count and timing, as the arm whose data is
 * cleared. It still logs, so the record shows the job ran and chose to clear nothing.
 */
public class ClearStorageJobHandler implements IJobHandler {

	private static final Logger logger = LoggerFactory.getLogger(ClearStorageJobHandler.class);

	@Override
	public void handle(IJobAwareRunnerContext<?> context) {
		WebDriver driver = context.getDriver();
		ClearStorageJob job = (ClearStorageJob) context.getJob();
		ClearStorageScope scope = job.getScope();

		if (scope == null) {
			throw new IllegalStateException("clear_storage job " + job.getId() + " has no scope");
		}

		switch (scope) {
		case NONE:
			logger.debug("clear_storage: scope none, leaving browsing data untouched");
			return;
		case LOCAL:
			logger.debug("clear_storage: localStorage, removed {}", WebStorage.clearLocal(driver));
			return;
		case SESSION:
			logger.debug("clear_storage: sessionStorage, removed {}", WebStorage.clearSession(driver));
			return;
		case ALL:
			BrowsingData.clearCookiesAndCache(driver);
			for (String origin : job.getOrigins()) {
				BrowsingData.clearSiteData(driver, origin);
			}
			logger.debug("clear_storage: cookies and cache cleared, site data cleared for {}",
					job.getOrigins());
			return;
		default:
			throw new IllegalStateException("unhandled clear_storage scope " + scope);
		}
	}
}
