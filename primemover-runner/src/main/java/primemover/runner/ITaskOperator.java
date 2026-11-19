package primemover.runner;

import primemover.model.Crawler;
import primemover.model.Joblist;

public interface ITaskOperator {
	/**
	 * Initialize the TaskService
	 * 
	 * @param crawler to use
	 * @return true indicates success
	 */
	Boolean prepareExeuction(Crawler crawler);

	/**
	 * Why the last {@link #prepareExeuction} returned false, in one line, or null
	 * if it is not known. Goes into the queue's status message, so that a locked
	 * profile and a dead proxy do not read the same.
	 */
	default String lastSetupFailure() {
		return null;
	}

	/**
	 * Tears down the TaskService
	 * 
	 */
	void tearDown(boolean immediate);

	/**
	 * This will run a job
	 * 
	 * @param jobs consists of a joblist to run
	 * 
	 * @return true indicates success
	 */
	JobListOutcome runJobList(Joblist jobs);
}
