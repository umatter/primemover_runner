package primemover.model;

public abstract class ScrollBaseJob extends BaseJob {

	private ScrollDirection direction;

	public ScrollBaseJob() {
		super();
	}

	public ScrollBaseJob(Long id, String reference, String title, ScrollDirection direction) {
		super(id, reference, title);
		this.direction = direction;
	}

	public ScrollDirection getDirection() {
		return direction;
	}

	public void setDirection(ScrollDirection direction) {
		this.direction = direction;
	}

}