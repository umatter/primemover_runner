package primemover.model;

public class ScrollJob extends BaseJob {
	
	public enum Direction {
		UP(1),
		DOWN(2);
		
		private final int code;

	    private Direction(int code) {
	        this.code = code;
	    }
	}
	
	private Direction direction;
	// TODO
	private Integer duration;
	private Integer length;
	
	public Direction getDirection() {
		return direction;
	}
	public void setDirection(Direction direction) {
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
	
	public ScrollJob(Long id, String reference, String title, Direction direction, Integer duration, Integer length) {
		super(id, reference, title);
		this.direction = direction;
		this.duration = duration;
		this.length = length;
	}
	
	public ScrollJob() {
		super();
	}
	
	
	

}
