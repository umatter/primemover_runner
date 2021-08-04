package primemover.service.anticaptcha;

public class SolvingResponse {

	private Integer errorId = 0;
	private String errorCode;
	private String errorDescription;
	
	private TaskStatus status;
	private Double cost;
	private String ip;
	private long createTime;
	private long endTime;
	private int solveCount;
	private SolvingSolution solution;
	
	public SolvingResponse() {
		
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

	public TaskStatus getStatus() {
		return status;
	}

	public void setStatus(TaskStatus status) {
		this.status = status;
	}

	public Double getCost() {
		return cost;
	}

	public void setCost(Double cost) {
		this.cost = cost;
	}

	public long getCreateTime() {
		return createTime;
	}

	public void setCreateTime(long createTime) {
		this.createTime = createTime;
	}

	public long getEndTime() {
		return endTime;
	}

	public void setEndTime(long endTime) {
		this.endTime = endTime;
	}

	public int getSolveCount() {
		return solveCount;
	}

	public void setSolveCount(int solveCount) {
		this.solveCount = solveCount;
	}

	public SolvingSolution getSolution() {
		return solution;
	}

	public void setSolution(SolvingSolution solution) {
		this.solution = solution;
	}

	public String getIp() {
		return ip;
	}

	public void setIp(String ip) {
		this.ip = ip;
	}

}
