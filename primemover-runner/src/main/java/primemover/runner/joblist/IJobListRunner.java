package primemover.runner.joblist;

import java.util.function.Supplier;

/**
 * Marker interface for running joblists
 * 
 * @author domin
 *
 */
public interface IJobListRunner extends Supplier<Boolean> {

	long getJobListId();

	/**
	 * Cuts this joblist off: interrupts its worker, ends the browser and stops its
	 * own state reports, so the thread returns and its executor slot is free for
	 * the next queue. May block while the browser is torn down, so call it from a
	 * thread that can afford that. Safe to call more than once, or after the run
	 * has finished.
	 */
	default void abort() {
	}
}
