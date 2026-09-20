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
	 *
	 * <p>
	 * Ending on a <b>challenge page</b> is the same kind of degradation and is
	 * reported here too: when {@code ChallengePageDetector} recognises a Google
	 * sorry page or a Cloudflare interstitial and no captcha was solved, the
	 * session saw "prove you are not a robot" instead of the content the
	 * experiment asked for. Note this is distinct from the {@code captcha_found}
	 * flag, which is a naive substring match and fires on ordinary pages carrying
	 * a captcha widget; only a recognised challenge PAGE warns.
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
