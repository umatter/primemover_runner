package primemover.model;

public enum JoblistState {
	SKIPPED_TIMEDOUT("skipped", "timed out"), SKIPPED_INACTIVE("skipped", "job inactive"),
	SKIPPED_REVIEWED("skipped", "job already reviewed"), RUNNING_INPROGRESS("running", "in progress"),
	RUNNING_INITDONE("running", "initialisation done"), DONE_INITFAILED("failure", "initialisation failed"),
	DONE_JOBLIST_EMPTY("failure", "joblist empty"), RUNNING_JOBSDONE("running", "jobs done"),
	RUNNING_INITFAILED("running", "init failed"), RUNNING_JOBSFAILURE("running", "failure running jobs"),
	DONE_SUCCESS("success", "done"), DONE_JOBSFAILURE("failure", "failure running jobs"),
	DONE_FAILURE("failure", "stoped with fatal failure"), DONE_PROCESSING_TIMEOUT("failure", "processing timed out");

	private String code;
	private String text;

	private JoblistState(String code, String text) {
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
