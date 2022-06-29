package primemover.model.jobs;

import primemover.model.ScrollDirection;

public class ScrollByLengthJob extends ScrollBaseJob {
	
	private Integer length;
	
	public Integer getLength() {
		return length;
	}
	public void setLength(Integer length) {
		this.length = length;
	}
	
	public ScrollByLengthJob(Long id, String name, String description, String task, ScrollDirection direction, Integer length) {
		super(id, name, description, task, direction);
		this.length = length;
	}
	
	public ScrollByLengthJob() {
		super();
	}
}
