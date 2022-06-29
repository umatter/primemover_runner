package primemover.model.jobs;

public class WaitJob extends BaseJob {
	private Integer secs;

	public Integer getSeconds() {
		return secs;
	}

	public void setSeconds(Integer secs) {
		this.secs = secs;
	}

	public WaitJob() {
		super();
	}

	public WaitJob(Long id, String name, String description, String task, Integer seconds) {
		super(id, name, description, task);
		
		this.secs = seconds;
	}
	
	
	
	

}
