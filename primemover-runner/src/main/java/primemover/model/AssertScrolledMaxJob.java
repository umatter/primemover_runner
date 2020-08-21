package primemover.model;

public class AssertScrolledMaxJob extends BaseJob {
	
	private ScrollDirection direction;
	
	public ScrollDirection getDirection() {
		return direction;
	}
	public void setDirection(ScrollDirection direction) {
		this.direction = direction;
	}
	public AssertScrolledMaxJob(Long id, String name, String description, ScrollDirection direction) {
		super(id, name, description);
		this.direction = direction;
	}
	public AssertScrolledMaxJob() {
		super();
	}
	
	
}
