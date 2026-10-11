package primemover.runner.job.handler;

import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import primemover.model.jobs.ClearStorageJob;
import primemover.model.jobs.ClearStorageScope;
import primemover.runner.context.IJobAwareRunnerContext;
import primemover.runner.job.IJobHandler;
import primemover.util.WebStorage;

/**
 * Clear the browsing data a site has stored about this bot: its cookies and its web storage.
 *
 * <p>
 * Everything here is standard WebDriver, deliberately. The Chrome DevTools Protocol would
 * clear cookies browser-wide in one call, but Multilogin's driver does not implement it --
 * the runner's own H.264 override has been falling back to script injection since it was
 * written, with {@code unknown command: .../ms/cdp/execute} in the log. A privacy arm built
 * on CDP would fail on every bot, every session.
 *
 * <p>
 * The consequence is that this acts on the <em>current origin</em>:
 * {@code deleteAllCookies} removes the cookies of the current document's domain, not the
 * whole browser, and {@code window.localStorage} is reachable only from a loaded page. So
 * the job has to run on a page of the origin being cleared, and the workflow is responsible
 * for navigating there first. That is why the design emits a visit before each clear, for
 * every arm alike: the arms then differ in what the job clears, never in where the bot went.
 *
 * <p>
 * {@link ClearStorageScope#NONE} is a deliberate no-op, so the control arm runs the same
 * workflow, with the same visits and the same job count, as the arm whose data is cleared.
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
			logger.debug("clear_storage: scope none, leaving this origin's data untouched");
			return;
		case LOCAL:
			logger.debug("clear_storage: localStorage, removed {}", WebStorage.clearLocal(driver));
			return;
		case SESSION:
			logger.debug("clear_storage: sessionStorage, removed {}", WebStorage.clearSession(driver));
			return;
		case ALL:
			int cookies = driver.manage().getCookies().size();
			driver.manage().deleteAllCookies();
			long local = WebStorage.clearLocal(driver);
			long session = WebStorage.clearSession(driver);
			// The counts are logged because "cleared" with no number cannot be told apart
			// from a job that ran against a page with nothing on it -- which is exactly how
			// the first version of this handler looked correct while clearing about:blank.
			logger.debug("clear_storage: {} cookies, localStorage={} sessionStorage={} at {}",
					cookies, local, session, driver.getCurrentUrl());
			return;
		default:
			throw new IllegalStateException("unhandled clear_storage scope " + scope);
		}
	}
}
