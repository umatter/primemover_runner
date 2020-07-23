package primemover.model;

public class AssertScrolledMaxJob extends BaseJob {
	
	private ScrollDirection direction;
	
	public ScrollDirection getDirection() {
		return direction;
	}
	public void setDirection(ScrollDirection direction) {
		this.direction = direction;
	}
	public AssertScrolledMaxJob(Long id, String reference, String title, ScrollDirection direction) {
		super(id, reference, title);
		this.direction = direction;
	}
	public AssertScrolledMaxJob() {
		super();
	}
	
	
}
