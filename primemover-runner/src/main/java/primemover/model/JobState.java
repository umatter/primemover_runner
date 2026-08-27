package primemover.model;

public enum JobState {
	RUNNING("running", "job started"),
	STORINGDATA("running", "storing data"),
	FAILURE("failure", "error during execution"),
	/**
	 * The job handler ran to completion but something the experiment cares about
	 * degraded -- e.g. the decision function fell back from LLM-driven to random
	 * video selection. Reported as {@code warning}, not {@code success}: an arm
	 * that silently became a control arm must not render as a green pill.
	 */
	WARNING("warning", "minor error encountered"),
	/**
	 * The job ran, but its recorded data (HAR, page source, screenshot, ...) could
	 * not be written to at least one storage backend. Reported as {@code warning}
	 * for the same reason: the job "succeeded" with no data to show for it.
	 */
	WARNING_JOBDATA("warning", "problems writing jobresult"),
	SUCCESS("success", "successfully finished job"),
	SKIPPED("skipped", "previous task was not executed");

	private String code;
	private String text;
	
	private JobState(String code, String text) {
		this.code = code;
		this.text = text;
	}

	public String getCode() {
		return code;
	}

	public String getText() {
		return text;
	}
}
