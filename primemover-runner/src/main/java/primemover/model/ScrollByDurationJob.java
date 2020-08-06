package primemover.model;

public class ScrollByDurationJob extends ScrollBaseJob {
	
	private Integer duration;
	
	public Integer getDuration() {
		return duration;
	}
	public void setDuration(Integer duration) {
		this.duration = duration;
	}
	
	public ScrollByDurationJob(Long id, String reference, String title, ScrollDirection direction, Integer duration) {
		super(id, reference, title, direction);
		this.duration = duration;
	}
	
	public ScrollByDurationJob() {
		super();
	}
}
