package primemover.model.jobs;

import primemover.model.HtmlSelectorType;

public class AssertElementExistsJob extends SelectorBaseJob {

	public AssertElementExistsJob() {
		super();
	}

	public AssertElementExistsJob(Long id, String name, String description, HtmlSelectorType type, String selector) {
		super(id, name, description, null, type, selector);
	}

}
