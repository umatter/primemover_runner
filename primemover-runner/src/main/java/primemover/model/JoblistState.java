package primemover.model;

public enum JoblistState {
	SKIPPED_TIMEDOUT("skipped", "timed out"),
	SKIPPED_INACTIVE("skpped", "job inactive"),
	SKIPPED_REVIEWED("skpped", "job already reviewed"),
	RUNNING_INPROGRESS("running", "in progress"),
	RUNNING_INITDONE("running", "initialisation done"),
	DONE_INITFAILED("failure", "initialisation failed"),
	RUNNING_JOBSDONE("running", "jobs done"),
	RUNNING_INITFAILED("running", "init failed"),
	RUNNING_JOBSFAILURE("running", "failure running jobs"),
	DONE_SUCCESS("success", "done"),
	DONE_JOBSFAILURE("failure", "failure running jobs"),
	DONE_FAILURE("failure", "stoped with fatal failure");

	
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
