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
	Boolean runJobList(Joblist jobs);
}
