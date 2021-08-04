package primemover.service.anticaptcha;

public class CreateTaskResponse {

	private Integer errorId = 0;
	private String errorCode;
	private String errorDescription;
	private Integer taskId;
	
	public CreateTaskResponse() {
	}
	
	public Boolean hasError() {
		return errorId != 0;
	}

	public Integer getErrorId() {
		return errorId;
	}

	public void setErrorId(Integer errorId) {
		this.errorId = errorId;
	}

	public String getErrorCode() {
		return errorCode;
	}

	public void setErrorCode(String errorCode) {
		this.errorCode = errorCode;
	}

	public String getErrorDescription() {
		return errorDescription;
	}

	public void setErrorDescription(String errorDescription) {
		this.errorDescription = errorDescription;
	}

	public Integer getTaskId() {
		return taskId;
	}

	public void setTaskId(Integer taskId) {
		this.taskId = taskId;
	}

	
}
