package primemover.model.jobs;

import primemover.model.ScrollDirection;

public abstract class ScrollBaseJob extends BaseJob {

	private ScrollDirection direction;

	public ScrollBaseJob() {
		super();
	}

	public ScrollBaseJob(Long id, String name, String description, String task, ScrollDirection direction) {
		super(id, name, description, task);
		this.direction = direction;
	}

	public ScrollDirection getDirection() {
		return direction;
	}

	public void setDirection(ScrollDirection direction) {
		this.direction = direction;
	}

}