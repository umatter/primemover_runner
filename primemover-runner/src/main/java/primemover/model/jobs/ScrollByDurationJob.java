package primemover.model.jobs;

import primemover.model.ScrollDirection;

public class ScrollByDurationJob extends ScrollBaseJob {
	
	private Integer duration;
	
	public Integer getDuration() {
		return duration;
	}
	public void setDuration(Integer duration) {
		this.duration = duration;
	}
	
	public ScrollByDurationJob(Long id, String name, String description, String task, ScrollDirection direction, Integer duration) {
		super(id, name, description, task, direction);
		this.duration = duration;
	}
	
	public ScrollByDurationJob() {
		super();
	}
}
