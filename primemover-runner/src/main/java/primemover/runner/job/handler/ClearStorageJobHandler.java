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
 * Clear web storage for the current origin.
 *
 * <p>
 * A {@link ClearStorageScope#NONE} job is a deliberate no-op: it lets an experiment's
 * control arm run the same workflow, with the same job count and timing, as the arm whose
 * storage is cleared. It still logs, so the record shows the job ran and chose to clear
 * nothing rather than being skipped.
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
		if (scope == ClearStorageScope.NONE) {
			logger.debug("clear_storage: scope none, leaving storage untouched");
			return;
		}

		long local = -1;
		long session = -1;
		if (scope == ClearStorageScope.LOCAL || scope == ClearStorageScope.ALL) {
			local = WebStorage.clearLocal(driver);
		}
		if (scope == ClearStorageScope.SESSION || scope == ClearStorageScope.ALL) {
			session = WebStorage.clearSession(driver);
		}
		logger.debug("clear_storage: scope {}, removed localStorage={} sessionStorage={} (-1 = store unreachable)",
				scope, local, session);
	}
}
