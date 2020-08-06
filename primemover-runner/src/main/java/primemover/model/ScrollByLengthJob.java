package primemover.model;

public class ScrollByLengthJob extends ScrollBaseJob {
	
	private Integer length;
	
	public Integer getLength() {
		return length;
	}
	public void setLength(Integer length) {
		this.length = length;
	}
	
	public ScrollByLengthJob(Long id, String reference, String title, ScrollDirection direction, Integer length) {
		super(id, reference, title, direction);
		this.length = length;
	}
	
	public ScrollByLengthJob() {
		super();
	}
}
