package primemover.model;

public abstract class ScrollBaseJob extends BaseJob {

	private ScrollDirection direction;

	public ScrollBaseJob() {
		super();
	}

	public ScrollBaseJob(Long id, String name, String description, ScrollDirection direction) {
		super(id, name, description);
		this.direction = direction;
	}

	public ScrollDirection getDirection() {
		return direction;
	}

	public void setDirection(ScrollDirection direction) {
		this.direction = direction;
	}

}