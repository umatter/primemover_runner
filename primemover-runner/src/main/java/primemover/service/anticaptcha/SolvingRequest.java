package primemover.service.anticaptcha;

public class SolvingRequest {
	
    private String clientKey;
    private Integer taskId;

	public SolvingRequest() {
	}

	public String getClientKey() {
		return clientKey;
	}

	public void setClientKey(String clientKey) {
		this.clientKey = clientKey;
	}

	public Integer getTaskId() {
		return taskId;
	}

	public void setTaskId(Integer taskId) {
		this.taskId = taskId;
	}

}
