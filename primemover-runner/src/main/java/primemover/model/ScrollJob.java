package primemover.model;

public class ScrollJob extends BaseJob {
	
	private ScrollDirection direction;
	// TODO
	private Integer duration;
	private Integer length;
	
	public ScrollDirection getDirection() {
		return direction;
	}
	public void setDirection(ScrollDirection direction) {
		this.direction = direction;
	}
	public Integer getDuration() {
		return duration;
	}
	public void setDuration(Integer duration) {
		this.duration = duration;
	}
	
	public Integer getLength() {
		return length;
	}
	public void setLength(Integer length) {
		this.length = length;
	}
	
	public ScrollJob(Long id, String reference, String title, ScrollDirection direction, Integer duration, Integer length) {
		super(id, reference, title);
		this.direction = direction;
		this.duration = duration;
		this.length = length;
	}
	
	public ScrollJob() {
		super();
	}
	
	
	

}
