package primemover.model;

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

	public WaitJob(Long id, String reference, String title, Integer seconds) {
		super(id, reference, title);
		
		this.secs = seconds;
	}
	
	
	
	

}
