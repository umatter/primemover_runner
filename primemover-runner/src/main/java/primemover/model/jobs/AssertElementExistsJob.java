package primemover.model.jobs;

import primemover.model.SelectorType;

public class AssertElementExistsJob extends SelectorBaseJob {

	public AssertElementExistsJob() {
		super();
	}

	public AssertElementExistsJob(Long id, String name, String description, SelectorType type, String selector) {
		super(id, name, description, null, type, selector);
	}
	
}
