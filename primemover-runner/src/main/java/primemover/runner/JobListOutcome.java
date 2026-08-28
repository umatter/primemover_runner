package primemover.runner;

/**
 * How a joblist actually turned out, counted per job.
 *
 * <p>
 * This type exists because {@code runJobList} used to return a bare
 * {@code Boolean} that was set to {@code true} unconditionally after the job
 * loop, so a queue reported {@code success} no matter how many of its jobs had
 * died. Measured on ex63 2026-08-27: five 113-job runs each lost 12-22 jobs to
 * a crashed renderer and 12-22 more to the skip-after-failure rule, and all five
 * were recorded {@code status_code = success, status_message = "done"}. Anything
 * filtering on queue status alone ingested five truncated sessions as clean
 * ones.
 */
public final class JobListOutcome {

	private int succeeded;
	private int warned;
	private int failed;
	private int skipped;

	public void countSucceeded() {
		succeeded++;
	}

	public void countWarned() {
		warned++;
	}

	public void countFailed() {
		failed++;
	}

	public void countSkipped() {
		skipped++;
	}

	public int getSucceeded() {
		return succeeded;
	}

	public int getWarned() {
		return warned;
	}

	public int getFailed() {
		return failed;
	}

	public int getSkipped() {
		return skipped;
	}

	public int getTotal() {
		return succeeded + warned + failed + skipped;
	}

	/** True when every job that ran finished cleanly and none was skipped. */
	public boolean isClean() {
		return failed == 0 && warned == 0 && skipped == 0;
	}

	/**
	 * True when the run completed but lost work along the way. A run that lost
	 * jobs is not a success, and it is not a total failure either -- calling it
	 * either one is what hid five truncated sessions.
	 */
	public boolean isDegraded() {
		return !isClean() && succeeded > 0;
	}

	/** True when nothing at all came out of the run. */
	public boolean isTotalFailure() {
		return succeeded == 0 && (failed > 0 || skipped > 0);
	}

	/** Single-line summary, suitable for a queue's status_message. */
	public String describe() {
		return String.format("%d/%d jobs succeeded (%d warning, %d failed, %d skipped)", succeeded, getTotal(), warned,
				failed, skipped);
	}

	@Override
	public String toString() {
		return describe();
	}
}
