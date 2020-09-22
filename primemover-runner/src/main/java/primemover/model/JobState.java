package primemover.model;

public enum JobState {
	RUNNING("running", "job started"),
	STORINGDATA("running", "storing data"),
	FAILURE("failure", "error during execution"),
	WARNING("success", "minor error encountered"),
	WARNING_JOBDATA("success", "problems writing jobresult"),
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
