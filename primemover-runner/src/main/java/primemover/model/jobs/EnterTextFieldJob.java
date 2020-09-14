package primemover.model.jobs;

import primemover.model.SelectorType;

public class EnterTextFieldJob extends SelectorBaseJob {
	
	private String value;
	public String getValue() {
		return value;
	}
	public void setValue(String value) {
		this.value = value;
	}
	public EnterTextFieldJob() {
		super();
	}
	
	public EnterTextFieldJob(Long id, String name, String description, SelectorType type, String fieldSelector, String value) {
		super(id, name, description, type, fieldSelector);
		this.value = value;
	}
	
}
