package primemover.model.jobs;

import primemover.model.SelectorType;

public class AssertTextInFieldJob extends SelectorBaseJob {
	
	private String value;

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}

	public AssertTextInFieldJob() {
	}

	public AssertTextInFieldJob(Long id, String name, String description, SelectorType type, String selector, String value) {
		super(id, name, description, null, type, selector);
		this.value = value;
	}

}
